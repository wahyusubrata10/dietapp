package com.hawdietpro.app.data.local

import com.hawdietpro.app.data.local.entity.FoodElementEntity
import com.hawdietpro.app.data.local.entity.FoodReferenceEntity
import com.hawdietpro.app.data.local.entity.MealEntryEntity
import com.hawdietpro.app.data.local.entity.MealWithElements
import com.hawdietpro.app.data.local.entity.UserProfileEntity
import com.hawdietpro.app.data.local.entity.WeightLogEntity
import com.hawdietpro.app.domain.config.FoodDbItem
import com.hawdietpro.app.domain.model.ActivityLevel
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.FoodReference
import com.hawdietpro.app.domain.model.Goal
import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.NutritionSource
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.model.Sex
import com.hawdietpro.app.domain.model.UserProfile
import com.hawdietpro.app.domain.model.WeightEntry
import com.hawdietpro.app.domain.util.TextNormalizer

private const val SEP = "|"

fun MealWithElements.toDomain(): Meal = Meal(
    id = meal.id,
    eatenAt = meal.eatenAt,
    mealType = MealType.parse(meal.mealType),
    menuName = meal.menuName,
    photoPath = meal.photoPath,
    confidence = meal.confidence,
    items = elements.sortedBy { it.position }.map { it.toDomain() },
    grade = Grade.parse(meal.grade),
    assessments = meal.assessments.split(SEP).filter { it.isNotBlank() },
    aiNotes = meal.aiNotes,
    suggestions = meal.suggestions.split("\n").filter { it.isNotBlank() },
    userNote = meal.userNote,
    favorite = meal.favorite,
)

fun FoodElementEntity.toDomain(): FoodItem = FoodItem(
    id = id,
    name = name,
    grams = grams,
    nutrients = Nutrients(calories, protein, carbs, fat, fiber, sugar, sodium),
    cookingMethod = cookingMethod,
    source = NutritionSource.parse(nutritionSource),
    foodRefId = foodRefId,
    category = category,
    aiNutrients = aiCalories?.let { Nutrients(calories = it) },
)

fun Meal.toEntity(now: Long, createdAt: Long = now): MealEntryEntity {
    val t = total
    return MealEntryEntity(
        id = id,
        eatenAt = eatenAt,
        mealType = mealType.name,
        menuName = menuName,
        photoPath = photoPath,
        confidence = confidence,
        totalCalories = t.calories,
        totalProtein = t.protein,
        totalCarbs = t.carbs,
        totalFat = t.fat,
        totalFiber = t.fiber,
        totalSugar = t.sugar,
        totalSodium = t.sodium,
        favorite = favorite,
        grade = grade?.name,
        assessments = assessments.joinToString(SEP),
        aiNotes = aiNotes,
        suggestions = suggestions.joinToString("\n"),
        userNote = userNote,
        createdAt = createdAt,
        updatedAt = now,
    )
}

fun FoodItem.toEntity(mealId: Long, position: Int): FoodElementEntity = FoodElementEntity(
    id = 0,
    mealId = mealId,
    position = position,
    name = name,
    grams = grams,
    calories = nutrients.calories,
    protein = nutrients.protein,
    carbs = nutrients.carbs,
    fat = nutrients.fat,
    fiber = nutrients.fiber,
    sugar = nutrients.sugar,
    sodium = nutrients.sodium,
    cookingMethod = cookingMethod,
    nutritionSource = source.name,
    foodRefId = foodRefId,
    category = category,
    aiCalories = aiNutrients?.calories,
)

fun FoodReferenceEntity.toDomain(): FoodReference = FoodReference(
    id = id,
    name = name,
    aliases = aliases.split(SEP).filter { it.isNotBlank() },
    category = category,
    per100g = Nutrients(calories, protein, carbs, fat, fiber, sugar ?: 0.0, sodium ?: 0.0),
    defaultServingG = defaultServingG,
    servingLabel = servingLabel,
    source = source,
    hasSugarSodium = sugar != null && sodium != null,
)

fun FoodReference.toEntity(dataVersion: Int): FoodReferenceEntity = FoodReferenceEntity(
    id = id,
    name = name,
    normalizedName = TextNormalizer.normalize(name),
    aliases = aliases.map(TextNormalizer::normalize).filter { it.isNotBlank() }.joinToString(SEP),
    category = category,
    calories = per100g.calories,
    protein = per100g.protein,
    carbs = per100g.carbs,
    fat = per100g.fat,
    fiber = per100g.fiber,
    sugar = if (hasSugarSodium) per100g.sugar else null,
    sodium = if (hasSugarSodium) per100g.sodium else null,
    defaultServingG = defaultServingG,
    servingLabel = servingLabel,
    source = source,
    dataVersion = dataVersion,
)

fun FoodDbItem.toReference(): FoodReference = FoodReference(
    id = id,
    name = name,
    aliases = aliases,
    category = category,
    per100g = Nutrients(kcal, protein, carbs, fat, fiber, sugar ?: 0.0, sodium ?: 0.0),
    defaultServingG = servingG,
    servingLabel = servingLabel,
    source = source,
    hasSugarSodium = sugar != null && sodium != null,
)

fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
    name = name,
    age = age,
    sex = Sex.parse(sex),
    heightCm = heightCm,
    weightKg = weightKg,
    activityLevel = ActivityLevel.parse(activityLevel),
    goal = Goal.parse(goal),
    targetWeightKg = targetWeightKg,
    customCalories = customCalories,
)

fun UserProfile.toEntity(now: Long): UserProfileEntity = UserProfileEntity(
    id = 1,
    name = name,
    age = age,
    sex = sex.name,
    heightCm = heightCm,
    weightKg = weightKg,
    activityLevel = activityLevel.name,
    goal = goal.name,
    targetWeightKg = targetWeightKg,
    customCalories = customCalories,
    updatedAt = now,
)

fun WeightLogEntity.toDomain() = WeightEntry(id, epochDay, weightKg, note)
