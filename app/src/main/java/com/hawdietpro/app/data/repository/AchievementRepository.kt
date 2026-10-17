package com.hawdietpro.app.data.repository

import com.hawdietpro.app.data.prefs.AppPreferences
import com.hawdietpro.app.data.remote.RemoteConfigStore
import com.hawdietpro.app.domain.achievement.Achievement
import com.hawdietpro.app.domain.achievement.AchievementCalculator
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
import com.hawdietpro.app.domain.scoring.FoodClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AchievementRepository @Inject constructor(
    private val meals: MealRepository,
    private val weights: WeightRepository,
    private val profiles: ProfileRepository,
    private val store: RemoteConfigStore,
    private val prefs: AppPreferences,
    private val exercises: ExerciseRepository,
) {
    fun observe(): Flow<List<Achievement>> = combine(
        meals.observeAll(),
        weights.observeAll(),
        profiles.observeProfile(),
        store.insightRules,
        exercises.observeAll(),
    ) { mealList, weightList, profile, rules, exerciseList ->
        AchievementCalculator(
            isVegetable = FoodClassifier(rules)::isVegetable,
            targetTolerancePct = rules.targetTolerancePct,
        ).compute(mealList, weightList, profile?.let(TdeeCalculator::calculate), profile?.goal, exerciseList)
    }

    /** Badge yang sudah terbuka tetapi belum pernah dilihat. */
    fun observeNew(): Flow<List<Achievement>> = combine(observe(), prefs.seenAchievements) { list, seen ->
        list.filter { it.unlocked && it.id !in seen }
    }

    suspend fun markAllSeen() {
        prefs.markAchievementsSeen(observe().first().filter { it.unlocked }.map { it.id }.toSet())
    }
}
