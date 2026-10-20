package com.hawdietpro.app.domain.product

import com.hawdietpro.app.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackagedProductTest {

    @Test
    fun `parse produk lengkap dari Open Food Facts`() {
        val body = """
            {"status":1,"product":{"product_name":"Chitato Sapi Panggang","brands":"Chitato, Indofood",
             "quantity":"68 g","serving_quantity":"35",
             "nutriments":{"energy-kcal_100g":540,"proteins_100g":"6","carbohydrates_100g":52,"fat_100g":34,
               "fiber_100g":3,"sugars_100g":2,"sodium_100g":0.6}}}
        """.trimIndent()
        val p = OpenFoodFactsParser.parse(body, "8992760221028")!!
        assertEquals("Chitato Sapi Panggang", p.name)
        assertEquals("Chitato", p.brand)
        assertEquals(540.0, p.per100.calories, 0.01)
        assertEquals(600.0, p.per100.sodium, 0.01)
        assertEquals(35.0, p.servingG!!, 0.0)
        assertEquals(68.0, p.packageG!!, 0.0)
        assertTrue(p.complete)
        assertFalse(p.suspicious)
        assertEquals("Chitato Sapi Panggang", p.displayName)
    }

    @Test
    fun `nilai per sajian dan kJ dikonversi ke per 100 g`() {
        val body = """
            {"status":1,"product":{"product_name":"Susu","serving_quantity":200,"product_quantity":"250",
             "nutriments":{"energy_serving":502,"proteins_serving":6.4,"carbohydrates_serving":9,"fat_serving":6.4,"salt_100g":0.1}}}
        """.trimIndent()
        val p = OpenFoodFactsParser.parse(body, "12345678")!!
        assertEquals(502 / 4.184 / 2, p.per100.calories, 0.1)
        assertEquals(3.2, p.per100.protein, 0.01)
        assertEquals(40.0, p.per100.sodium, 0.01)
        assertEquals(250.0, p.packageG!!, 0.0)
    }

    @Test
    fun `produk tidak ditemukan atau data tidak lengkap`() {
        assertNull(OpenFoodFactsParser.parse("""{"status":0,"status_verbose":"product not found"}""", "1"))
        assertNull(OpenFoodFactsParser.parse("bukan json", "1"))
        val partial = OpenFoodFactsParser.parse("""{"status":1,"product":{"product_name":"Kopi X","nutriments":{}}}""", "1")
        assertNotNull(partial)
        assertFalse(partial!!.complete)
    }

    @Test
    fun `angka kalori yang tidak cocok dengan makro ditandai`() {
        val p = PackagedProduct("1", "X", per100 = Nutrients(100.0, 10.0, 50.0, 20.0), source = PackagedProduct.SOURCE_OFF)
        assertTrue(p.suspicious)
    }

    @Test
    fun `ukuran dari teks kemasan`() {
        assertEquals(250.0, OpenFoodFactsParser.gramsFromText("250 ml")!!, 0.0)
        assertEquals(1000.0, OpenFoodFactsParser.gramsFromText("1 L")!!, 0.0)
        assertEquals(60.0, OpenFoodFactsParser.gramsFromText("2 x 30 g")!!, 0.0)
        assertEquals(68.5, OpenFoodFactsParser.gramsFromText("68,5 g")!!, 0.0)
        assertNull(OpenFoodFactsParser.gramsFromText("satu bungkus"))
    }

    @Test
    fun `label per sajian ke per 100 g dan validasi barcode`() {
        val per100 = PackagedProduct.per100FromServing(Nutrients(150.0, 2.0, 20.0, 7.0), 30.0)!!
        assertEquals(500.0, per100.calories, 0.01)
        assertNull(PackagedProduct.per100FromServing(Nutrients(150.0), null))
        assertTrue(PackagedProduct.isValidBarcode("8992760221028"))
        assertFalse(PackagedProduct.isValidBarcode("12345"))
        assertFalse(PackagedProduct.isValidBarcode("89927602210AB"))
    }
}
