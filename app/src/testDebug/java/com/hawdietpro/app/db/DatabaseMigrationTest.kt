package com.hawdietpro.app.db

import android.app.Application
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hawdietpro.app.data.local.HawDatabase
import com.hawdietpro.app.data.local.Migrations
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrade database pengguna v5 → v6 (magnesium): semua catatan lama harus utuh dan skema hasil
 * migrasi harus persis sama dengan skema yang diharapkan Room (diperiksa oleh runMigrationsAndValidate).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HawDatabase::class.java)

    private fun seedV5() {
        helper.createDatabase(DB, 5).apply {
            execSQL(
                "INSERT INTO meal_entry (id, eaten_at, meal_type, menu_name, photo_path, confidence, total_calories, total_protein, " +
                    "total_carbs, total_fat, total_fiber, total_sugar, total_sodium, favorite, grade, assessments, ai_notes, suggestions, " +
                    "user_note, created_at, updated_at) VALUES (7, 1700000000000, 'MAKAN_SIANG', 'Nasi tempe bayam', '/foto/a.jpg', 0.8, " +
                    "520, 22, 70, 16, 6, 4, 900, 1, 'B', 'Seimbang', 'catatan AI', 'saran', 'enak', 1700000000000, 1700000000000)",
            )
            execSQL(
                "INSERT INTO food_element (id, meal_id, position, name, grams, calories, protein, carbs, fat, fiber, sugar, sodium, " +
                    "cooking_method, nutrition_source, food_ref_id, category, ai_calories) VALUES " +
                    "(1, 7, 0, 'Nasi putih', 150, 270, 4.5, 60, 0.5, 0.3, 0.1, 2, NULL, 'DB', 'nasi_putih', 'karbo', 260), " +
                    "(2, 7, 1, 'Tempe goreng', 50, 112, 10, 5, 7, 0.7, 0.5, 180, 'goreng', 'AI', NULL, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO food_reference (id, name, normalized_name, aliases, category, calories, protein, carbs, fat, fiber, sugar, sodium, " +
                    "default_serving_g, serving_label, source, data_version) VALUES " +
                    "('user_x', 'Kue buatan', 'kue buatan', '', 'camilan', 400, 6, 50, 20, 1, 25, 200, 50, '1 porsi', 'USER', 0)",
            )
            execSQL(
                "INSERT INTO barcode_product (barcode, name, brand, calories, protein, carbs, fat, fiber, sugar, sodium, serving_g, package_g, " +
                    "source, complete, updated_at) VALUES ('899', 'Teh botol', 'Merek', 40, 0, 10, 0, 0, 9, 10, 350, 350, 'MANUAL', 1, 1)",
            )
            close()
        }
    }

    @Test
    fun `v5 ke v6 mempertahankan data dan menambah kolom magnesium`() {
        seedV5()
        val db = helper.runMigrationsAndValidate(DB, 6, true, Migrations.MIGRATION_5_6)

        db.query("SELECT menu_name, total_calories, total_sodium, favorite, user_note, total_magnesium FROM meal_entry WHERE id = 7").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Nasi tempe bayam", c.getString(0))
            assertEquals(520.0, c.getDouble(1), 0.0)
            assertEquals(900.0, c.getDouble(2), 0.0)
            assertEquals(1, c.getInt(3))
            assertEquals("enak", c.getString(4))
            assertEquals(0.0, c.getDouble(5), 0.0)
        }
        db.query("SELECT name, grams, calories, food_ref_id, magnesium FROM food_element ORDER BY position").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals("Nasi putih", c.getString(0))
            assertEquals(150.0, c.getDouble(1), 0.0)
            assertEquals("nasi_putih", c.getString(3))
            assertEquals(0.0, c.getDouble(4), 0.0)
        }
        db.query("SELECT name, magnesium FROM food_reference WHERE id = 'user_x'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Kue buatan", c.getString(0))
            assertTrue("magnesium makanan lama belum diketahui (NULL)", c.isNull(1))
        }
        db.query("SELECT name, magnesium FROM barcode_product WHERE barcode = '899'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0.0, c.getDouble(1), 0.0)
        }
        db.close()
    }

    @Test
    fun `Room membuka database hasil migrasi lalu pengisian magnesium memperbarui total`() = runBlocking {
        seedV5()
        helper.runMigrationsAndValidate(DB, 6, true, Migrations.MIGRATION_5_6).close()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val room = Room.databaseBuilder(context, HawDatabase::class.java, DB)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = room.mealDao()
            val pending = dao.getElementsWithoutMagnesium()
            assertEquals(2, pending.size)
            dao.applyMagnesium(mapOf(pending[0].id to 19.2, pending[1].id to 42.5), pending.map { it.mealId })
            val meal = dao.getMeal(7)!!
            assertEquals(61.7, meal.meal.totalMagnesium, 1e-6)
            assertEquals(520.0, meal.meal.totalCalories, 0.0)
            assertEquals(listOf(19.2, 42.5), meal.elements.sortedBy { it.position }.map { it.magnesium })
            assertTrue(dao.getElementsWithoutMagnesium().isEmpty())
            assertNull(room.foodReferenceDao().getById("user_x")!!.magnesium)
        } finally {
            room.close()
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
