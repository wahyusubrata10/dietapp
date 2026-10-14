package com.hawdietpro.app.ui.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.data.prefs.SecureStorage
import com.hawdietpro.app.data.repository.ProfileRepository
import com.hawdietpro.app.domain.model.ActivityLevel
import com.hawdietpro.app.domain.model.Goal
import com.hawdietpro.app.domain.model.Sex
import com.hawdietpro.app.domain.model.UserProfile
import com.hawdietpro.app.domain.nutrition.DailyTarget
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileForm(
    val name: String = "",
    val sex: Sex = Sex.PRIA,
    val age: String = "",
    val height: String = "",
    val weight: String = "",
    val targetWeight: String = "",
    val activity: ActivityLevel = ActivityLevel.RINGAN,
    val goal: Goal = Goal.TURUN,
    val customCalories: String = "",
    val apiKey: String = "",
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    private fun num(s: String) = s.replace(',', '.').trim().toDoubleOrNull()

    /** Null = valid (atau kosong). Kosong ditangani oleh pengecekan wajib isi. */
    private fun rangeError(raw: String, value: Double?, range: ClosedFloatingPointRange<Double>, message: String): String? = when {
        raw.isBlank() -> null
        value == null -> "Angka tidak valid"
        value !in range -> message
        else -> null
    }

    val ageValue get() = age.trim().toIntOrNull()
    val heightValue get() = num(height)
    val weightValue get() = num(weight)
    val targetWeightValue get() = num(targetWeight)
    val customValue get() = customCalories.trim().toIntOrNull()

    val ageError get() = rangeError(age, ageValue?.toDouble(), 13.0..100.0, "Umur 13–100 tahun")
    val heightError get() = rangeError(height, heightValue, 100.0..250.0, "Tinggi 100–250 cm")
    val weightError get() = rangeError(weight, weightValue, 25.0..300.0, "Berat 25–300 kg")
    val targetWeightError get() = rangeError(targetWeight, targetWeightValue, 25.0..300.0, "Berat 25–300 kg")

    val personalValid get() = name.isNotBlank() && ageValue != null && ageError == null
    val bodyValid get() = heightValue != null && heightError == null && weightValue != null && weightError == null && targetWeightError == null
    val valid get() = personalValid && bodyValid

    fun toProfile(): UserProfile? = if (!valid) null else UserProfile(
        name = name.trim(),
        age = ageValue!!,
        sex = sex,
        heightCm = heightValue!!,
        weightKg = weightValue!!,
        activityLevel = activity,
        goal = goal,
        targetWeightKg = targetWeightValue,
        customCalories = customValue,
    )

    val preview: DailyTarget? get() = toProfile()?.let(TdeeCalculator::calculate)
}

/** Dipakai bersama oleh Onboarding & Edit Profil. */
@HiltViewModel
class ProfileFormViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val secure: SecureStorage,
) : ViewModel() {

    private val _form = MutableStateFlow(ProfileForm())
    val form: StateFlow<ProfileForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val p = profiles.getProfile()
            _form.update { f ->
                if (p == null) f.copy(loaded = true) else f.copy(
                    name = p.name,
                    sex = p.sex,
                    age = p.age.toString(),
                    height = trim(p.heightCm),
                    weight = trim(p.weightKg),
                    targetWeight = p.targetWeightKg?.let(::trim).orEmpty(),
                    activity = p.activityLevel,
                    goal = p.goal,
                    customCalories = p.customCalories?.toString().orEmpty(),
                    loaded = true,
                )
            }
        }
    }

    private fun trim(v: Double) = if (v == v.toInt().toDouble()) v.toInt().toString() else v.toString()

    fun update(block: (ProfileForm) -> ProfileForm) = _form.update(block)

    fun save() {
        val profile = _form.value.toProfile() ?: return
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val key = _form.value.apiKey
            if (key.isNotBlank()) secure.setApiKey(key)
            profiles.save(profile)
            _form.update { it.copy(saving = false, saved = true) }
        }
    }
}
