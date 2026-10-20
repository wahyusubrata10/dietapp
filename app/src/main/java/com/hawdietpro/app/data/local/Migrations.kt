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

    /** v4: catatan untuk AI disimpan di antrian analisa latar belakang. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pending_analysis ADD COLUMN hint TEXT")
        }
    }

    /** v5: cache produk kemasan dari scan barcode. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `barcode_product` (" +
                    "`barcode` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`brand` TEXT, " +
                    "`calories` REAL NOT NULL, " +
                    "`protein` REAL NOT NULL, " +
                    "`carbs` REAL NOT NULL, " +
                    "`fat` REAL NOT NULL, " +
                    "`fiber` REAL NOT NULL, " +
                    "`sugar` REAL NOT NULL, " +
                    "`sodium` REAL NOT NULL, " +
                    "`serving_g` REAL, " +
                    "`package_g` REAL, " +
                    "`source` TEXT NOT NULL, " +
                    "`complete` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`barcode`))",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
