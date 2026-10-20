package com.hawdietpro.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val age: Int,
    val sex: String,
    @ColumnInfo(name = "height_cm") val heightCm: Double,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "activity_level") val activityLevel: String,
    val goal: String,
    @ColumnInfo(name = "target_weight_kg") val targetWeightKg: Double?,
    @ColumnInfo(name = "custom_calories") val customCalories: Int?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "meal_entry",
    indices = [Index("eaten_at"), Index("meal_type")],
)
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "eaten_at") val eatenAt: Long,
    @ColumnInfo(name = "meal_type") val mealType: String,
    @ColumnInfo(name = "menu_name") val menuName: String,
    @ColumnInfo(name = "photo_path") val photoPath: String?,
    val confidence: Double?,
    @ColumnInfo(name = "total_calories") val totalCalories: Double,
    @ColumnInfo(name = "total_protein") val totalProtein: Double,
    @ColumnInfo(name = "total_carbs") val totalCarbs: Double,
    @ColumnInfo(name = "total_fat") val totalFat: Double,
    @ColumnInfo(name = "total_fiber") val totalFiber: Double,
    @ColumnInfo(name = "total_sugar", defaultValue = "0") val totalSugar: Double = 0.0,
    @ColumnInfo(name = "total_sodium", defaultValue = "0") val totalSodium: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    val grade: String?,
    /** Label penilaian, dipisah "|" (contoh: "Tinggi karbo|Kurang sayur"). */
    val assessments: String,
    @ColumnInfo(name = "ai_notes") val aiNotes: String?,
    /** Saran, dipisah baris baru. */
    val suggestions: String,
    @ColumnInfo(name = "user_note") val userNote: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "food_element",
    foreignKeys = [
        ForeignKey(
            entity = MealEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["meal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("meal_id"), Index("food_ref_id")],
)
data class FoodElementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "meal_id") val mealId: Long,
    val position: Int,
    val name: String,
    val grams: Double,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    @ColumnInfo(defaultValue = "0") val sugar: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val sodium: Double = 0.0,
    @ColumnInfo(name = "cooking_method") val cookingMethod: String?,
    @ColumnInfo(name = "nutrition_source") val nutritionSource: String,
    @ColumnInfo(name = "food_ref_id") val foodRefId: String?,
    val category: String?,
    @ColumnInfo(name = "ai_calories") val aiCalories: Double?,
)

data class MealWithElements(
    @Embedded val meal: MealEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "meal_id")
    val elements: List<FoodElementEntity>,
)

@Entity(
    tableName = "food_reference",
    indices = [Index("normalized_name"), Index("category")],
)
data class FoodReferenceEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    /** Alias ternormalisasi, dipisah "|". */
    val aliases: String,
    val category: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    /** Gula g/100 g; null = belum ada data. */
    val sugar: Double? = null,
    /** Natrium mg/100 g; null = belum ada data. */
    val sodium: Double? = null,
    @ColumnInfo(name = "default_serving_g") val defaultServingG: Double,
    @ColumnInfo(name = "serving_label") val servingLabel: String?,
    /** TKPI, POPULER, atau USER (ditambah pengguna). */
    val source: String,
    @ColumnInfo(name = "data_version") val dataVersion: Int,
)

@Entity(
    tableName = "weight_log",
    indices = [Index(value = ["epoch_day"], unique = true)],
)
data class WeightLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "epoch_day") val epochDay: Long,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "exercise_log",
    indices = [Index(value = ["epoch_day"])],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "epoch_day") val epochDay: Long,
    @ColumnInfo(name = "performed_at") val performedAt: Long,
    @ColumnInfo(name = "activity_id") val activityId: String?,
    val name: String,
    @ColumnInfo(name = "duration_min") val durationMin: Int,
    val calories: Double,
    val source: String,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Cache produk kemasan per barcode (dari Open Food Facts, label dibaca AI, atau isian sendiri). Gizi per 100 g. */
@Entity(tableName = "barcode_product")
data class BarcodeProductEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String?,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val sodium: Double,
    @ColumnInfo(name = "serving_g") val servingG: Double?,
    @ColumnInfo(name = "package_g") val packageG: Double?,
    val source: String,
    val complete: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "pending_analysis")
data class PendingAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "photo_path") val photoPath: String,
    @ColumnInfo(name = "meal_type") val mealType: String,
    @ColumnInfo(name = "captured_at") val capturedAt: Long,
    /** MENUNGGU, DIPROSES, GAGAL. Item yang sukses langsung dihapus. */
    val status: String,
    val attempts: Int,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Catatan pengguna untuk AI (mis. "nasi setengah"), ikut dikirim saat analisa di latar belakang. */
    val hint: String? = null,
)

/** Proyeksi ringan untuk perhitungan kalori harian (tanpa elemen). */
data class CalorieEntry(
    val eatenAt: Long,
    val calories: Double,
)
