package com.hawdietpro.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.hawdietpro.app.data.local.entity.BarcodeProductEntity
import com.hawdietpro.app.data.local.entity.CalorieEntry
import com.hawdietpro.app.data.local.entity.ExerciseEntity
import com.hawdietpro.app.data.local.entity.FoodElementEntity
import com.hawdietpro.app.data.local.entity.FoodReferenceEntity
import com.hawdietpro.app.data.local.entity.MealEntryEntity
import com.hawdietpro.app.data.local.entity.MealWithElements
import com.hawdietpro.app.data.local.entity.PendingAnalysisEntity
import com.hawdietpro.app.data.local.entity.UserProfileEntity
import com.hawdietpro.app.data.local.entity.WeightLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MealDao {

    @Insert
    abstract suspend fun insertMeal(meal: MealEntryEntity): Long

    @Update
    abstract suspend fun updateMeal(meal: MealEntryEntity)

    @Insert
    abstract suspend fun insertElements(elements: List<FoodElementEntity>)

    @Query("DELETE FROM food_element WHERE meal_id = :mealId")
    abstract suspend fun deleteElements(mealId: Long)

    /** Menyimpan makan + seluruh elemennya secara atomik. Mengembalikan id makan. */
    @Transaction
    open suspend fun saveMeal(meal: MealEntryEntity, elements: List<FoodElementEntity>): Long {
        val id = if (meal.id == 0L) insertMeal(meal) else meal.id.also { updateMeal(meal) }
        deleteElements(id)
        insertElements(elements.mapIndexed { index, e -> e.copy(id = 0, mealId = id, position = index) })
        return id
    }

    @Query("DELETE FROM meal_entry WHERE id = :id")
    abstract suspend fun deleteMeal(id: Long)

    @Query("UPDATE meal_entry SET favorite = :favorite WHERE id = :id")
    abstract suspend fun setFavorite(id: Long, favorite: Boolean)

    @Transaction
    @Query("SELECT * FROM meal_entry WHERE favorite = 1 ORDER BY eaten_at DESC")
    abstract fun observeFavorites(): Flow<List<MealWithElements>>

    @Transaction
    @Query("SELECT * FROM meal_entry ORDER BY eaten_at DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<MealWithElements>>

    @Transaction
    @Query("SELECT * FROM meal_entry ORDER BY eaten_at ASC")
    abstract fun observeAllMeals(): Flow<List<MealWithElements>>

    @Transaction
    @Query("SELECT * FROM meal_entry WHERE id = :id")
    abstract fun observeMeal(id: Long): Flow<MealWithElements?>

    @Transaction
    @Query("SELECT * FROM meal_entry WHERE id = :id")
    abstract suspend fun getMeal(id: Long): MealWithElements?

    @Transaction
    @Query("SELECT * FROM meal_entry WHERE eaten_at >= :start AND eaten_at < :end ORDER BY eaten_at DESC")
    abstract fun observeMealsBetween(start: Long, end: Long): Flow<List<MealWithElements>>

    @Transaction
    @Query("SELECT * FROM meal_entry WHERE eaten_at >= :start AND eaten_at < :end ORDER BY eaten_at DESC")
    abstract suspend fun getMealsBetween(start: Long, end: Long): List<MealWithElements>

    @Transaction
    @Query(
        """
        SELECT * FROM meal_entry
        WHERE menu_name LIKE '%' || :query || '%'
           OR user_note LIKE '%' || :query || '%'
           OR id IN (SELECT meal_id FROM food_element WHERE name LIKE '%' || :query || '%')
        ORDER BY eaten_at DESC
        LIMIT :limit
        """,
    )
    abstract fun searchMeals(query: String, limit: Int = 200): Flow<List<MealWithElements>>

    @Query("SELECT eaten_at AS eatenAt, total_calories AS calories FROM meal_entry ORDER BY eaten_at ASC")
    abstract fun observeCalorieEntries(): Flow<List<CalorieEntry>>

    @Query("SELECT eaten_at FROM meal_entry WHERE eaten_at >= :start AND eaten_at < :end")
    abstract fun observeMealTimes(start: Long, end: Long): Flow<List<Long>>

    @Transaction
    @Query("SELECT * FROM meal_entry ORDER BY eaten_at ASC")
    abstract suspend fun getAllMeals(): List<MealWithElements>

    @Query("SELECT photo_path FROM meal_entry WHERE photo_path IS NOT NULL")
    abstract suspend fun getAllPhotoPaths(): List<String>

    @Query("SELECT COUNT(*) FROM meal_entry")
    abstract suspend fun count(): Int

    @Query("SELECT MIN(eaten_at) FROM meal_entry")
    abstract suspend fun firstMealTime(): Long?

    @Query("DELETE FROM meal_entry")
    abstract suspend fun deleteAll()
}

@Dao
interface FoodReferenceDao {
    @Query("SELECT * FROM food_reference ORDER BY name")
    suspend fun getAll(): List<FoodReferenceEntity>

    @Query("SELECT * FROM food_reference ORDER BY name")
    fun observeAll(): Flow<List<FoodReferenceEntity>>

    @Query("SELECT * FROM food_reference WHERE id = :id")
    suspend fun getById(id: String): FoodReferenceEntity?

    @Upsert
    suspend fun upsertAll(items: List<FoodReferenceEntity>)

    /** Hapus makanan bawaan yang sudah tidak ada di versi data terbaru (makanan buatan pengguna aman). */
    @Query("DELETE FROM food_reference WHERE source != 'USER' AND data_version != :version")
    suspend fun deleteBundledOlderThan(version: Int)

    @Query("SELECT * FROM food_reference WHERE source = 'USER'")
    suspend fun getUserFoods(): List<FoodReferenceEntity>

    @Query("SELECT COUNT(*) FROM food_reference")
    suspend fun count(): Int
}

@Dao
interface WeightLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<WeightLogEntity>)

    @Query("SELECT * FROM weight_log ORDER BY epoch_day ASC")
    fun observeAll(): Flow<List<WeightLogEntity>>

    @Query("SELECT * FROM weight_log ORDER BY epoch_day ASC")
    suspend fun getAll(): List<WeightLogEntity>

    @Query("SELECT * FROM weight_log ORDER BY epoch_day DESC LIMIT 1")
    suspend fun latest(): WeightLogEntity?

    @Query("DELETE FROM weight_log WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM weight_log")
    suspend fun deleteAll()
}

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<ExerciseEntity>)

    @Query("SELECT * FROM exercise_log WHERE epoch_day BETWEEN :startDay AND :endDay ORDER BY performed_at ASC")
    fun observeRange(startDay: Long, endDay: Long): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise_log ORDER BY epoch_day ASC, performed_at ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise_log ORDER BY epoch_day ASC, performed_at ASC")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("DELETE FROM exercise_log WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM exercise_log")
    suspend fun deleteAll()
}

