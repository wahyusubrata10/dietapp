package com.hawdietpro.app.data.repository

import com.hawdietpro.app.data.local.dao.UserProfileDao
import com.hawdietpro.app.data.local.toDomain
import com.hawdietpro.app.data.local.toEntity
import com.hawdietpro.app.domain.model.UserProfile
import com.hawdietpro.app.domain.nutrition.DailyTarget
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val dao: UserProfileDao,
    private val weights: WeightRepository,
) {
    fun observeProfile(): Flow<UserProfile?> = dao.observe().map { it?.toDomain() }

    fun observeTarget(): Flow<DailyTarget?> = observeProfile().map { p -> p?.let(TdeeCalculator::calculate) }

    suspend fun getProfile(): UserProfile? = dao.get()?.toDomain()

    suspend fun getTarget(): DailyTarget? = getProfile()?.let(TdeeCalculator::calculate)

    /** Simpan profil; bila berat berubah, otomatis tercatat di log berat hari ini. */
    suspend fun save(profile: UserProfile) {
        val previous = dao.get()?.toDomain()
        dao.upsert(profile.toEntity(System.currentTimeMillis()))
        if (previous == null || previous.weightKg != profile.weightKg) {
            weights.log(LocalDate.now(), profile.weightKg, updateProfile = false)
        }
    }
}
