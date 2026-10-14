package com.hawdietpro.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Penyimpanan terenkripsi (Android Keystore) untuk API key Gemini. */
@Singleton
class SecureStorage @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs: SharedPreferences by lazy { open() }

    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    init {
        _hasApiKey.value = !getApiKey().isNullOrBlank()
    }

    fun getApiKey(): String? = runCatching { prefs.getString(KEY_GEMINI, null) }.getOrNull()

    fun setApiKey(key: String?) {
        prefs.edit().apply {
            if (key.isNullOrBlank()) remove(KEY_GEMINI) else putString(KEY_GEMINI, key.trim())
        }.apply()
        _hasApiKey.value = !key.isNullOrBlank()
    }

    private fun open(): SharedPreferences = try {
        create()
    } catch (e: Exception) {
        // Keystore bisa rusak (mis. setelah restore); hapus file lama lalu buat ulang.
        Log.w(TAG, "EncryptedSharedPreferences rusak, dibuat ulang", e)
        context.deleteSharedPreferences(FILE)
        create()
    }

    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val TAG = "SecureStorage"
        const val FILE = "secure_prefs"
        const val KEY_GEMINI = "gemini_api_key"
    }
}
