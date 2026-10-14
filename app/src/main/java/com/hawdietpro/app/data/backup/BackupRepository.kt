package com.hawdietpro.app.data.backup

import android.content.Context
import android.net.Uri
import com.hawdietpro.app.BuildConfig
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.core.ImageProcessor
import com.hawdietpro.app.data.local.HawDatabase
import com.hawdietpro.app.data.local.dao.FoodReferenceDao
import com.hawdietpro.app.data.local.dao.MealDao
import com.hawdietpro.app.data.local.dao.UserProfileDao
import com.hawdietpro.app.data.local.dao.WeightLogDao
import com.hawdietpro.app.data.local.entity.WeightLogEntity
import com.hawdietpro.app.data.local.toDomain
import com.hawdietpro.app.data.local.toEntity
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
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Backup ZIP (backup.json + photos/) dan ekspor CSV untuk Excel. */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: HawDatabase,
    private val meals: MealDao,
    private val weights: WeightLogDao,
    private val profiles: UserProfileDao,
    private val foods: FoodReferenceDao,
    private val images: ImageProcessor,
    private val json: Json,
) {
    data class ImportResult(val meals: Int, val weights: Int, val photos: Int)

    suspend fun exportZip(target: Uri): Int = withContext(Dispatchers.IO) {
        val allMeals = meals.getAllMeals().map { it.toDomain() }
        val photoNames = mutableMapOf<String, String>()
        val backup = BackupFile(
            appVersionCode = BuildConfig.VERSION_CODE,
            exportedAt = System.currentTimeMillis(),
            profile = profiles.get()?.toDomain()?.let {
                BackupProfile(it.name, it.age, it.sex.name, it.heightCm, it.weightKg, it.activityLevel.name, it.goal.name, it.targetWeightKg, it.customCalories)
            },
            meals = allMeals.map { m ->
                val photo = m.photoPath?.let { path -> File(path).takeIf { it.exists() }?.let { photoNames.getOrPut(path) { it.name } } }
                BackupMeal(
                    eatenAt = m.eatenAt,
                    mealType = m.mealType.name,
                    menuName = m.menuName,
                    photo = photo,
                    confidence = m.confidence,
                    grade = m.grade?.name,
                    assessments = m.assessments,
                    aiNotes = m.aiNotes,
                    suggestions = m.suggestions,
                    userNote = m.userNote,
                    items = m.items.map { i ->
                        BackupItem(
                            i.name, i.grams, i.nutrients.calories, i.nutrients.protein, i.nutrients.carbs, i.nutrients.fat, i.nutrients.fiber,
                            i.cookingMethod, i.source.name, i.foodRefId, i.category, i.aiNutrients?.calories,
                        )
                    },
                )
            },
            weights = weights.getAll().map { BackupWeight(it.epochDay, it.weightKg, it.note) },
            userFoods = foods.getUserFoods().map { f ->
                BackupFood(f.id, f.name, f.category, f.calories, f.protein, f.carbs, f.fat, f.fiber, f.defaultServingG)
            },
        )
        val out = context.contentResolver.openOutputStream(target) ?: error("Tidak bisa menulis file")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_NAME))
            zip.write(json.encodeToString(BackupFile.serializer(), backup).toByteArray())
            zip.closeEntry()
            photoNames.forEach { (path, name) ->
                zip.putNextEntry(ZipEntry("photos/$name"))
                File(path).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        allMeals.size
    }

    /** [replaceAll] = hapus semua catatan lama dulu; false = gabungkan (catatan identik dilewati). */
    suspend fun importZip(source: Uri, replaceAll: Boolean): ImportResult = withContext(Dispatchers.IO) {
        var backup: BackupFile? = null
        val photoMap = mutableMapOf<String, String>()
        val input = context.contentResolver.openInputStream(source) ?: error("Tidak bisa membuka file")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == JSON_NAME -> backup = json.decodeFromString(BackupFile.serializer(), zip.readBytes().decodeToString())
                    entry.name.startsWith("photos/") && !entry.isDirectory -> {
                        val original = entry.name.substringAfterLast('/')
                        // Nama file dibuat baru agar aman (tidak ada path traversal).
                        val dest = File(images.photoDir, "imp_${UUID.randomUUID().toString().take(8)}_${original.filter { it.isLetterOrDigit() || it == '.' || it == '_' }}")
                        dest.outputStream().use { zip.copyTo(it) }
                        photoMap[original] = dest.path
                    }
                }
                zip.closeEntry()
            }
        }
        val data = backup ?: error("File bukan backup Haw Diet Pro (backup.json tidak ada)")

        var mealCount = 0
        db.withTransaction {
            if (replaceAll) {
                meals.getAllPhotoPaths().forEach { File(it).delete() }
                meals.deleteAll()
                weights.deleteAll()
            }
            val existing = if (replaceAll) emptySet() else meals.getAllMeals().map { it.meal.eatenAt to it.meal.menuName }.toSet()
            data.meals.forEach { bm ->
                if ((bm.eatenAt to bm.menuName) in existing) return@forEach
                val meal = Meal(
                    eatenAt = bm.eatenAt,
                    mealType = MealType.parse(bm.mealType),
                    menuName = bm.menuName,
                    photoPath = bm.photo?.let { photoMap[it] },
                    confidence = bm.confidence,
                    items = bm.items.map { i ->
                        FoodItem(
                            name = i.name,
                            grams = i.grams,
                            nutrients = Nutrients(i.calories, i.protein, i.carbs, i.fat, i.fiber),
                            cookingMethod = i.cookingMethod,
                            source = NutritionSource.parse(i.source),
                            foodRefId = i.foodRefId,
                            category = i.category,
                            aiNutrients = i.aiCalories?.let { Nutrients(calories = it) },
                        )
                    },
                    grade = Grade.parse(bm.grade),
                    assessments = bm.assessments,
                    aiNotes = bm.aiNotes,
                    suggestions = bm.suggestions,
                    userNote = bm.userNote,
                )
                val now = System.currentTimeMillis()
                meals.saveMeal(meal.toEntity(now), meal.items.mapIndexed { idx, it -> it.toEntity(0, idx) })
                mealCount++
            }
            weights.upsertAll(data.weights.map { WeightLogEntity(epochDay = it.epochDay, weightKg = it.weightKg, note = it.note, createdAt = System.currentTimeMillis()) })
            if (data.userFoods.isNotEmpty()) {
                foods.upsertAll(
                    data.userFoods.map { f ->
                        FoodReference(f.id, f.name, emptyList(), f.category, Nutrients(f.kcal, f.protein, f.carbs, f.fat, f.fiber), f.servingG, "1 porsi", "USER").toEntity(0)
                    },
                )
            }
            data.profile?.let { p ->
                if (replaceAll || profiles.get() == null) {
                    profiles.upsert(
                        UserProfile(
                            p.name, p.age, Sex.parse(p.sex), p.heightCm, p.weightKg,
                            ActivityLevel.parse(p.activityLevel), Goal.parse(p.goal), p.targetWeightKg, p.customCalories,
                        ).toEntity(System.currentTimeMillis()),
                    )
                }
            }
        }
        // Foto yang tidak dipakai (catatan duplikat) dibersihkan.
        val used = meals.getAllPhotoPaths().toSet()
        photoMap.values.filter { it !in used }.forEach { File(it).delete() }
        ImportResult(mealCount, data.weights.size, photoMap.size)
    }

    /** CSV per elemen makanan; pemisah ";" & desimal koma agar langsung rapi di Excel Indonesia. */
    suspend fun exportMealsCsv(target: Uri): Int = withContext(Dispatchers.IO) {
        val all = meals.getAllMeals().map { it.toDomain() }
        val zone = ZoneId.systemDefault()
        var rows = 0
        val out = context.contentResolver.openOutputStream(target) ?: error("Tidak bisa menulis file")
        OutputStreamWriter(out, Charsets.UTF_8).use { w ->
            w.write("﻿")
            w.write("Tanggal;Jam;Waktu makan;Menu;Elemen;Gram;Kalori (kkal);Protein (g);Karbo (g);Lemak (g);Serat (g);Metode masak;Sumber angka;Skor gizi;Penilaian;Catatan\n")
            all.forEach { m ->
                val dt = Instant.ofEpochMilli(m.eatenAt).atZone(zone)
                m.items.forEach { i ->
                    w.write(
                        listOf(
                            dt.toLocalDate().toString(),
                            Format.time(m.eatenAt),
                            m.mealType.label,
                            m.menuName,
                            i.name,
                            dec(i.grams),
                            dec(i.nutrients.calories),
                            dec(i.nutrients.protein),
                            dec(i.nutrients.carbs),
                            dec(i.nutrients.fat),
                            dec(i.nutrients.fiber),
                            i.cookingMethod.orEmpty(),
                            i.source.label,
                            m.grade?.name.orEmpty(),
                            m.assessments.joinToString(", "),
                            m.userNote.orEmpty(),
                        ).joinToString(";") { csv(it) } + "\n",
                    )
                    rows++
                }
            }
        }
        rows
    }

    suspend fun exportWeightCsv(target: Uri): Int = withContext(Dispatchers.IO) {
        val all = weights.getAll()
        val out = context.contentResolver.openOutputStream(target) ?: error("Tidak bisa menulis file")
        OutputStreamWriter(out, Charsets.UTF_8).use { w ->
            w.write("﻿")
            w.write("Tanggal;Berat (kg);Catatan\n")
            all.forEach { w.write("${LocalDate.ofEpochDay(it.epochDay)};${dec(it.weightKg)};${csv(it.note.orEmpty())}\n") }
        }
        all.size
    }

    private fun dec(v: Double): String = Format.oneDecimal(v)

    private fun csv(value: String): String {
        val needsQuote = value.contains(';') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    private companion object {
        const val JSON_NAME = "backup.json"
    }
}
