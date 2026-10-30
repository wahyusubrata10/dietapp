package com.hawdietpro.app.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test

class DayStatusTest {
    @Test
    fun `tanpa catatan kosong`() {
        assertEquals(DayStatus.KOSONG, DayStatus.of(null, 2000))
        assertEquals(DayStatus.KOSONG, DayStatus.of(0.0, 2000))
    }

    @Test
    fun `batas toleransi sepuluh persen`() {
        assertEquals(DayStatus.SESUAI, DayStatus.of(2200.0, 2000))
        assertEquals(DayStatus.LEBIH, DayStatus.of(2201.0, 2000))
        assertEquals(DayStatus.SESUAI, DayStatus.of(1800.0, 2000))
        assertEquals(DayStatus.KURANG, DayStatus.of(1799.0, 2000))
    }

    @Test
    fun `tanpa target dianggap sesuai bila ada catatan`() {
        assertEquals(DayStatus.SESUAI, DayStatus.of(1500.0, null))
    }
}
