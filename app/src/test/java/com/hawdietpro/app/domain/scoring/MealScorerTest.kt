package com.hawdietpro.app.domain.scoring

import com.hawdietpro.app.domain.TestFixtures
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealScorerTest {

    private val scorer = MealScorer(TestFixtures.insightRules)

    private fun item(name: String, grams: Double, kcal: Double, p: Double, c: Double, f: Double, fib: Double = 0.0, category: String? = null, method: String? = null) =
        FoodItem(name = name, grams = grams, nutrients = Nutrients(kcal, p, c, f, fib), category = category, cookingMethod = method)

    @Test
    fun `piring seimbang mendapat grade bagus`() {
        val items = listOf(
            item("Nasi merah", 120.0, 180.0, 3.4, 39.0, 0.5, 0.4, "karbo"),
            item("Ikan bakar", 120.0, 190.0, 30.0, 2.4, 7.0, 0.0, "lauk_hewani", "bakar"),
            item("Tumis buncis", 120.0, 85.0, 2.4, 7.0, 5.0, 3.0, "sayur", "tumis"),
            item("Tahu kukus", 60.0, 50.0, 6.5, 0.5, 2.8, 0.1, "lauk_nabati", "kukus"),
            item("Pepaya", 150.0, 70.0, 0.8, 18.0, 0.2, 2.6, "buah"),
        )
        val result = scorer.analyze(items, MealType.MAKAN_SIANG, 2000)
        assertTrue("grade ${result.grade}", result.grade == Grade.A || result.grade == Grade.B)
        assertTrue(result.labels.contains("Seimbang"))
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `nasi padang besar tanpa sayur dinilai rendah`() {
        val items = listOf(
            item("Nasi putih", 300.0, 540.0, 9.0, 119.0, 0.9, 0.6, "karbo"),
            item("Rendang daging", 80.0, 170.0, 17.6, 5.6, 8.0, 0.8, "lauk_hewani", "santan"),
            item("Kuah gulai", 60.0, 72.0, 0.9, 2.4, 6.6, 0.3, "kuah", "santan"),
            item("Perkedel kentang", 50.0, 105.0, 2.0, 11.0, 6.0, 0.8, "lauk_nabati", "goreng"),
            item("Kerupuk", 15.0, 77.0, 0.8, 9.9, 3.8, 0.1, "gorengan", "goreng"),
            item("Es teh manis", 300.0, 96.0, 0.0, 24.0, 0.0, 0.0, "minuman"),
        )
        val result = scorer.analyze(items, MealType.MAKAN_SIANG, 1800)
        assertTrue("grade ${result.grade}", result.grade >= Grade.C)
        assertTrue(result.labels.contains("Tinggi karbo"))
        assertTrue(result.labels.contains("Kurang sayur"))
        assertTrue(result.labels.contains("Porsi besar"))
        assertTrue(result.suggestions.size in 1..3)
    }

    @Test
    fun `camilan kecil tidak dihukum kurang sayur`() {
        val items = listOf(item("Pisang", 100.0, 92.0, 1.0, 23.0, 0.3, 1.9, "buah"))
        val result = scorer.analyze(items, MealType.CAMILAN, 2000)
        assertTrue(!result.labels.contains("Kurang sayur"))
        assertEquals(Grade.A, result.grade)
    }

    @Test
    fun `gorengan terdeteksi dari nama dan kategori`() {
        val classifier = FoodClassifier(TestFixtures.insightRules)
        assertTrue(classifier.isFried(item("Tempe goreng", 50.0, 1.0, 0.0, 0.0, 0.0)))
        assertTrue(classifier.isFried(item("Bakwan", 50.0, 1.0, 0.0, 0.0, 0.0, category = "gorengan")))
        assertTrue(classifier.isVegetable(item("Lalapan timun", 50.0, 1.0, 0.0, 0.0, 0.0)))
        assertTrue(classifier.isSantan(item("Opor ayam", 50.0, 1.0, 0.0, 0.0, 0.0)))
        assertTrue(classifier.isSweetDrink(item("Es teh manis", 250.0, 80.0, 0.0, 20.0, 0.0)))
    }

    @Test
    fun `operator aturan`() {
        assertTrue(RuleOps.evaluate(5.0, ">", 4.0))
        assertTrue(RuleOps.evaluate(4.0, ">=", 4.0))
        assertTrue(!RuleOps.evaluate(4.0, "??", 4.0))
    }
}
