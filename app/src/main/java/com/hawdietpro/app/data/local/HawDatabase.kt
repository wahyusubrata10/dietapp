package com.hawdietpro.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hawdietpro.app.data.local.dao.ExerciseDao
import com.hawdietpro.app.data.local.dao.FoodReferenceDao
import com.hawdietpro.app.data.local.dao.MealDao
import com.hawdietpro.app.data.local.dao.PendingAnalysisDao
import com.hawdietpro.app.data.local.dao.UserProfileDao
import com.hawdietpro.app.data.local.dao.WeightLogDao
import com.hawdietpro.app.data.local.entity.ExerciseEntity
import com.hawdietpro.app.data.local.entity.FoodElementEntity
import com.hawdietpro.app.data.local.entity.FoodReferenceEntity
import com.hawdietpro.app.data.local.entity.MealEntryEntity
import com.hawdietpro.app.data.local.entity.PendingAnalysisEntity
import com.hawdietpro.app.data.local.entity.UserProfileEntity
import com.hawdietpro.app.data.local.entity.WeightLogEntity

/**
 * Database utama. ATURAN: setiap menaikkan [DATABASE_VERSION] wajib menambah
 * Migration di [Migrations.ALL] (tidak memakai destructive migration) agar data
 * pengguna tidak pernah hilang saat update aplikasi.
 */
@Database(
    entities = [
        UserProfileEntity::class,
        MealEntryEntity::class,
        FoodElementEntity::class,
        FoodReferenceEntity::class,
        WeightLogEntity::class,
        PendingAnalysisEntity::class,
        ExerciseEntity::class,
    ],
    version = HawDatabase.DATABASE_VERSION,
    exportSchema = true,
)
abstract class HawDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun foodReferenceDao(): FoodReferenceDao
    abstract fun weightLogDao(): WeightLogDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun pendingAnalysisDao(): PendingAnalysisDao
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        const val DATABASE_VERSION = 3
        const val DATABASE_NAME = "haw_diet.db"
    }
}
