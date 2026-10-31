package com.hawdietpro.app.ui.feature.camera

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.core.ImageProcessor
import com.hawdietpro.app.data.remote.RemoteConfigStore
import com.hawdietpro.app.domain.model.MealType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import javax.inject.Inject

data class CameraState(
    val photoPath: String? = null,
    val processing: Boolean = false,
    val mealType: MealType = MealType.fromHour(LocalTime.now().hour),
    val capturedAt: Long = System.currentTimeMillis(),
    val hint: String = "",
    val error: String? = null,
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val images: ImageProcessor,
    private val store: RemoteConfigStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        CameraState(
            mealType = savedState.get<String>(KEY_MEAL_TYPE)?.let(MealType::parse)
                ?: savedState.get<String>("type")?.takeIf { it.isNotBlank() }?.let(MealType::parse)
                ?: MealType.fromHour(LocalTime.now().hour),
            // Bila proses sempat dimatikan sistem setelah memotret, foto yang sudah jadi dipakai lagi.
            photoPath = savedState.get<String>(KEY_PHOTO)?.takeIf { File(it).exists() },
            capturedAt = savedState.get<Long>(KEY_CAPTURED_AT) ?: System.currentTimeMillis(),
            hint = savedState.get<String>(KEY_HINT).orEmpty(),
        ),
    )
    val state: StateFlow<CameraState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.collect {
                savedState[KEY_PHOTO] = it.photoPath
                savedState[KEY_CAPTURED_AT] = it.capturedAt
                savedState[KEY_MEAL_TYPE] = it.mealType.name
                savedState[KEY_HINT] = it.hint
            }
        }
    }

    private var handedOff = false

    /** Dibuka dari "Pilih dari galeri": langsung tampilkan pemilih foto sekali. */
    var openGalleryOnStart: Boolean = savedState.get<String>("gallery") == "1"
        private set

    fun consumeOpenGallery() {
        openGalleryOnStart = false
    }

    val captureDir: File get() = File(images.photoDir.parentFile, "capture").apply { mkdirs() }

    fun onCaptured(raw: File) = process { images.importFromFile(raw, maxSize(), quality()) }

    fun onPicked(uri: Uri) = process { images.importFromUri(uri, maxSize(), quality()) }

    private fun maxSize() = store.geminiPrompt.value.maxImageSize
    private fun quality() = store.geminiPrompt.value.jpegQuality

    private fun process(block: suspend () -> File) {
        _state.update { it.copy(processing = true, error = null) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { file ->
                    discardPhoto()
                    _state.update { it.copy(photoPath = file.path, processing = false, capturedAt = System.currentTimeMillis()) }
                }
                .onFailure { e -> _state.update { it.copy(processing = false, error = "Foto gagal diproses: ${e.message}") } }
        }
    }

    fun setMealType(type: MealType) = _state.update { it.copy(mealType = type) }
    fun setHint(hint: String) = _state.update { it.copy(hint = hint) }
    fun showError(message: String) = _state.update { it.copy(error = message) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun retake() {
        discardPhoto()
        _state.update { it.copy(photoPath = null) }
    }

    fun handOff() {
        handedOff = true
    }

    private fun discardPhoto() {
        _state.value.photoPath?.let { File(it).delete() }
    }

    override fun onCleared() {
        if (!handedOff) discardPhoto()
    }

    private companion object {
        const val KEY_PHOTO = "cam_photo"
        const val KEY_CAPTURED_AT = "cam_captured_at"
        const val KEY_MEAL_TYPE = "cam_meal_type"
        const val KEY_HINT = "cam_hint"
    }
}
