package com.hawdietpro.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * Sebelum Room membuka database dengan versi skema yang lebih baru, salin file
 * database lama ke files/backups agar data bisa dipulihkan bila migrasi gagal.
 */
object PreMigrationBackup {
    private const val TAG = "PreMigrationBackup"
    private const val KEEP = 3

    fun runIfNeeded(context: Context) {
        val dbFile = context.getDatabasePath(HawDatabase.DATABASE_NAME)
        if (!dbFile.exists()) return
        val currentVersion = try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        } catch (e: Exception) {
            Log.w(TAG, "Tidak bisa membaca versi database", e)
            return
        }
        if (currentVersion <= 0 || currentVersion >= HawDatabase.DATABASE_VERSION) return

        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        listOf("", "-wal", "-shm").forEach { suffix ->
            val src = File(dbFile.path + suffix)
            if (src.exists()) src.copyTo(File(dir, "pre-migrate-v$currentVersion-$stamp.db$suffix"), overwrite = true)
        }
        dir.listFiles { f -> f.name.startsWith("pre-migrate-") && f.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP)
            ?.forEach { old ->
                listOf("", "-wal", "-shm").forEach { File(old.path + it).delete() }
            }
        Log.i(TAG, "Backup sebelum migrasi v$currentVersion → v${HawDatabase.DATABASE_VERSION} dibuat")
    }
}
