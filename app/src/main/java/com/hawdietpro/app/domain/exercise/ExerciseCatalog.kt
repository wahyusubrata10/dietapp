package com.hawdietpro.app.domain.exercise

import kotlin.math.max

data class Activity(val id: String, val name: String, val emoji: String, val met: Double, val group: String)

/**
 * Daftar aktivitas umum dengan nilai MET (Compendium of Physical Activities, 2011/2024).
 * Kalori dihitung sebagai kalori AKTIF tambahan: (MET − 1) × berat (kg) × jam.
 * Dikurangi 1 MET karena energi istirahat sudah termasuk di BMR/TDEE, jadi tidak dihitung dua kali.
 */
object ExerciseCatalog {

    val activities: List<Activity> = listOf(
        Activity("jalan_santai", "Jalan santai", "🚶", 3.0, "Kardio"),
        Activity("jalan_cepat", "Jalan cepat", "🚶‍♂️", 4.3, "Kardio"),
        Activity("lari_santai", "Lari santai (8 km/jam)", "🏃", 8.3, "Kardio"),
        Activity("lari_cepat", "Lari cepat (10 km/jam)", "🏃‍♂️", 9.8, "Kardio"),
        Activity("sepeda_santai", "Sepeda santai", "🚲", 4.0, "Kardio"),
        Activity("sepeda_sedang", "Sepeda sedang/cepat", "🚴", 7.5, "Kardio"),
        Activity("sepeda_statis", "Sepeda statis", "🚴‍♂️", 6.8, "Kardio"),
        Activity("renang", "Renang", "🏊", 6.0, "Kardio"),
        Activity("lompat_tali", "Lompat tali", "🪢", 11.0, "Kardio"),
        Activity("naik_tangga", "Naik tangga", "🪜", 8.0, "Kardio"),
        Activity("hiit", "HIIT / sirkuit", "🔥", 8.0, "Kardio"),
        Activity("senam_aerobik", "Senam aerobik / zumba", "💃", 6.5, "Kelas"),
        Activity("senam_pagi", "Senam pagi / SKJ", "🤸", 4.0, "Kelas"),
        Activity("yoga", "Yoga", "🧘", 2.5, "Kelas"),
        Activity("pilates", "Pilates", "🧘‍♀️", 3.0, "Kelas"),
        Activity("gym_beban", "Angkat beban / gym", "🏋️", 5.0, "Kekuatan"),
        Activity("bodyweight", "Push up, squat, plank", "💪", 3.8, "Kekuatan"),
        Activity("futsal", "Futsal / sepak bola", "⚽", 7.0, "Olahraga"),
        Activity("badminton", "Badminton", "🏸", 5.5, "Olahraga"),
        Activity("basket", "Basket", "🏀", 6.5, "Olahraga"),
        Activity("voli", "Voli", "🏐", 4.0, "Olahraga"),
        Activity("tenis", "Tenis", "🎾", 7.3, "Olahraga"),
        Activity("padel", "Padel", "🎾", 6.0, "Olahraga"),
        Activity("tenis_meja", "Tenis meja", "🏓", 4.0, "Olahraga"),
        Activity("bela_diri", "Bela diri / tinju", "🥊", 7.8, "Olahraga"),
        Activity("hiking", "Hiking / naik gunung", "🥾", 6.0, "Luar ruang"),
        Activity("berkebun", "Berkebun", "🌱", 3.8, "Harian"),
        Activity("bersih_rumah", "Bersih-bersih rumah", "🧹", 3.3, "Harian"),
        Activity("main_anak", "Main aktif dengan anak", "🧒", 4.0, "Harian"),
    )

    private val byId = activities.associateBy { it.id }

    fun find(id: String?): Activity? = id?.let(byId::get)

    /** Kalori aktif tambahan (kkal) untuk [minutes] menit pada [weightKg]. */
    fun estimate(met: Double, weightKg: Double, minutes: Int): Double =
        max(0.0, met - 1.0) * weightKg * (minutes.coerceAtLeast(0) / 60.0)
}
