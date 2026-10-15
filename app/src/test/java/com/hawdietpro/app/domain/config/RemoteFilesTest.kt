package com.hawdietpro.app.domain.config

import com.hawdietpro.app.domain.TestFixtures
import com.hawdietpro.app.domain.scoring.RuleOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Memastikan file /remote yang dibundel ke APK valid terhadap model aplikasi. */
class RemoteFilesTest {

    @Test
    fun `config json valid`() {
        val cfg = TestFixtures.json.decodeFromString(AppConfig.serializer(), TestFixtures.remoteFile("config.json").readText())
        assertTrue(cfg.version >= 1)
    }

    @Test
    fun `gemini prompt valid`() {
        val cfg = TestFixtures.json.decodeFromString(GeminiPromptConfig.serializer(), TestFixtures.remoteFile("gemini_prompt.json").readText())
        assertTrue(cfg.model.isNotBlank())
        assertTrue(cfg.userPrompt.contains("{meal_type}"))
        assertTrue(cfg.responseSchema.containsKey("properties"))
    }

    @Test
    fun `food db valid dan id unik`() {
        val db = TestFixtures.foodDb
        assertTrue(db.foods.size >= 1000)
        assertEquals(db.foods.size, db.foods.map { it.id }.toSet().size)
        db.foods.forEach { f ->
            assertTrue("${f.id} kategori tak dikenal", f.category in db.categories.keys)
            assertTrue("${f.id} kalori negatif", f.kcal >= 0)
            val est = f.protein * 4 + f.carbs * 4 + f.fat * 9
            if (f.kcal > 20) assertTrue("${f.id}: kkal ${f.kcal} vs makro $est", kotlin.math.abs(est - f.kcal) / f.kcal < 0.35)
        }
    }

    @Test
    fun `insight rules memakai operator yang dikenal`() {
        val rules = TestFixtures.insightRules
        (rules.mealScoring.rules.map { it.op } + rules.insights.map { it.op }).forEach {
            assertTrue("operator $it", it in RuleOps.SUPPORTED)
        }
        assertEquals(setOf("A", "B", "C", "D", "E"), rules.mealScoring.grades.map { it.grade }.toSet())
    }

    @Test
    fun `validator menerima semua file bawaan`() {
        val j = TestFixtures.json
        val files = listOf(
            j.decodeFromString(AppConfig.serializer(), TestFixtures.remoteFile("config.json").readText()),
            j.decodeFromString(GeminiPromptConfig.serializer(), TestFixtures.remoteFile("gemini_prompt.json").readText()),
            TestFixtures.foodDb,
            TestFixtures.insightRules,
        )
        files.forEach { assertEquals(emptyList<String>(), ConfigValidator.validate(it)) }
    }

    @Test
    fun `validator menolak file rusak`() {
        val bad = TestFixtures.foodDb.copy(foods = TestFixtures.foodDb.foods + TestFixtures.foodDb.foods.first())
        assertTrue(ConfigValidator.validate(bad).isNotEmpty())
        val badPrompt = j().decodeFromString(GeminiPromptConfig.serializer(), TestFixtures.remoteFile("gemini_prompt.json").readText()).copy(model = "")
        assertTrue(ConfigValidator.validate(badPrompt).isNotEmpty())
    }

    private fun j() = TestFixtures.json
}
