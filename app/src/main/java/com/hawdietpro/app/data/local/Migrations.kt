package com.hawdietpro.app.data.local

import androidx.room.migration.Migration

/**
 * Daftar migrasi skema. Contoh saat menaikkan versi ke 2:
 *
 * ```
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE meal_entry ADD COLUMN mood TEXT")
 *     }
 * }
 * ```
 * lalu tambahkan ke [ALL].
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
