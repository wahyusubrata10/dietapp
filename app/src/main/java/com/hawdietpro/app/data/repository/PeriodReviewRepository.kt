package com.hawdietpro.app.data.repository

import android.content.Context
import com.hawdietpro.app.data.gemini.GeminiClient
import com.hawdietpro.app.data.gemini.GeminiReview
import com.hawdietpro.app.domain.insight.Period
import com.hawdietpro.app.domain.insight.PeriodStats
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.UserProfile
import com.hawdietpro.app.domain.nutrition.DailyTarget
import com.hawdietpro.app.domain.nutrition.WeightProjection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Serializable
data class CachedReview(
    val key: String,
    val dataHash: String,
    val createdAt: Long,
    val review: GeminiReview,
)

/**
 * Analisa mendalam AI per periode. Yang dikirim ke Gemini hanya ringkasan angka (tanpa foto),
 * dan hasilnya disimpan agar tidak meminta ulang selama datanya belum berubah.
 */
@Singleton
class PeriodReviewRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val gemini: GeminiClient,
    private val json: Json,
) {
    private val dir = File(context.filesDir, "reviews").apply { mkdirs() }

    fun available(): Boolean = gemini.reviewAvailable()

    fun key(period: Period, stats: PeriodStats) = "${period.name}_${stats.start}"

    fun dataHash(stats: PeriodStats) = "${stats.daysLogged}-${stats.totalCalories.roundToInt()}-${stats.endInclusive}"

    suspend fun cached(period: Period, stats: PeriodStats): CachedReview? = withContext(Dispatchers.IO) {
        val f = File(dir, key(period, stats) + ".json")
        if (!f.exists()) null else runCatching { json.decodeFromString(CachedReview.serializer(), f.readText()) }.getOrNull()
    }

    suspend fun generate(
        period: Period,
        stats: PeriodStats,
        previous: PeriodStats?,
        target: DailyTarget?,
        profile: UserProfile?,
        projection: WeightProjection?,
    ): CachedReview {
        val input = buildInput(period, stats, previous, target, profile, projection)
        val review = gemini.generateReview(input)
        val cached = CachedReview(key(period, stats), dataHash(stats), System.currentTimeMillis(), review)
        withContext(Dispatchers.IO) {
            File(dir, cached.key + ".json").writeText(json.encodeToString(CachedReview.serializer(), cached))
            // Simpan maksimal 20 analisa terakhir.
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { it.delete() }
        }
        return cached
    }

    private fun r1(v: Double) = (v * 10).roundToInt() / 10.0

    /** Ringkasan statistik dalam JSON berbahasa Indonesia untuk prompt. */
    fun buildInput(
        period: Period,
        stats: PeriodStats,
        previous: PeriodStats?,
        target: DailyTarget?,
        profile: UserProfile?,
        projection: WeightProjection?,
    ): String {
        val avg = stats.averagePerDay
        val split = avg.macroEnergy
        val total = stats.totalCalories.takeIf { it > 0 } ?: 1.0
        val obj = buildJsonObject {
            put("periode", if (period == Period.MONTH) "bulanan" else "mingguan")
            put("tanggal", "${stats.start} s/d ${stats.endInclusive}")
            profile?.let { p ->
                putJsonObject("profil") {
                    put("nama", p.name)
                    put("umur", p.age)
                    put("jenis_kelamin", p.sex.label)
                    put("tinggi_cm", r1(p.heightCm))
                    put("berat_kg", r1(p.weightKg))
                    p.targetWeightKg?.let { put("berat_target_kg", r1(it)) }
                    put("aktivitas", p.activityLevel.label)
                    put("tujuan", p.goal.label)
                }
            }
            target?.let { t ->
                putJsonObject("target_harian") {
                    put("kalori", t.calories)
                    put("protein_g", t.proteinG)
                    put("karbo_g", t.carbsG)
                    put("lemak_g", t.fatG)
                    put("serat_g", t.fiberG)
                    put("tdee", t.tdee)
                    put("bmi", t.bmi)
                }
            }
            put("hari_dalam_periode", stats.days.size)
            put("hari_tercatat", stats.daysLogged)
            put("streak_hari", stats.currentStreak)
            putJsonObject("rata_rata_per_hari_tercatat") {
                put("kalori", avg.calories.roundToInt())
                put("protein_g", r1(avg.protein))
                put("karbo_g", r1(avg.carbs))
                put("lemak_g", r1(avg.fat))
                put("serat_g", r1(avg.fiber))
                put("persen_energi_protein", split.proteinPct.roundToInt())
                put("persen_energi_karbo", split.carbsPct.roundToInt())
                put("persen_energi_lemak", split.fatPct.roundToInt())
            }
            put("hari_sesuai_target", stats.daysOnTarget)
            put("hari_lebih_target", stats.daysOver)
            put("hari_kurang_target", stats.daysUnder)
            putJsonObject("persen_kalori_per_waktu_makan") {
                MealType.entries.forEach { put(it.label, ((stats.mealTypeCalories[it] ?: 0.0) / total * 100).roundToInt()) }
            }
            put("persen_kalori_setelah_jam_21", (stats.metrics["lateNightShare"] ?: 0.0).roundToInt())
            put("persen_kalori_gorengan", (stats.metrics["friedShare"] ?: 0.0).roundToInt())
            put("persen_kalori_minuman_manis", (stats.metrics["sweetDrinkShare"] ?: 0.0).roundToInt())
            put("persen_hari_ada_sayur", (stats.metrics["vegDaysPct"] ?: 0.0).roundToInt())
            put("hari_tanpa_sarapan", (stats.metrics["breakfastSkipDays"] ?: 0.0).roundToInt())
            putJsonObject("jumlah_makan_per_skor_gizi") {
                stats.gradeCounts.forEach { (g, c) -> put(g.name, c) }
            }
            putJsonArray("menu_tersering") {
                stats.topMenus.forEach { m ->
                    addJsonObject {
                        put("menu", m.name)
                        put("kali", m.count)
                        put("total_kkal", m.calories.roundToInt())
                    }
                }
            }
            putJsonArray("sumber_kalori_terbesar") {
                stats.topSources.forEach { s ->
                    addJsonObject {
                        put("elemen", s.name)
                        put("persen", s.sharePct.roundToInt())
                    }
                }
            }
            putJsonArray("kalori_harian") {
                stats.days.forEach { d ->
                    addJsonObject {
                        put("tanggal", d.date.toString())
                        put("kkal", d.nutrients.calories.roundToInt())
                        put("jumlah_makan", d.mealCount)
                    }
                }
            }
            previous?.takeIf { it.daysLogged > 0 }?.let { prev ->
                putJsonObject("periode_sebelumnya") {
                    put("hari_tercatat", prev.daysLogged)
                    put("rata_kalori", prev.averagePerDay.calories.roundToInt())
                    put("rata_protein_g", r1(prev.averagePerDay.protein))
                    put("rata_serat_g", r1(prev.averagePerDay.fiber))
                    put("hari_lebih_target", prev.daysOver)
                }
            }
            projection?.let { pr ->
                putJsonObject("berat_badan") {
                    put("tanggal_mulai", pr.startDate.toString())
                    put("berat_awal_kg", r1(pr.startWeight))
                    put("perkiraan_hari_ini_kg", r1(pr.estimatedToday))
                    pr.latestActual?.let { put("timbangan_terakhir_kg", r1(it.weightKg)) }
                    pr.actualChange?.let { put("perubahan_aktual_kg", r1(it)) }
                    put("perubahan_perkiraan_kg", r1(pr.estimatedChange))
                    pr.recentDailyBalance?.let { put("rata_surplus_defisit_14_hari_kkal", it.roundToInt()) }
                    pr.targetDate?.let { put("perkiraan_tanggal_capai_target", it.toString()) }
                }
            }
        }
        return obj.toString()
    }
}
