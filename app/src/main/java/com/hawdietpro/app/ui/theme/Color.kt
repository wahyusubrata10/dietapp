package com.hawdietpro.app.ui.theme

import androidx.compose.ui.graphics.Color

// Palet "Kebun Segar": hijau daun (segar, sehat) + aprikot hangat (menggugah selera).
// Semua pasangan teks lolos WCAG AA (≥ 4,5:1) di tema terang & gelap.

// ── Brand ───────────────────────────────────────────────────────────────────
val Emerald900 = Color(0xFF073D29)
val Emerald800 = Color(0xFF123A2A)
val Emerald700 = Color(0xFF0E6A49)
val Emerald600 = Color(0xFF127A53)
val Emerald300 = Color(0xFF5CD49F)
val Emerald100 = Color(0xFFD9F2E5)
val Emerald50 = Color(0xFFEDF8F2)

/** Ujung gradasi aksen (tombol +, busur kalori). */
val Teal500 = Color(0xFF1E8C66)

val Orange900 = Color(0xFF5A2306)
val Orange500 = Color(0xFFB5531A)
val Orange300 = Color(0xFFFFAD7A)
val Orange100 = Color(0xFFFFE7D6)

val Indigo900 = Color(0xFF0B2A6B)
val Indigo600 = Color(0xFF2457C5)
val Indigo300 = Color(0xFF8DB2FF)
val Indigo100 = Color(0xFFDCE6FB)

val Red900 = Color(0xFF410002)
val Red600 = Color(0xFFB42318)
val Red300 = Color(0xFFFF8A7E)
val Red100 = Color(0xFFFFE3DF)

// ── Netral hangat (9 langkah) ───────────────────────────────────────────────
object Neutral {
    val N50 = Color(0xFFF7F8F5)
    val N100 = Color(0xFFEFF2EE)
    val N200 = Color(0xFFE2E7E2)
    val N300 = Color(0xFFC8D0CA)
    val N400 = Color(0xFFA3ADA6)
    val N500 = Color(0xFF7A847E)
    val N600 = Color(0xFF5A645E)
    val N700 = Color(0xFF3D4541)
    val N800 = Color(0xFF161B18)
}

val Ink900 = Neutral.N800
/** Teks sekunder terang: 6,4:1 di latar, 6,8:1 di kartu putih. */
val Ink500 = Color(0xFF535D57)
val Ink300 = Neutral.N400
val Line = Neutral.N200
val Paper = Neutral.N50
val White = Color(0xFFFFFFFF)

val NightBg = Color(0xFF0E1210)
val NightCard = Color(0xFF161B19)
val NightCardHigh = Color(0xFF1D2421)
val NightLine = Color(0xFF28312D)
val NightInk = Color(0xFFECF1EE)
/** Teks sekunder gelap: 8,2:1 di kartu. */
val NightInkSub = Color(0xFFA9B4AE)

// ── Warna makro: kontras ≥ 3:1 di latar terang maupun gelap ─────────────────
object MacroColors {
    val Protein = Color(0xFFE0614F)
    val Carbs = Color(0xFFC0801A)
    val Fat = Color(0xFF4E7FE0)
    val Fiber = Color(0xFF2E9E70)
}

// ── Skor gizi A–E ──────────────────────────────────────────────────────────
object GradeColors {
    val A = Color(0xFF16A34A)
    val B = Color(0xFF65A30D)
    val C = Color(0xFFCA8A04)
    val D = Color(0xFFEA7317)
    val E = Color(0xFFDC4A2F)
}

// ── Rekap mingguan (Wrapped): gradasi tetap per slide, teks selalu putih ──
object WrapColors {
    val Slides = listOf(
        listOf(Color(0xFF0B4A37), Color(0xFF13896A), Color(0xFF2CC598)),
        listOf(Color(0xFF1B1F6B), Color(0xFF3F3FC2), Color(0xFF7A6CF0)),
        listOf(Color(0xFF7A2E0B), Color(0xFFD25B17), Color(0xFFFF9E4F)),
        listOf(Color(0xFF6B0F3A), Color(0xFFC0306E), Color(0xFFFF7BA9)),
        listOf(Color(0xFF07343F), Color(0xFF0E7490), Color(0xFF22C3D6)),
    )
    /** Kartu ajakan di Beranda: perpaduan warna slide. */
    val Teaser = listOf(Color(0xFF3F3FC2), Color(0xFFC0306E), Color(0xFFFF9E4F))
}

/** Sorotan hangat di atas latar gelap/foto (lampu kilat aktif, hari "lebih" di rekap). */
val Highlight = Color(0xFFFFD27A)

// ── Waktu makan ────────────────────────────────────────────────────────────
object MealTypeColors {
    val Sarapan = Color(0xFFC0801A)
    val Siang = Color(0xFF2E9E70)
    val Malam = Color(0xFF4E7FE0)
    val Camilan = Color(0xFFE0614F)
}
