package com.hawdietpro.app.domain.config

/**
 * Menyusun urutan model Gemini yang dicoba: override pengguna → model utama → cadangan dari remote config.
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
        max: Int = 4,
    ): List<String> {
        fun liteOk(m: String) = allowLite || !m.contains("lite", ignoreCase = true)
        val preferred = (listOf(primary) + fallbacks).filter(::liteOk)
        val chosen = if (available == null) {
            preferred
        } else {
            // Alias "-latest" tidak selalu muncul di daftar model, jadi tetap dipertahankan.
            val alive = preferred.filter { it in available || it.endsWith("-latest") }
            val auto = available
                .mapNotNull { id -> AUTO.matchEntire(id)?.let { m -> Triple(id, m.groupValues[1].toDouble(), m.groupValues[2].isNotEmpty()) } }
                .filter { liteOk(it.first) }
                .sortedWith(compareBy<Triple<String, Double, Boolean>> { it.third }.thenByDescending { it.second })
                .map { it.first }
            alive + auto
        }
        return (listOfNotNull(override?.takeIf { it.isNotBlank() }) + chosen).distinct().take(max)
            .ifEmpty { listOf(primary) }
    }
}
