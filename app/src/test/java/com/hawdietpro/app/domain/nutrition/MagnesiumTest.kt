package com.hawdietpro.app.domain.nutrition

import com.hawdietpro.app.domain.TestFixtures
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.FoodReference
import com.hawdietpro.app.domain.model.NutritionSource
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.model.Sex
import com.hawdietpro.app.domain.model.sum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnesiumTest {

    private fun ref(mg: Double?, category: String = "lauk_nabati") = FoodReference(
        id = "tempe_goreng", name = "Tempe goreng", aliases = emptyList(), category = category,
        per100g = Nutrients(calories = 225.0, protein = 20.0, fiber = 1.4, magnesium = mg ?: 0.0),
        defaultServingG = 50.0, servingLabel = null, source = "POPULER", hasMagnesium = mg != null,
    )

    @Test
    fun `AKG 2019 dewasa`() {
        assertEquals(360, Magnesium.dailyTargetMg(Sex.PRIA, 30))
        assertEquals(350, Magnesium.dailyTargetMg(Sex.PRIA, 70))
        assertEquals(330, Magnesium.dailyTargetMg(Sex.WANITA, 25))
        assertEquals(340, Magnesium.dailyTargetMg(Sex.WANITA, 40))
        val target = TdeeCalculator.calculate(com.hawdietpro.app.domain.model.UserProfile("U", 30, Sex.PRIA, 175.0, 80.0, com.hawdietpro.app.domain.model.ActivityLevel.SEDENTARI, com.hawdietpro.app.domain.model.Goal.TURUN, 70.0))
        assertEquals(360, target.magnesiumMg)
    }

    @Test
    fun `magnesium ikut dijumlah dan diskalakan`() {
        val total = listOf(Nutrients(magnesium = 40.0), Nutrients(magnesium = 25.5)).sum()
        assertEquals(65.5, total.magnesium, 1e-9)
        assertEquals(131.0, (total * 2.0).magnesium, 1e-9)
    }

    @Test
    fun `elemen dari DB memakai magnesium DB, bila tidak ada pakai angka AI`() {
        val item = FoodItem(name = "Tempe goreng", grams = 50.0, nutrients = Nutrients(calories = 110.0, magnesium = 9.0), source = NutritionSource.AI)
        val withDb = NutritionCalculator.applyReference(item, ref(85.0))
        assertEquals(42.5, withDb.nutrients.magnesium, 1e-9)
        val noDb = NutritionCalculator.applyReference(item, ref(null))
        assertEquals(9.0, noDb.nutrients.magnesium, 1e-9)
        // Ubah gram: dihitung ulang dari DB.
        assertEquals(85.0, NutritionCalculator.rescale(withDb, 100.0, ref(85.0)).nutrients.magnesium, 1e-9)
    }

    @Test
    fun `perkiraan untuk catatan lama tanpa DB masuk akal`() {
        val tempe = FoodItem(name = "Tempe", grams = 50.0, nutrients = Nutrients(calories = 100.0, protein = 10.0, fiber = 0.7), category = "lauk_nabati")
        val nasi = FoodItem(name = "Nasi", grams = 150.0, nutrients = Nutrients(calories = 270.0, protein = 4.5, fiber = 0.3), category = "karbo")
        val teh = FoodItem(name = "Teh botol", grams = 350.0, nutrients = Nutrients(calories = 140.0), category = "kemasan")
        val minyak = FoodItem(name = "Minyak", grams = 10.0, nutrients = Nutrients(calories = 90.0), category = "minyak_lemak")
        val mgTempe = Magnesium.forItem(tempe, null)
        val mgNasi = Magnesium.forItem(nasi, null)
        // Tempe jauh lebih padat magnesium daripada nasi putih per gram.
        assertTrue(mgTempe / 50 > mgNasi / 150)
        assertTrue("nasi 150 g ±20–40 mg, dapat $mgNasi", mgNasi in 15.0..45.0)
        assertTrue("teh manis kemasan rendah, dapat ${Magnesium.forItem(teh, null)}", Magnesium.forItem(teh, null) < 30)
        assertEquals(0.0, Magnesium.forItem(minyak, null), 0.0)
        // Referensi DB lebih diutamakan daripada perkiraan.
        assertEquals(42.5, Magnesium.forItem(tempe, ref(85.0)), 1e-9)
    }

    @Test
    fun `fill tidak menimpa angka yang sudah ada`() {
        val item = FoodItem(name = "Bayam", grams = 100.0, nutrients = Nutrients(magnesium = 79.0), category = "sayur")
        assertEquals(79.0, Magnesium.fill(item, null).nutrients.magnesium, 0.0)
        val empty = item.copy(nutrients = Nutrients(calories = 30.0, protein = 3.0, fiber = 2.0))
        assertTrue(Magnesium.fill(empty, null).nutrients.magnesium > 0)
    }

    @Test
    fun `database makanan punya magnesium untuk semua makanan dengan nilai wajar`() {
        val foods = TestFixtures.foodDb.foods
        assertTrue(foods.all { it.magnesium != null })
        assertTrue(foods.all { it.magnesium!! in 0.0..600.0 })
        fun mg(id: String) = foods.first { it.id == id }.magnesium!!
        assertTrue("nasi putih ${mg("nasi_putih")}", mg("nasi_putih") in 8.0..20.0)
        assertTrue("nasi merah lebih tinggi dari nasi putih", mg("nasi_merah") > mg("nasi_putih") * 2)
        val tempe = foods.first { it.name.equals("Tempe goreng", ignoreCase = true) }.magnesium!!
        assertTrue("tempe goreng $tempe", tempe in 60.0..110.0)
        val oil = foods.first { it.name.equals("Minyak goreng", ignoreCase = true) }.magnesium!!
        assertEquals(0.0, oil, 0.0)
    }
}
