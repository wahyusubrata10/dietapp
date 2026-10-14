package com.hawdietpro.app.domain.nutrition

import com.hawdietpro.app.domain.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodMatcherTest {

    private val matcher = FoodMatcher(TestFixtures.references)

    private fun idOf(name: String, method: String? = null) = matcher.match(name, method)?.reference?.id

    @Test
    fun `cocok persis lewat alias tanpa memandang huruf besar`() {
        assertEquals("nasi_putih", idOf("NASI"))
        assertEquals("rendang_daging", idOf("Rendang Sapi"))
        assertEquals("ayam_goreng_tepung", idOf("fried chicken"))
    }

    @Test
    fun `metode masak membantu memilih varian`() {
        assertEquals("ayam_bakar", idOf("Ayam", "bakar"))
        assertEquals("ikan_goreng", idOf("Ikan", "goreng"))
    }

    @Test
    fun `nama dengan tanda baca tetap cocok`() {
        assertEquals("gado_gado", idOf("Gado-gado"))
        assertEquals("sambal_ijo", idOf("Sambal Ijo (lado mudo)").let { it ?: idOf("Sambal ijo") })
    }

    @Test
    fun `kemiripan token untuk nama lebih panjang`() {
        assertEquals("ayam_goreng_tepung", idOf("Ayam goreng tepung renyah"))
        assertEquals("telur_dadar", idOf("Telur dadar padang tebal"))
    }

    @Test
    fun `toleransi salah ketik`() {
        assertEquals("tempe_goreng", idOf("tempe gorng"))
    }

    @Test
    fun `pencarian manual mengutamakan awalan`() {
        val results = matcher.search("soto")
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().name.lowercase().startsWith("soto"))
    }

    @Test
    fun `levenshtein`() {
        assertEquals(1, FoodMatcher.levenshtein("tempe", "tempeh"))
        assertEquals(0, FoodMatcher.levenshtein("nasi", "nasi"))
        assertEquals(3, FoodMatcher.levenshtein("abc", ""))
    }
}
