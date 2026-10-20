package com.hawdietpro.app.domain.product

import com.hawdietpro.app.domain.model.Nutrients
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Membaca respons Open Food Facts API v2 (`/api/v2/product/{barcode}.json`).
 * Angka di OFF bisa berupa angka atau teks, dan kadang hanya tersedia per sajian, jadi dibaca longgar.
 */
object OpenFoodFactsParser {

    const val FIELDS = "product_name,product_name_id,product_name_en,generic_name,brands,quantity,product_quantity," +
        "serving_size,serving_quantity,nutriments"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Null bila produk tidak ditemukan. */
    fun parse(body: String, barcode: String): PackagedProduct? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val status = root["status"]?.number()
        val product = root["product"] as? JsonObject ?: return null
        if (status != null && status < 1.0) return null

        val name = listOf("product_name_id", "product_name", "product_name_en", "generic_name")
            .firstNotNullOfOrNull { product[it]?.text()?.trim()?.takeIf(String::isNotBlank) }
        val brand = product["brands"]?.text()?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        val servingG = product["serving_quantity"]?.number()?.takeIf { it > 0 }
        val packageG = product["product_quantity"]?.number()?.takeIf { it > 0 }
            ?: product["quantity"]?.text()?.let(::gramsFromText)

        val n = product["nutriments"] as? JsonObject ?: JsonObject(emptyMap())
        fun per100(key: String): Double? {
            n["${key}_100g"]?.number()?.let { return it }
            val serving = n["${key}_serving"]?.number() ?: return null
            return servingG?.let { serving * 100.0 / it }
        }
        val kcal = per100("energy-kcal") ?: per100("energy")?.let { it / 4.184 } // energy = kJ
        val protein = per100("proteins")
        val carbs = per100("carbohydrates")
        val fat = per100("fat")
        val sodiumMg = per100("sodium")?.let { it * 1000 } ?: per100("salt")?.let { it * 400 }

        if (name == null && kcal == null) return null
        return PackagedProduct(
            barcode = barcode,
            name = name ?: "Produk $barcode",
            brand = brand,
            per100 = Nutrients(
                calories = kcal ?: 0.0,
                protein = protein ?: 0.0,
                carbs = carbs ?: 0.0,
                fat = fat ?: 0.0,
                fiber = per100("fiber") ?: 0.0,
                sugar = per100("sugars") ?: 0.0,
                sodium = sodiumMg ?: 0.0,
            ),
            servingG = servingG,
            packageG = packageG,
            source = PackagedProduct.SOURCE_OFF,
            complete = kcal != null && protein != null && carbs != null && fat != null,
        )
    }

    /** "250 ml", "68 g", "1 kg", "2 x 30 g" → gram/ml. */
    fun gramsFromText(text: String): Double? {
        val t = text.lowercase().replace(',', '.')
        Regex("""(\d+)\s*[x×]\s*(\d+(?:\.\d+)?)\s*(g|gr|ml)\b""").find(t)?.let { m ->
            return m.groupValues[1].toDouble() * m.groupValues[2].toDouble()
        }
        val m = Regex("""(\d+(?:\.\d+)?)\s*(kg|g|gr|gram|ml|l|liter)\b""").find(t) ?: return null
        val v = m.groupValues[1].toDouble()
        return when (m.groupValues[2]) {
            "kg", "l", "liter" -> v * 1000
            else -> v
        }
    }

    private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.content
    private fun JsonElement.number(): Double? = (this as? JsonPrimitive)?.content?.replace(',', '.')?.toDoubleOrNull()
}
