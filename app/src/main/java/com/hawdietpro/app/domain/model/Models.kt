package com.hawdietpro.app.domain.model

/** Satu elemen di piring (nasi, lauk, sayur, sambal, ...). */
data class FoodItem(
    val id: Long = 0,
    val name: String,
    val grams: Double,
    val nutrients: Nutrients,
    val cookingMethod: String? = null,
    val source: NutritionSource = NutritionSource.AI,
    val foodRefId: String? = null,
    /** Kategori dari DB lokal (karbo, sayur, ...) bila cocok. */
    val category: String? = null,
    /** Angka asli dari AI, disimpan agar bisa dibandingkan dengan DB. */
    val aiNutrients: Nutrients? = null,
)

data class Meal(
    val id: Long = 0,
    val eatenAt: Long,
    val mealType: MealType,
    val menuName: String,
    val photoPath: String?,
    val confidence: Double?,
    val items: List<FoodItem>,
    val grade: Grade? = null,
    val assessments: List<String> = emptyList(),
    val aiNotes: String? = null,
    val suggestions: List<String> = emptyList(),
    val userNote: String? = null,
    val favorite: Boolean = false,
) {
    val total: Nutrients get() = items.map { it.nutrients }.sum()
}

data class FoodReference(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val category: String,
    val per100g: Nutrients,
    val defaultServingG: Double,
    val servingLabel: String?,
    val source: String,
    /** false = DB belum punya data gula/natrium untuk makanan ini (pakai estimasi AI). */
    val hasSugarSodium: Boolean = false,
)

data class UserProfile(
    val name: String,
    val age: Int,
    val sex: Sex,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: ActivityLevel,
    val goal: Goal,
    val targetWeightKg: Double?,
    /** Jika diisi, menggantikan target kalori hasil hitungan. */
    val customCalories: Int? = null,
)

data class WeightEntry(
    val id: Long = 0,
    val epochDay: Long,
    val weightKg: Double,
    val note: String? = null,
)
