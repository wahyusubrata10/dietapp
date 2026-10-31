package com.hawdietpro.app.domain.nutrition

import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.Nutrients
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlausibilityTest {
    private fun item(g: Double, kcal: Double) = FoodItem(name = "x", grams = g, nutrients = Nutrients(calories = kcal))

    @Test
    fun `porsi wajar lolos`() {
        assertNull(Plausibility.check(item(150.0, 195.0)))
        // Air putih 0 kkal tetap wajar.
        assertNull(Plausibility.check(item(250.0, 0.0)))
        // Minyak goreng ±9 kkal/g masih wajar.
        assertNull(Plausibility.check(item(10.0, 88.0)))
    }

    @Test
    fun `angka ekstrem ditandai`() {
        assertNotNull(Plausibility.check(item(2000.0, 500.0)))
        assertNotNull(Plausibility.check(item(300.0, 3200.0)))
        assertNotNull(Plausibility.check(item(50.0, 900.0)))
    }
}