@Dao
interface BarcodeProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BarcodeProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<BarcodeProductEntity>)

    @Query("SELECT * FROM barcode_product WHERE barcode = :barcode")
    suspend fun get(barcode: String): BarcodeProductEntity?

    @Query("SELECT * FROM barcode_product ORDER BY updated_at DESC")
    suspend fun getAll(): List<BarcodeProductEntity>

    @Query("SELECT * FROM barcode_product ORDER BY updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BarcodeProductEntity>>

    @Query("DELETE FROM barcode_product WHERE barcode = :barcode")
    suspend fun delete(barcode: String)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)
}

@Dao
interface PendingAnalysisDao {
    @Insert
    suspend fun insert(item: PendingAnalysisEntity): Long

    @Update
    suspend fun update(item: PendingAnalysisEntity)

    @Query("SELECT * FROM pending_analysis ORDER BY captured_at ASC")
    fun observeAll(): Flow<List<PendingAnalysisEntity>>

    @Query("SELECT * FROM pending_analysis WHERE status != 'DIPROSES' ORDER BY captured_at ASC")
    suspend fun getProcessable(): List<PendingAnalysisEntity>

    @Query("SELECT * FROM pending_analysis WHERE id = :id")
    suspend fun getById(id: Long): PendingAnalysisEntity?

    @Query("UPDATE pending_analysis SET status = 'MENUNGGU' WHERE status = 'DIPROSES'")
    suspend fun resetStuck()

    @Query("DELETE FROM pending_analysis WHERE id = :id")
    suspend fun delete(id: Long)
}
