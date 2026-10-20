package com.hawdietpro.app.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val format: Int = 3,
    val app: String = "Haw Diet Pro",
    val appVersionCode: Int,
    val exportedAt: Long,
    val profile: BackupProfile? = null,
    val meals: List<BackupMeal> = emptyList(),
    val weights: List<BackupWeight> = emptyList(),
    val userFoods: List<BackupFood> = emptyList(),
    /** Ada sejak format v2; backup lama tanpa olahraga tetap bisa dibaca. */
    val exercises: List<BackupExercise> = emptyList(),
    /** Produk kemasan hasil scan barcode (format v3). */
    val barcodeProducts: List<BackupBarcodeProduct> = emptyList(),
)

@Serializable
data class BackupBarcodeProduct(
    val barcode: String,
    val name: String,
    val brand: String? = null,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodium: Double = 0.0,
    val servingG: Double? = null,
    val packageG: Double? = null,
    val source: String,
    val complete: Boolean = true,
)

@Serializable
data class BackupExercise(
    val epochDay: Long,
    val performedAt: Long,
    val activityId: String? = null,
    val name: String,
    val durationMin: Int,
    val calories: Double,
    val source: String = "MANUAL",
    val note: String? = null,
)

@Serializable
data class BackupProfile(
    val name: String,
    val age: Int,
    val sex: String,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: String,
    val goal: String,
    val targetWeightKg: Double? = null,
    val customCalories: Int? = null,
)

@Serializable
data class BackupMeal(
    val eatenAt: Long,
    val mealType: String,
    val menuName: String,
    /** Nama file di folder photos/ dalam ZIP. */
    val photo: String? = null,
    val confidence: Double? = null,
    val grade: String? = null,
    val assessments: List<String> = emptyList(),
    val aiNotes: String? = null,
    val suggestions: List<String> = emptyList(),
    val userNote: String? = null,
    val favorite: Boolean = false,
    val items: List<BackupItem> = emptyList(),
)

@Serializable
data class BackupItem(
    val name: String,
    val grams: Double,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double = 0.0,
    val sodium: Double = 0.0,
    val cookingMethod: String? = null,
    val source: String = "AI",
    val foodRefId: String? = null,
    val category: String? = null,
    val aiCalories: Double? = null,
)

@Serializable
data class BackupWeight(val epochDay: Long, val weightKg: Double, val note: String? = null)

@Serializable
data class BackupFood(
    val id: String,
    val name: String,
    val category: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val servingG: Double,
)
