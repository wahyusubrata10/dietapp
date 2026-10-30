package com.hawdietpro.app.domain.nutrition

import com.hawdietpro.app.domain.model.Goal
import com.hawdietpro.app.domain.model.Sex
import com.hawdietpro.app.domain.model.UserProfile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Hasil perhitungan target harian. */
data class DailyTarget(
    val bmr: Int,
    val tdee: Int,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val fiberG: Int,
    /** Selisih terhadap TDEE (negatif = defisit). */
    val adjustment: Int,
    val bmi: Double,
    val bmiCategory: String,
    /** Perkiraan perubahan berat per minggu (kg, negatif = turun). */
    val weeklyChangeKg: Double,
    /** Perkiraan minggu menuju berat target, null bila tidak relevan. */
    val weeksToTarget: Int?,
    val warnings: List<String>,
    /** true = TDEE berasal dari data timbangan & catatan makan (adaptif), bukan rumus saja. */
    val adaptive: Boolean = false,
)

/**
 * BMR (Mifflin-St Jeor) → TDEE → target kalori yang aman.
 * Batas keamanan:
 * - Defisit maks 500 kkal/hari dan maks 20% TDEE (±0,5 kg/minggu).
 * - Surplus maks 300 kkal/hari dan maks 15% TDEE.
 * - Batas bawah kalori 1200 (wanita) / 1500 (pria), tidak pernah di bawah BMR × 1,0 bila memungkinkan.
 * - BMI < 18,5 dengan tujuan turun → tidak diberi defisit.
 */
object TdeeCalculator {

    const val MAX_DEFICIT = 500
    const val MAX_DEFICIT_RATIO = 0.20
    const val MAX_SURPLUS = 300
    const val MAX_SURPLUS_RATIO = 0.15
    const val MIN_CALORIES_FEMALE = 1200
    const val MIN_CALORIES_MALE = 1500
    private const val KCAL_PER_KG = 7700.0

