package com.hawdietpro.app.data.repository

import com.hawdietpro.app.data.local.dao.UserProfileDao
import com.hawdietpro.app.data.local.dao.WeightLogDao
import com.hawdietpro.app.data.local.entity.WeightLogEntity
import com.hawdietpro.app.data.local.toDomain
import com.hawdietpro.app.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeightRepository @Inject constructor(
    private val dao: WeightLogDao,
    private val profileDao: UserProfileDao,
) {
    fun observeAll(): Flow<List<WeightEntry>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<WeightEntry> = dao.getAll().map { it.toDomain() }

    /** Satu catatan per hari; mencatat ulang di hari yang sama menggantikan nilai lama. */
    suspend fun log(date: LocalDate, weightKg: Double, note: String? = null, updateProfile: Boolean = true) {
        dao.upsert(WeightLogEntity(epochDay = date.toEpochDay(), weightKg = weightKg, note = note, createdAt = System.currentTimeMillis()))
        if (updateProfile) {
            val latest = dao.latest()
            val profile = profileDao.get()
            if (profile != null && latest != null && latest.epochDay == date.toEpochDay()) {
                profileDao.upsert(profile.copy(weightKg = weightKg, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
