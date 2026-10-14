package com.hawdietpro.app.domain.model

enum class MealType(val label: String, val emoji: String) {
    SARAPAN("Sarapan", "🌅"),
    MAKAN_SIANG("Makan Siang", "☀️"),
    MAKAN_MALAM("Makan Malam", "🌙"),
    CAMILAN("Camilan", "🍪");

    companion object {
        /** Tebakan waktu makan dari jam (0–23). */
        fun fromHour(hour: Int): MealType = when (hour) {
            in 4..9 -> SARAPAN
            10 -> CAMILAN
            in 11..14 -> MAKAN_SIANG
            in 15..16 -> CAMILAN
            in 17..20 -> MAKAN_MALAM
            else -> CAMILAN
        }

        fun parse(value: String?): MealType = entries.firstOrNull { it.name == value } ?: CAMILAN
    }
}

/** Asal angka gizi sebuah elemen makanan. */
enum class NutritionSource(val label: String) {
    AI("AI"),
    DB("DB lokal"),
    MANUAL("Manual");

    companion object {
        fun parse(value: String?): NutritionSource = entries.firstOrNull { it.name == value } ?: AI
    }
}

enum class Sex(val label: String) {
    PRIA("Pria"),
    WANITA("Wanita");

    companion object {
        fun parse(value: String?): Sex = entries.firstOrNull { it.name == value } ?: PRIA
    }
}

enum class ActivityLevel(val label: String, val description: String, val factor: Double) {
    SEDENTARI("Jarang bergerak", "Kerja duduk, hampir tidak olahraga", 1.2),
    RINGAN("Ringan", "Olahraga ringan 1–3x/minggu", 1.375),
    SEDANG("Sedang", "Olahraga 3–5x/minggu", 1.55),
    AKTIF("Aktif", "Olahraga berat 6–7x/minggu", 1.725),
    SANGAT_AKTIF("Sangat aktif", "Kerja fisik berat + latihan harian", 1.9);

    companion object {
        fun parse(value: String?): ActivityLevel = entries.firstOrNull { it.name == value } ?: SEDENTARI
    }
}

enum class Goal(val label: String) {
    TURUN("Turunkan berat"),
    JAGA("Jaga berat"),
    NAIK("Naikkan berat");

    companion object {
        fun parse(value: String?): Goal = entries.firstOrNull { it.name == value } ?: JAGA
    }
}

enum class Grade { A, B, C, D, E;

    companion object {
        fun parse(value: String?): Grade? = entries.firstOrNull { it.name == value }
    }
}
