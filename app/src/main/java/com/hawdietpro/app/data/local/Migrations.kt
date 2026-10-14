package com.hawdietpro.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Daftar migrasi skema. Setiap kenaikan versi WAJIB punya migrasi di sini
 * (tidak memakai destructive migration) agar data pengguna tidak hilang.
 */
object Migrations {

    /** v2: gula & natrium, serta tanda favorit. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE meal_entry ADD COLUMN total_sugar REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE meal_entry ADD COLUMN total_sodium REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE meal_entry ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE food_element ADD COLUMN sugar REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE food_element ADD COLUMN sodium REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE food_reference ADD COLUMN sugar REAL")
            db.execSQL("ALTER TABLE food_reference ADD COLUMN sodium REAL")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
