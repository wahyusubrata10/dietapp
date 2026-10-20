package com.hawdietpro.app.data.repository

import com.hawdietpro.app.BuildConfig
import com.hawdietpro.app.core.ImageProcessor
import com.hawdietpro.app.data.gemini.GeminiClient
import com.hawdietpro.app.data.local.dao.BarcodeProductDao
import com.hawdietpro.app.data.local.toDomain
import com.hawdietpro.app.data.local.toEntity
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.NutritionSource
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.product.OpenFoodFactsParser
import com.hawdietpro.app.domain.product.PackagedProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LookupResult {
    data class Found(val product: PackagedProduct, val cached: Boolean) : LookupResult
    /** Produk dikenal, tapi data gizinya kosong/tidak lengkap. */
    data class Incomplete(val product: PackagedProduct) : LookupResult
    data object NotFound : LookupResult
    data class Error(val message: String) : LookupResult
}

/**
 * Produk kemasan per barcode: cache lokal → Open Food Facts (gratis, tanpa API key) →
 * cadangan foto label gizi dibaca AI. Yang dikirim ke internet hanya nomor barcode.
 */
@Singleton
class BarcodeRepository @Inject constructor(
    private val dao: BarcodeProductDao,
    private val http: OkHttpClient,
    private val gemini: GeminiClient,
    private val images: ImageProcessor,
    private val meals: MealRepository,
    private val evaluator: MealEvaluator,
) {
    fun observeRecent(limit: Int = 12): Flow<List<PackagedProduct>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun lookup(barcode: String): LookupResult {
        dao.get(barcode)?.toDomain()?.let { cached ->
            if (cached.complete) return LookupResult.Found(cached, cached = true)
        }
        return try {
            val product = fetchOpenFoodFacts(barcode) ?: return LookupResult.NotFound
            if (!product.complete) return LookupResult.Incomplete(product)
            dao.upsert(product.toEntity())
            LookupResult.Found(product, cached = false)
        } catch (e: IOException) {
            LookupResult.Error("Tidak bisa terhubung ke Open Food Facts. Periksa internet, atau foto label gizinya.")
        }
    }

    private suspend fun fetchOpenFoodFacts(barcode: String): PackagedProduct? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=${OpenFoodFactsParser.FIELDS}")
            // OFF meminta User-Agent yang jelas untuk aplikasi.
            .header("User-Agent", "HawDietPro/${BuildConfig.VERSION_NAME} (Android; github.com/${BuildConfig.GITHUB_REPO})")
            .get()
            .build()
        val call = http.newCall(request)
        call.timeout().timeout(15, TimeUnit.SECONDS)
        call.execute().use { response ->
            when {
                response.code == 404 -> null
                !response.isSuccessful -> throw IOException("HTTP ${response.code}")
                else -> OpenFoodFactsParser.parse(response.body?.string().orEmpty(), barcode)
            }
        }
    }

    /** Membaca foto label gizi dengan AI lalu menyimpannya untuk scan berikutnya. */
    suspend fun readLabel(photo: File, barcode: String, knownName: String?, knownBrand: String?): PackagedProduct {
        // Resolusi lebih tinggi dari foto makanan agar angka kecil di label tetap terbaca.
        val processed = images.importFromFile(photo, maxSize = 1600, quality = 88)
        try {
            val label = gemini.readLabel(images.readBytes(processed.path))
            if (!label.readable || label.calories == null) {
                throw LabelException("Tabel gizi belum terbaca jelas. Foto ulang lebih dekat, lurus, dan terang.")
            }
            val values = Nutrients(
                calories = label.calories,
                protein = label.protein ?: 0.0,
                carbs = label.carbs ?: 0.0,
                fat = label.fat ?: 0.0,
                fiber = label.fiber ?: 0.0,
                sugar = label.sugar ?: 0.0,
                sodium = label.sodium ?: 0.0,
            )
            val serving = label.servingG
                ?: label.packageG?.let { pkg -> label.servingsPerPackage?.takeIf { it > 0 }?.let { pkg / it } }
            val per100 = if (label.basis == "per_100g") values
            else PackagedProduct.per100FromServing(values, serving)
                ?: throw LabelException("Takaran saji (gram/ml) tidak terbaca. Pastikan baris \"Takaran saji\" ikut terfoto.")
            val product = PackagedProduct(
                barcode = barcode,
                name = label.name?.takeIf { it.isNotBlank() } ?: knownName ?: "Produk $barcode",
                brand = label.brand?.takeIf { it.isNotBlank() } ?: knownBrand,
                per100 = per100,
                servingG = serving,
                packageG = label.packageG ?: serving?.let { s -> label.servingsPerPackage?.let { it * s } },
                source = PackagedProduct.SOURCE_LABEL,
            )
            dao.upsert(product.toEntity())
            return product
        } finally {
            processed.delete()
        }
    }

    suspend fun saveProduct(product: PackagedProduct) = dao.upsert(product.toEntity())

    suspend fun forget(barcode: String) = dao.delete(barcode)

    /** Mencatat produk sebagai satu kali makan. */
    suspend fun log(product: PackagedProduct, grams: Double, mealType: MealType, eatenAt: Long): Long {
        val item = FoodItem(
            name = product.displayName,
            grams = grams,
            nutrients = product.nutrientsFor(grams),
            source = NutritionSource.KEMASAN,
            foodRefId = "barcode:${product.barcode}",
            category = "kemasan",
        )
        val meal = evaluator.evaluate(
            Meal(
                eatenAt = eatenAt,
                mealType = mealType,
                menuName = product.displayName,
                photoPath = null,
                confidence = null,
                items = listOf(item),
                userNote = "Barcode ${product.barcode} · ${PackagedProduct.sourceLabel(product.source)}",
            ),
        )
        return meals.save(meal)
    }

    class LabelException(message: String) : Exception(message)
}