    fun bmr(sex: Sex, weightKg: Double, heightCm: Double, age: Int): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * age
        return if (sex == Sex.PRIA) base + 5 else base - 161
    }

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val m = heightCm / 100.0
        return if (m <= 0) 0.0 else weightKg / (m * m)
    }

    /** Kategori BMI untuk orang Asia (WHO Asia-Pasifik). */
    fun bmiCategory(bmi: Double): String = when {
        bmi <= 0 -> "-"
        bmi < 18.5 -> "Berat badan kurang"
        bmi < 23.0 -> "Normal"
        bmi < 25.0 -> "Berlebih"
        bmi < 30.0 -> "Obesitas I"
        else -> "Obesitas II"
    }

    fun minimumCalories(sex: Sex): Int = if (sex == Sex.PRIA) MIN_CALORIES_MALE else MIN_CALORIES_FEMALE

    fun calculate(profile: UserProfile): DailyTarget = calculate(profile, null)

    /** [measuredTdee] = TDEE adaptif dari data (lihat [AdaptiveTdeeEstimator]); null = pakai rumus. */
    fun calculate(profile: UserProfile, measuredTdee: Int?): DailyTarget {
        val warnings = mutableListOf<String>()
        val bmr = bmr(profile.sex, profile.weightKg, profile.heightCm, profile.age)
        val tdee = measuredTdee?.toDouble() ?: (bmr * profile.activityLevel.factor)
        val bmi = bmi(profile.weightKg, profile.heightCm)
        val floor = minimumCalories(profile.sex).toDouble()

        var goal = profile.goal
        if (goal == Goal.TURUN && bmi in 0.1..18.49) {
            warnings += "BMI Anda di bawah normal, jadi target tidak dibuat defisit. Konsultasikan dengan tenaga kesehatan sebelum menurunkan berat."
            goal = Goal.JAGA
        }

        val computed = when (goal) {
            Goal.TURUN -> {
                val deficit = min(MAX_DEFICIT.toDouble(), tdee * MAX_DEFICIT_RATIO)
                val raw = tdee - deficit
                when {
                    tdee <= floor -> {
                        warnings += "Kebutuhan energi Anda sudah rendah, jadi target dibuat sama dengan TDEE. Perbanyak aktivitas fisik untuk menurunkan berat."
                        tdee
                    }
                    raw < floor -> {
                        warnings += "Target dinaikkan ke batas aman minimum ${floor.toInt()} kkal."
                        floor
                    }
                    else -> raw
                }
            }
            Goal.JAGA -> tdee
            Goal.NAIK -> tdee + min(MAX_SURPLUS.toDouble(), tdee * MAX_SURPLUS_RATIO)
        }

        var calories = roundTo10(computed)
        profile.customCalories?.let { custom ->
            val safeMin = minimumCalories(profile.sex)
            if (custom < safeMin) {
                warnings += "Target manual $custom kkal terlalu rendah, dipakai batas aman $safeMin kkal."
                calories = safeMin
            } else if (custom > tdee + 1000) {
                warnings += "Target manual jauh di atas kebutuhan Anda, dibatasi ke ${roundTo10(tdee + 1000)} kkal."
                calories = roundTo10(tdee + 1000)
            } else {
                calories = custom
            }
        }

        val macros = macroTargets(calories, profile.weightKg, goal)
        val adjustment = calories - tdee.roundToInt()
        val weeklyChange = adjustment * 7 / KCAL_PER_KG
        val weeksToTarget = profile.targetWeightKg?.let { target ->
            val diff = target - profile.weightKg
            if (weeklyChange == 0.0 || diff == 0.0 || diff * weeklyChange < 0) null
            else kotlin.math.ceil(diff / weeklyChange).toInt()
        }

        if (bmi >= 18.5 && profile.targetWeightKg != null) {
            val targetBmi = bmi(profile.targetWeightKg, profile.heightCm)
            if (targetBmi < 18.5) {
                warnings += "Berat target membuat BMI di bawah 18,5. Pertimbangkan target yang lebih realistis."
            }
        }

        return DailyTarget(
            bmr = bmr.roundToInt(),
            tdee = tdee.roundToInt(),
            calories = calories,
            proteinG = macros.proteinG,
            carbsG = macros.carbsG,
            fatG = macros.fatG,
            fiberG = macros.fiberG,
            adjustment = adjustment,
            bmi = (bmi * 10).roundToInt() / 10.0,
            bmiCategory = bmiCategory(bmi),
            weeklyChangeKg = (weeklyChange * 100).roundToInt() / 100.0,
            weeksToTarget = weeksToTarget,
            warnings = warnings,
            adaptive = measuredTdee != null,
        )
    }

    data class MacroTargets(val proteinG: Int, val carbsG: Int, val fatG: Int, val fiberG: Int)

    /**
     * Protein berdasarkan berat badan (1,2–1,6 g/kg, maks 30% energi), lemak 28% energi,
     * karbo sisa energi, serat 14 g per 1000 kkal (min 25 g).
     */
    fun macroTargets(calories: Int, weightKg: Double, goal: Goal): MacroTargets {
        val perKg = when (goal) {
            Goal.TURUN -> 1.6
            Goal.JAGA -> 1.2
            Goal.NAIK -> 1.6
        }
        val proteinG = min(weightKg * perKg, calories * 0.30 / 4)
        val fatG = calories * 0.28 / 9
        val carbsG = max(0.0, (calories - proteinG * 4 - fatG * 9) / 4)
        val fiberG = max(25.0, calories / 1000.0 * 14)
        return MacroTargets(proteinG.roundToInt(), carbsG.roundToInt(), fatG.roundToInt(), fiberG.roundToInt())
    }

    private fun roundTo10(value: Double): Int = ((value / 10.0).roundToInt() * 10)
}
