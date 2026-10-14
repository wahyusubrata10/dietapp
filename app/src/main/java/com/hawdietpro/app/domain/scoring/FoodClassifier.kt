package com.hawdietpro.app.domain.scoring

import com.hawdietpro.app.domain.config.InsightRulesFile
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.util.TextNormalizer

/** Mengelompokkan elemen makanan berdasarkan kategori DB & kata kunci dari aturan remote. */
class FoodClassifier(private val rules: InsightRulesFile) {

    private val vegKeywords = rules.vegetableKeywords.map(TextNormalizer::normalize)
    private val friedKeywords = rules.friedKeywords.map(TextNormalizer::normalize)
    private val santanKeywords = rules.santanKeywords.map(TextNormalizer::normalize)
    private val sweetDrinkKeywords = rules.sweetDrinkKeywords.map(TextNormalizer::normalize)

    private fun text(item: FoodItem): String =
        TextNormalizer.normalize(item.name + " " + (item.cookingMethod ?: ""))

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        if (keywords.isEmpty()) return false
        val padded = " $text "
        return keywords.any { it.isNotBlank() && padded.contains(" $it ") }
    }

    fun isVegetable(item: FoodItem): Boolean =
        item.category in rules.vegetableCategories || containsAny(text(item), vegKeywords)

    fun isFried(item: FoodItem): Boolean =
        item.category == "gorengan" || containsAny(text(item), friedKeywords)

    fun isSantan(item: FoodItem): Boolean = containsAny(text(item), santanKeywords)

    fun isSweetDrink(item: FoodItem): Boolean {
        val t = text(item)
        if (containsAny(t, sweetDrinkKeywords)) return item.nutrients.calories >= 20
        return item.category == "minuman" && item.nutrients.calories >= 40
    }
}
