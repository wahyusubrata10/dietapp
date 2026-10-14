package com.hawdietpro.app.ui

import com.hawdietpro.app.ui.feature.profile.ProfileForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFormTest {

    private val valid = ProfileForm(name = "Wahyu", age = "30", height = "170", weight = "75")

    @Test
    fun `angka valid tidak menghasilkan error`() {
        assertNull(valid.ageError)
        assertNull(valid.heightError)
        assertNull(valid.weightError)
        assertNull(valid.targetWeightError)
        assertTrue(valid.valid)
        assertNotNull(valid.preview)
    }

    @Test
    fun `desimal dengan koma diterima`() {
        val f = valid.copy(height = "170,5", weight = "72,3", targetWeight = "68,5")
        assertNull(f.heightError)
        assertNull(f.weightError)
        assertNull(f.targetWeightError)
        assertTrue(f.valid)
    }

    @Test
    fun `di luar rentang menampilkan pesan rentang`() {
        assertEquals("Umur 13–100 tahun", valid.copy(age = "5").ageError)
        assertEquals("Tinggi 100–250 cm", valid.copy(height = "20").heightError)
        assertFalse(valid.copy(age = "5").valid)
    }

    @Test
    fun `teks bukan angka tidak valid`() {
        assertEquals("Angka tidak valid", valid.copy(weight = "7.5.3").weightError)
    }

    @Test
    fun `kolom kosong tidak menampilkan error tapi form belum valid`() {
        val f = valid.copy(age = "")
        assertNull(f.ageError)
        assertFalse(f.valid)
        assertNull(valid.copy(targetWeight = "").targetWeightError)
    }
}
