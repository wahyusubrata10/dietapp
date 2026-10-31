package com.hawdietpro.app.core

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Cek keberadaan file foto dengan cache, agar daftar yang di-scroll tidak membaca penyimpanan di thread UI. */
object PhotoFiles {
    private val cache = ConcurrentHashMap<String, Boolean>()

    fun exists(path: String): Boolean = cache.getOrPut(path) { File(path).exists() }

    /** Dipanggil setelah foto dihapus agar cache tidak basi. */
    fun forget(path: String) {
        cache.remove(path)
    }
}
