package com.hawdietpro.app.domain.nutrition

import com.hawdietpro.app.domain.TestFixtures
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.NutritionSource
import com.hawdietpro.app.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionCalculatorTest {

    private val rice = TestFixtures.references.first { it.id == "nasi_putih" }

    @Test
    fun `gizi dihitung dari gram kali nilai per 100 g`() {
        val n = NutritionCalculator.fromReference(rice, 150.0)
        assertEquals(270.0, n.calories, 0.01) // 180 × 1.5
        assertEquals(4.5, n.protein, 0.01)
        assertEquals(59.7, n.carbs, 0.01)
    }

    @Test
    fun `menerapkan referensi DB mengganti angka AI dan menyimpan angka asli`() {
        val ai = FoodItem(name = "Nasi putih", grams = 200.0, nutrients = Nutrients(300.0, 5.0, 65.0, 1.0, 0.5))
        val applied = NutritionCalculator.applyReference(ai, rice)
        assertEquals(NutritionSource.DB, applied.source)
        assertEquals(360.0, applied.nutrients.calories, 0.01)
        assertEquals(300.0, applied.aiNutrients!!.calories, 0.01)
        assertEquals("nasi_putih", applied.foodRefId)
    }

    @Test
    fun `ubah gram elemen DB dihitung ulang dari referensi`() {
        val item = NutritionCalculator.applyReference(
            FoodItem(name = "Nasi", grams = 100.0, nutrients = Nutrients.ZERO), rice,
        )
        val half = NutritionCalculator.rescale(item, 50.0, rice)
        assertEquals(90.0, half.nutrients.calories, 0.01)
    }

    @Test
    fun `ubah gram elemen AI diskalakan proporsional`() {
        val item = FoodItem(name = "Rendang", grams = 80.0, nutrients = Nutrients(200.0, 18.0, 6.0, 12.0, 1.0))
        val doubled = NutritionCalculator.rescale(item, 160.0, null)
        assertEquals(400.0, doubled.nutrients.calories, 0.01)
        assertEquals(36.0, doubled.nutrients.protein, 0.01)
    }

    @Test
    fun `gram nol tidak menghasilkan pembagian nol`() {
        val item = FoodItem(name = "X", grams = 0.0, nutrients = Nutrients(10.0))
        val r = NutritionCalculator.rescale(item, 50.0, null)
        assertEquals(10.0, r.nutrients.calories, 0.01)
    }

    @Test
    fun `persentase energi makro`() {
        val split = Nutrients(calories = 0.0, protein = 25.0, carbs = 50.0, fat = 10.0).macroEnergy
        // 100 + 200 + 90 = 390
        assertEquals(25.64, split.proteinPct, 0.01)
        assertEquals(51.28, split.carbsPct, 0.01)
        assertEquals(23.08, split.fatPct, 0.01)
    }

    @Test
    fun `referensi tersedia untuk makanan Indonesia populer`() {
        val matcher = FoodMatcher(TestFixtures.references)
        listOf("Rendang", "Soto ayam", "Pecel", "Tempe goreng", "Nasi uduk", "Ayam geprek", "Bakwan", "Nasi kuning", "Gado-gado")
            .forEach { assertNotNull("tidak cocok: $it", matcher.match(it)) }
    }

    @Test
    fun `nama tak dikenal tidak dipaksakan cocok`() {
        assertNull(FoodMatcher(TestFixtures.references).match("Kue misterius planet mars"))
    }

    @Test
    fun `gula dan natrium dari DB bila tersedia`() {
        val kecap = TestFixtures.references.first { it.id == "kecap_manis" }
        val item = NutritionCalculator.applyReference(
            FoodItem(name = "Kecap", grams = 15.0, nutrients = Nutrients(40.0, sugar = 1.0, sodium = 10.0)), kecap,
        )
        assertEquals(7.5, item.nutrients.sugar, 0.01) // 50 g/100 g × 15 g
        assertEquals(570.0, item.nutrients.sodium, 0.01)
    }

    @Test
    fun `gula dan natrium tetap estimasi AI bila DB belum punya data`() {
        val ref = TestFixtures.references.first { !it.hasSugarSodium }
        val ai = Nutrients(100.0, sugar = 3.0, sodium = 400.0)
        val item = NutritionCalculator.applyReference(FoodItem(name = ref.name, grams = 100.0, nutrients = ai), ref)
        assertEquals(3.0, item.nutrients.sugar, 0.0)
        assertEquals(400.0, item.nutrients.sodium, 0.0)
        // diskalakan proporsional saat gram diubah
        val half = NutritionCalculator.rescale(item, 50.0, ref)
        assertEquals(200.0, half.nutrients.sodium, 0.01)
    }
}
