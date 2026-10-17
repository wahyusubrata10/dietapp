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

    /** v3: catatan olahraga (kalori terbakar). */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `exercise_log` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`epoch_day` INTEGER NOT NULL, " +
                    "`performed_at` INTEGER NOT NULL, " +
                    "`activity_id` TEXT, " +
                    "`name` TEXT NOT NULL, " +
                    "`duration_min` INTEGER NOT NULL, " +
                    "`calories` REAL NOT NULL, " +
                    "`source` TEXT NOT NULL, " +
                    "`note` TEXT, " +
                    "`created_at` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_log_epoch_day` ON `exercise_log` (`epoch_day`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
