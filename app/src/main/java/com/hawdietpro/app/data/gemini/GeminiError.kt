package com.hawdietpro.app.data.gemini

/** Kesalahan analisa dengan pesan Bahasa Indonesia yang siap ditampilkan. */
sealed class GeminiError(message: String, val retryable: Boolean, val queueable: Boolean) : Exception(message) {
    class MissingApiKey : GeminiError(
        "API key Gemini belum diisi. Buka Profil → Pengaturan → API Key Gemini.", retryable = false, queueable = false,
    )
    class InvalidApiKey(detail: String) : GeminiError(
        "API key Gemini tidak valid atau tidak punya akses. Periksa kembali key di Pengaturan. ($detail)", retryable = false, queueable = false,
    )
    class RateLimited(val retryAfterSeconds: Long?) : GeminiError(
        "Batas pemakaian gratis Gemini sedang tercapai. " +
            (retryAfterSeconds?.let { "Coba lagi dalam ±$it detik, " } ?: "Coba lagi beberapa saat lagi, ") +
            "atau simpan ke antrian agar diproses otomatis.",
        retryable = true, queueable = true,
    )
    class Network : GeminiError(
        "Tidak ada koneksi internet. Foto bisa disimpan ke antrian dan dianalisa otomatis saat online.", retryable = true, queueable = true,
    )
    class Server(val code: Int) : GeminiError(
        "Server Gemini sedang sibuk (kode $code). Coba lagi atau simpan ke antrian.", retryable = true, queueable = true,
    )
    class ModelNotFound(model: String) : GeminiError(
        "Model \"$model\" tidak tersedia. Periksa nama model di Pengaturan.", retryable = false, queueable = false,
    )
    class Blocked(reason: String) : GeminiError(
        "Foto ditolak oleh filter keamanan Gemini ($reason). Coba foto lain.", retryable = false, queueable = false,
    )
    class NotFood : GeminiError(
        "Foto tidak terdeteksi sebagai makanan atau minuman. Coba foto ulang dengan makanan terlihat jelas.", retryable = false, queueable = false,
    )
    class BadResponse(detail: String) : GeminiError(
        "Jawaban AI tidak bisa dibaca ($detail). Coba lagi.", retryable = true, queueable = true,
    )
    class Timeout : GeminiError(
        "AI terlalu lama menjawab. Coba lagi, atau biarkan dianalisa di latar belakang.", retryable = true, queueable = true,
    )
    /** Model tidak menerima pengaturan thinking → dicoba ulang tanpa pengaturan itu. */
    class UnsupportedThinking : GeminiError("Pengaturan thinking tidak didukung model", retryable = true, queueable = true)
    class Other(detail: String) : GeminiError("Analisa gagal: $detail", retryable = true, queueable = true)

    /** Kode singkat untuk riwayat analisa (diagnosa). */
    val shortCode: String
        get() = when (this) {
            is MissingApiKey -> "tanpa API key"
            is InvalidApiKey -> "API key ditolak"
            is RateLimited -> "429 kuota" + (retryAfterSeconds?.let { " (tunggu ${it}s)" } ?: "")
            is Network -> "jaringan"
            is Server -> "$code sibuk"
            is ModelNotFound -> "404 model tidak ada"
            is Blocked -> "diblokir"
            is NotFood -> "bukan makanan"
            is BadResponse -> "jawaban rusak"
            is Timeout -> "timeout"
            is UnsupportedThinking -> "thinking tidak didukung"
            is Other -> message?.removePrefix("Analisa gagal: ")?.take(40) ?: "lainnya"
        }
}
