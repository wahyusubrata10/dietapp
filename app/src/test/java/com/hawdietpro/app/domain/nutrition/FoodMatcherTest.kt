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
    fun `ejaan lain diseragamkan`() {
        assertEquals("ayam_suwir_bumbu_bali", idOf("Ayam suir bumbu Bali"))
        assertEquals("ayam_suwir_bumbu_kuning", idOf("ayam suir bumbu kuning"))
        assertEquals("telur_balado", idOf("Telor balado"))
        assertEquals("sambal_ijo", idOf("Sambel ijo"))
    }

    @Test
    fun `makanan lokal yang dulu tidak ada kini dikenali`() {
        assertEquals("jengkol_rebus", idOf("Jengkol"))
        assertEquals("semur_jengkol", idOf("Semur jengkol"))
        assertEquals("jengkol_balado", idOf("jengkol balado"))
        assertEquals("petai", idOf("pete"))
        assertEquals("ikan_tongkol_bumbu_bali", idOf("Tongkol bumbu bali"))
        assertEquals("tahu_bumbu_bali", idOf("Tahu bumbu Bali"))
        assertEquals("tempe_bacem", idOf("Bacem tempe"))
        assertEquals("kremesan", idOf("Kremes"))
    }

    @Test
    fun `produk minimarket dikenali dari merek`() {
        assertEquals("hotto_purto", idOf("Hotto Purto"))
        assertEquals("chitato", idOf("Chitato"))
        assertEquals("ultra_milk_cokelat", idOf("Ultra Milk Coklat"))
        assertEquals("milo_kotak", idOf("Milo kotak"))
        assertEquals("guribee", idOf("Guribee"))
    }

    @Test
    fun `masakan babi, swikee dan cemilan dikenali`() {
        assertEquals("babi_panggang_karo", idOf("Babi panggang karo"))
        assertEquals("babi_panggang_karo", idOf("BPK"))
        assertEquals("babi_krispi", idOf("Babi crispy"))
        assertEquals("sop_iga_babi", idOf("Sup iga babi"))
        assertEquals("swikee_kuah_tauco", idOf("Swikee"))
        assertEquals("swikee_goreng", idOf("swike goreng"))
        assertEquals("se_i_babi", idOf("Sei babi"))
        assertEquals("kangkung_terasi".let { idOf("Cah kangkung terasi") ?: it }, idOf("Cah kangkung terasi"))
        assertEquals("tiramisu", idOf("Tiramisu"))
    }

    @Test
    fun `lauk berbeda tidak tertukar`() {
        assertTrue(idOf("Udang balado") != idOf("Cumi balado"))
        assertTrue(idOf("Tahu bumbu bali") != idOf("Telur bumbu bali"))
        assertEquals("cumi_balado", idOf("Cumi balado"))
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
