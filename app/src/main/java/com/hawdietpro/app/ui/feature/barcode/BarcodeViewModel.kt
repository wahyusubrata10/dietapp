package com.hawdietpro.app.ui.feature.barcode

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.core.ImageProcessor
import com.hawdietpro.app.data.gemini.GeminiError
import com.hawdietpro.app.data.repository.BarcodeRepository
import com.hawdietpro.app.data.repository.LookupResult
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.product.PackagedProduct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import javax.inject.Inject
import kotlin.math.roundToInt

sealed interface ScanPhase {
    data object Scanning : ScanPhase
    data class Looking(val barcode: String) : ScanPhase
    data class Found(val product: PackagedProduct, val cached: Boolean) : ScanPhase
    /** [partial] = produk dikenal tapi gizinya kosong. */
    data class NotFound(val barcode: String, val partial: PackagedProduct?, val message: String) : ScanPhase
    data class CapturingLabel(val barcode: String, val partial: PackagedProduct?) : ScanPhase
    data class ReadingLabel(val barcode: String) : ScanPhase
    data class Manual(val barcode: String, val partial: PackagedProduct?) : ScanPhase
}

data class BarcodeUi(
    val phase: ScanPhase = ScanPhase.Scanning,
    val grams: String = "",
    val mealType: MealType = MealType.fromHour(LocalTime.now().hour),
    val saving: Boolean = false,
    val labelError: String? = null,
)

/** Isian manual dari label (angka per takaran saji). */
data class ManualLabel(
    val name: String = "",
    val servingG: String = "",
    val packageG: String = "",
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val sugar: String = "",
    val sodium: String = "",
)

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    private val repo: BarcodeRepository,
    private val images: ImageProcessor,
) : ViewModel() {

    private val _state = MutableStateFlow(BarcodeUi())
    val state: StateFlow<BarcodeUi> = _state.asStateFlow()

    val recent: StateFlow<List<PackagedProduct>> = repo.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onDetected(code: String) {
        if (_state.value.phase != ScanPhase.Scanning || !PackagedProduct.isValidBarcode(code)) return
        lookup(code)
    }

    fun lookup(code: String) {
        val barcode = code.trim()
        if (!PackagedProduct.isValidBarcode(barcode)) return
        _state.update { it.copy(phase = ScanPhase.Looking(barcode), labelError = null) }
        viewModelScope.launch {
            val phase = when (val r = repo.lookup(barcode)) {
                is LookupResult.Found -> return@launch showFound(r.product, r.cached)
                is LookupResult.Incomplete -> ScanPhase.NotFound(
                    barcode, r.product,
                    "\"${r.product.displayName}\" dikenal, tapi data gizinya belum lengkap di Open Food Facts.",
                )
                LookupResult.NotFound -> ScanPhase.NotFound(barcode, null, "Produk ini belum ada di database Open Food Facts.")
                is LookupResult.Error -> ScanPhase.NotFound(barcode, null, r.message)
            }
            _state.update { it.copy(phase = phase) }
        }
    }

    fun open(product: PackagedProduct) = showFound(product, cached = true)

    private fun showFound(product: PackagedProduct, cached: Boolean) = _state.update {
        it.copy(phase = ScanPhase.Found(product, cached), grams = defaultGrams(product).roundToInt().toString(), labelError = null)
    }

    /** Kemasan kecil (≤ 300 g/ml) biasanya dihabiskan sekaligus; kemasan besar pakai takaran saji. */
    private fun defaultGrams(p: PackagedProduct): Double =
        p.packageG?.takeIf { it <= 300 } ?: p.servingG ?: p.packageG ?: 100.0

    fun setGrams(value: String) = _state.update { it.copy(grams = value.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6)) }
    fun setMealType(type: MealType) = _state.update { it.copy(mealType = type) }
    fun rescan() = _state.update { BarcodeUi(mealType = it.mealType) }

    fun gramsValue(): Double? = _state.value.grams.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 5000 }

    fun save(onDone: (String) -> Unit) {
        val phase = _state.value.phase as? ScanPhase.Found ?: return
        val grams = gramsValue() ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repo.log(phase.product, grams, _state.value.mealType, System.currentTimeMillis())
            val kcal = phase.product.nutrientsFor(grams).calories
            onDone("${phase.product.displayName} tercatat: ${Format.kcal(kcal)}")
        }
    }

    // ── Foto label ──
    fun startLabelCapture() {
        val (code, partial) = currentCode() ?: return
        _state.update { it.copy(phase = ScanPhase.CapturingLabel(code, partial), labelError = null) }
    }

    fun cancelLabelCapture() {
        val (code, partial) = currentCode() ?: return
        _state.update { it.copy(phase = ScanPhase.NotFound(code, partial, "Foto label dibatalkan.")) }
    }

    fun onLabelPhoto(file: File) = readLabel { file }

    fun onLabelFromGallery(uri: Uri) = readLabel { images.importFromUri(uri, maxSize = 2048, quality = 92) }

    private fun readLabel(source: suspend () -> File) {
        val (code, partial) = currentCode() ?: return
        _state.update { it.copy(phase = ScanPhase.ReadingLabel(code), labelError = null) }
        viewModelScope.launch {
            try {
                val product = repo.readLabel(source(), code, partial?.name, partial?.brand)
                showFound(product, cached = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = when (e) {
                    is GeminiError, is BarcodeRepository.LabelException -> e.message
                    else -> "Gagal membaca label: ${e.message}"
                }
                _state.update { it.copy(phase = ScanPhase.NotFound(code, partial, msg ?: "Gagal membaca label"), labelError = msg) }
            }
        }
    }

    // ── Isi manual ──
    fun startManual() {
        val (code, partial) = currentCode() ?: return
        _state.update { it.copy(phase = ScanPhase.Manual(code, partial)) }
    }

    /** Menyimpan angka label (per takaran saji). Null bila isian belum valid. */
    fun saveManual(form: ManualLabel): Boolean {
        val (code, partial) = currentCode() ?: return false
        fun d(v: String) = v.replace(',', '.').toDoubleOrNull()
        val serving = d(form.servingG)?.takeIf { it > 0 } ?: return false
        val kcal = d(form.calories)?.takeIf { it >= 0 } ?: return false
        val perServing = Nutrients(
            calories = kcal,
            protein = d(form.protein) ?: 0.0,
            carbs = d(form.carbs) ?: 0.0,
            fat = d(form.fat) ?: 0.0,
            sugar = d(form.sugar) ?: 0.0,
            sodium = d(form.sodium) ?: 0.0,
        )
        val product = PackagedProduct(
            barcode = code,
            name = form.name.trim().ifBlank { partial?.name ?: "Produk $code" },
            brand = partial?.brand,
            per100 = PackagedProduct.per100FromServing(perServing, serving) ?: return false,
            servingG = serving,
            packageG = d(form.packageG)?.takeIf { it > 0 },
            source = PackagedProduct.SOURCE_MANUAL,
        )
        viewModelScope.launch {
            repo.saveProduct(product)
            showFound(product, cached = false)
        }
        return true
    }

    private fun currentCode(): Pair<String, PackagedProduct?>? = when (val p = _state.value.phase) {
        is ScanPhase.NotFound -> p.barcode to p.partial
        is ScanPhase.CapturingLabel -> p.barcode to p.partial
        is ScanPhase.Manual -> p.barcode to p.partial
        is ScanPhase.Found -> p.product.barcode to p.product
        is ScanPhase.ReadingLabel -> p.barcode to null
        is ScanPhase.Looking -> p.barcode to null
        ScanPhase.Scanning -> null
    }
}
