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
        CameraState(mealType = savedState.get<String>("type")?.takeIf { it.isNotBlank() }?.let(MealType::parse) ?: MealType.fromHour(LocalTime.now().hour)),
    )
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private var handedOff = false

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
}
