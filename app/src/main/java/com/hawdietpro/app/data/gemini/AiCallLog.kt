package com.hawdietpro.app.data.gemini

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Satu percobaan ke satu model. [startMs] relatif terhadap awal permintaan. */
@Serializable
data class AiAttempt(val model: String, val startMs: Long, val durationMs: Long, val outcome: String) {
    val ok: Boolean get() = outcome == AiCallTrace.OK
}

/** Satu permintaan AI (analisa foto, saran menu, analisa mendalam) beserta semua percobaannya. */
@Serializable
data class AiCallRecord(
    val time: Long,
    val kind: String,
    val totalMs: Long,
    val success: Boolean,
    val error: String? = null,
    val attempts: List<AiAttempt> = emptyList(),
    val notes: List<String> = emptyList(),
)

/** Pengumpul data satu permintaan; aman dipakai dari beberapa coroutine (permintaan paralel). */
class AiCallTrace(private val kind: String) {
    private val start = System.currentTimeMillis()
    private val attempts = mutableListOf<AiAttempt>()
    private val notes = mutableListOf<String>()

    fun now(): Long = System.currentTimeMillis() - start

    @Synchronized fun attempt(model: String, startMs: Long, outcome: String) {
        attempts += AiAttempt(model, startMs, now() - startMs, outcome)
    }

    @Synchronized fun note(text: String) {
        notes += "${now() / 1000.0}s: $text"
    }

    @Synchronized fun build(success: Boolean, error: GeminiError?) = AiCallRecord(
        time = start,
        kind = kind,
        totalMs = now(),
        success = success,
        error = error?.shortCode,
        attempts = attempts.sortedBy { it.startMs },
        notes = notes.toList(),
    )

    companion object {
        const val OK = "OK"
        const val CANCELLED = "dihentikan (cadangan lebih cepat)"
    }
}

/** Riwayat permintaan AI terakhir, disimpan lokal untuk diagnosa (tanpa foto & tanpa isi jawaban). */
@Singleton
class AiCallLog @Inject constructor(@ApplicationContext context: Context, private val json: Json) {

    private val file = File(context.filesDir, "ai_call_log.json")
    private val serializer = ListSerializer(AiCallRecord.serializer())
    private val _records = MutableStateFlow(load())
    val records: StateFlow<List<AiCallRecord>> = _records.asStateFlow()

    private fun load(): List<AiCallRecord> =
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())

    @Synchronized fun add(record: AiCallRecord) {
        val updated = (listOf(record) + _records.value).take(MAX)
        _records.value = updated
        runCatching { file.writeText(json.encodeToString(serializer, updated)) }
    }

    @Synchronized fun clear() {
        _records.value = emptyList()
        file.delete()
    }

    private companion object {
        const val MAX = 60
    }
}
