package com.hawdietpro.app.domain.config

/**
 * Menyusun urutan model Gemini yang dicoba: override pengguna → model utama → cadangan dari remote config
 * (mode Teliti: model "lite" hanya di akhir sebagai cadangan terakhir).
 * Bila daftar model aktif dari API ([available]) diketahui, model yang sudah dimatikan Google dibuang dan
 * model Flash stabil terbaru yang tersedia ditambahkan otomatis, sehingga aplikasi tetap jalan walau
 * Google mematikan model lama.
 */
object ModelChain {

    private val AUTO = Regex("""^gemini-(\d+(?:\.\d+)?)-flash(-lite)?$""")

    fun build(
        override: String?,
        primary: String,
        fallbacks: List<String>,
        available: Set<String>?,
        allowLite: Boolean,
        max: Int = 5,
    ): List<String> {
        fun isLite(m: String) = m.contains("lite", ignoreCase = true)
        val preferred = listOf(primary) + fallbacks
        val chosen = if (available == null) {
            preferred
        } else {
            // Alias "-latest" tidak selalu muncul di daftar model, jadi tetap dipertahankan.
            val alive = preferred.filter { it in available || it.endsWith("-latest") }
            val auto = available
                .mapNotNull { id -> AUTO.matchEntire(id)?.let { m -> Triple(id, m.groupValues[1].toDouble(), m.groupValues[2].isNotEmpty()) } }
                .sortedWith(compareBy<Triple<String, Double, Boolean>> { it.third }.thenByDescending { it.second })
                .map { it.first }
            alive + auto
        }.distinct()
        // Mode Cepat: urutan apa adanya. Mode Teliti: model "lite" dipindah ke paling belakang sebagai cadangan
        // terakhir (mis. saat kuota Flash habis), maksimal 2.
        val ordered = if (allowLite) chosen.take(max) else {
            val (lite, full) = chosen.partition(::isLite)
            full.take(max - minOf(2, lite.size)) + lite.take(2)
        }
        return (listOfNotNull(override?.takeIf { it.isNotBlank() }) + ordered).distinct().take(max + 1)
            .ifEmpty { listOf(primary) }
    }
}
