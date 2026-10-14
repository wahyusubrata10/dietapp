package com.hawdietpro.app.domain.nutrition

import com.hawdietpro.app.domain.model.ActivityLevel
import com.hawdietpro.app.domain.model.Goal
import com.hawdietpro.app.domain.model.Sex
import com.hawdietpro.app.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TdeeCalculatorTest {

    private fun profile(
        sex: Sex = Sex.PRIA,
        age: Int = 30,
        height: Double = 170.0,
        weight: Double = 75.0,
        activity: ActivityLevel = ActivityLevel.SEDANG,
        goal: Goal = Goal.JAGA,
        target: Double? = null,
        custom: Int? = null,
    ) = UserProfile("Uji", age, sex, height, weight, activity, goal, target, custom)

    @Test
    fun `BMR pria sesuai rumus Mifflin-St Jeor`() {
        // 10*75 + 6.25*170 - 5*30 + 5 = 1667.5
        assertEquals(1667.5, TdeeCalculator.bmr(Sex.PRIA, 75.0, 170.0, 30), 0.001)
    }

    @Test
    fun `BMR wanita sesuai rumus Mifflin-St Jeor`() {
        // 10*55 + 6.25*158 - 5*25 - 161 = 1251.5
        assertEquals(1251.5, TdeeCalculator.bmr(Sex.WANITA, 55.0, 158.0, 25), 0.001)
    }

    @Test
    fun `TDEE memakai faktor aktivitas`() {
        val result = TdeeCalculator.calculate(profile())
        assertEquals(1668, result.bmr)
        assertEquals(2585, result.tdee) // 1667.5 × 1.55 = 2584.6
        assertEquals(2580, result.calories) // dibulatkan ke 10
        assertEquals(0.0, result.weeklyChangeKg, 0.05)
    }

    @Test
    fun `defisit turun berat maksimal 500 kkal`() {
        val result = TdeeCalculator.calculate(profile(goal = Goal.TURUN))
        // TDEE 2584.6 − 500 = 2084.6 → dibulatkan ke 2080
        assertEquals(2080, result.calories)
        assertTrue(result.adjustment >= -505)
        assertTrue(result.weeklyChangeKg < 0)
    }

    @Test
    fun `defisit dibatasi 20 persen TDEE`() {
        // Wanita kecil, sedentari: TDEE kecil sehingga 20% < 500
        val p = profile(sex = Sex.WANITA, age = 40, height = 155.0, weight = 70.0, activity = ActivityLevel.SEDENTARI, goal = Goal.TURUN)
        val result = TdeeCalculator.calculate(p)
        val tdee = result.tdee
        assertTrue("defisit ${tdee - result.calories} melebihi 20%", tdee - result.calories <= (tdee * 0.20).toInt() + 10)
        assertTrue(result.calories >= TdeeCalculator.MIN_CALORIES_FEMALE)
    }

    @Test
    fun `target tidak pernah di bawah batas minimum`() {
        val p = profile(sex = Sex.WANITA, age = 70, height = 145.0, weight = 45.0, activity = ActivityLevel.SEDENTARI, goal = Goal.TURUN)
        val result = TdeeCalculator.calculate(p)
        assertTrue(result.calories <= result.tdee + 5)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `BMI kurang tidak diberi defisit`() {
        val p = profile(height = 175.0, weight = 52.0, goal = Goal.TURUN)
        val result = TdeeCalculator.calculate(p)
        assertTrue(result.bmi < 18.5)
        assertEquals(0.0, result.adjustment.toDouble(), 10.0)
        assertTrue(result.warnings.any { it.contains("BMI") })
    }

    @Test
    fun `surplus naik berat maksimal 300 kkal`() {
        val result = TdeeCalculator.calculate(profile(goal = Goal.NAIK))
        assertTrue(result.adjustment in 1..305)
    }

    @Test
    fun `target manual terlalu rendah dinaikkan ke batas aman`() {
        val result = TdeeCalculator.calculate(profile(goal = Goal.TURUN, custom = 900))
        assertEquals(TdeeCalculator.MIN_CALORIES_MALE, result.calories)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `makro menjumlah ke target kalori`() {
        val result = TdeeCalculator.calculate(profile(goal = Goal.TURUN))
        val kcal = result.proteinG * 4 + result.carbsG * 4 + result.fatG * 9
        assertEquals(result.calories.toDouble(), kcal.toDouble(), 15.0)
        assertTrue(result.fiberG >= 25)
    }

    @Test
    fun `estimasi minggu menuju berat target`() {
        val result = TdeeCalculator.calculate(profile(goal = Goal.TURUN, target = 70.0))
        // defisit ±505 kkal/hari → ±0.46 kg/minggu → 5 kg ≈ 11 minggu
        assertTrue(result.weeksToTarget in 9..13)
    }

    @Test
    fun `kategori BMI Asia`() {
        assertEquals("Normal", TdeeCalculator.bmiCategory(22.0))
        assertEquals("Berlebih", TdeeCalculator.bmiCategory(24.0))
        assertEquals("Obesitas I", TdeeCalculator.bmiCategory(27.0))
        assertEquals("Berat badan kurang", TdeeCalculator.bmiCategory(17.0))
    }
}
