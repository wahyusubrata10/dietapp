package com.hawdietpro.app.ui.feature.meal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.core.NetworkMonitor
import com.hawdietpro.app.data.gemini.GeminiError
import com.hawdietpro.app.data.remote.RemoteConfigStore
import com.hawdietpro.app.data.repository.FoodRepository
import com.hawdietpro.app.data.repository.MealAnalyzer
import com.hawdietpro.app.data.repository.MealEvaluator
import com.hawdietpro.app.data.repository.MealRepository
import com.hawdietpro.app.data.repository.PendingRepository
import com.hawdietpro.app.data.repository.ProfileRepository
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.FoodReference
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.NutritionSource
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.nutrition.NutritionCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

enum class EditorMode { PHOTO, EDIT, MANUAL }

sealed interface EditorPhase {
    data object Loading : EditorPhase
    data object Analyzing : EditorPhase
    data class Error(val message: String, val retryable: Boolean, val queueable: Boolean) : EditorPhase
    data object Ready : EditorPhase
    data object Saving : EditorPhase
}

data class EditorState(
    val mode: EditorMode = EditorMode.MANUAL,
    val phase: EditorPhase = EditorPhase.Loading,
    val meal: Meal = Meal(eatenAt = System.currentTimeMillis(), mealType = MealType.CAMILAN, menuName = "", photoPath = null, confidence = null, items = emptyList()),
    val hint: String = "",
    val targetCalories: Int? = null,
    val searchResults: List<FoodReference> = emptyList(),
    /** Pesan selesai (lalu layar ditutup). */
    val finishedMessage: String? = null,
    val dirty: Boolean = false,
    /** Pengali porsi seluruh piring (1 = sesuai foto/catatan). */
    val portion: Double = 1.0,
)

@HiltViewModel
class MealEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val analyzer: MealAnalyzer,
    private val evaluator: MealEvaluator,
    private val meals: MealRepository,
    private val foods: FoodRepository,
    private val profiles: ProfileRepository,
    private val pending: PendingRepository,
    private val network: NetworkMonitor,
    store: RemoteConfigStore,
) : ViewModel() {

    private val mealId: Long = savedState.get<String>("id")?.toLongOrNull() ?: 0L
    private val photoArg: String? = savedState.get<String>("photo")?.takeIf { it.isNotBlank() }
    private val typeArg: MealType? = savedState.get<String>("type")?.takeIf { it.isNotBlank() }?.let(MealType::parse)
    private val timeArg: Long? = savedState.get<String>("time")?.toLongOrNull()
    private val hintArg: String = savedState.get<String>("hint").orEmpty()
    private val pendingId: Long? = savedState.get<String>("pending")?.toLongOrNull()

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    val aiEnabled = store.appConfig.value.isEnabled("ai_analysis")

    /** true setelah foto disimpan/diantrikan, agar foto tidak ikut dihapus saat layar ditutup. */
    private var committed = false

    init {
        viewModelScope.launch {
            val target = profiles.getTarget()?.calories
            foods.seedIfNeeded()
            when {
                mealId != 0L -> {
                    val meal = meals.getMeal(mealId)
                    if (meal == null) {
                        _state.update { it.copy(finishedMessage = "Catatan tidak ditemukan") }
                    } else {
                        _state.update { it.copy(mode = EditorMode.EDIT, meal = meal, phase = EditorPhase.Ready, targetCalories = target) }
                    }
                }
                photoArg != null -> {
                    val time = timeArg ?: System.currentTimeMillis()
                    val type = typeArg ?: MealType.fromHour(hourOf(time))
                    _state.update {
                        it.copy(
                            mode = EditorMode.PHOTO,
                            meal = it.meal.copy(eatenAt = time, mealType = type, photoPath = photoArg, menuName = ""),
                            hint = hintArg,
                            targetCalories = target,
                        )
                    }
                    if (pendingId != null) {
                        // Dibuka dari antrian untuk diisi manual.
                        _state.update { it.copy(phase = EditorPhase.Ready, meal = it.meal.copy(menuName = "Makanan")) }
                    } else {
                        analyze()
                    }
                }
                else -> {
                    val time = timeArg ?: System.currentTimeMillis()
                    _state.update {
                        it.copy(
                            mode = EditorMode.MANUAL,
                            phase = EditorPhase.Ready,
                            meal = it.meal.copy(eatenAt = time, mealType = typeArg ?: MealType.fromHour(hourOf(time)), menuName = ""),
                            targetCalories = target,
                        )
                    }
                }
            }
        }
    }

    private fun hourOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).hour

    private var analysisJob: kotlinx.coroutines.Job? = null

    fun analyze() {
        val s = _state.value
        val photo = s.meal.photoPath ?: return
        _state.update { it.copy(phase = EditorPhase.Analyzing) }
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            try {
                val result = analyzer.analyze(photo, s.meal.mealType, s.meal.eatenAt, s.hint)
                _state.update { it.copy(meal = result.copy(id = it.meal.id, userNote = it.meal.userNote), phase = EditorPhase.Ready, dirty = true, portion = 1.0) }
            } catch (e: GeminiError) {
                _state.update { it.copy(phase = EditorPhase.Error(e.message ?: "Analisa gagal", e.retryable, e.queueable && pendingId == null)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val offline = !network.isOnline()
                _state.update {
                    it.copy(
                        phase = EditorPhase.Error(
                            if (offline) GeminiError.Network().message!! else "Analisa gagal: ${e.message ?: e.javaClass.simpleName}",
                            retryable = true,
                            queueable = pendingId == null,
                        ),
                    )
                }
            }
        }
    }

    fun setHint(value: String) = _state.update { it.copy(hint = value) }

    /** Hentikan penantian: foto dianalisa di background, hasil tercatat otomatis + notifikasi. */
    fun continueInBackground() {
        analysisJob?.cancel()
        val m = _state.value.meal
        val photo = m.photoPath ?: return
        viewModelScope.launch {
            pending.enqueue(photo, m.mealType, m.eatenAt, _state.value.hint)
            committed = true
            _state.update { it.copy(finishedMessage = "Dianalisa di background. Anda akan dapat notifikasi saat selesai.") }
        }
    }

    /** Simpan foto ke antrian offline, diproses otomatis saat online. */
    fun queueForLater() {
        val m = _state.value.meal
        val photo = m.photoPath ?: return
        viewModelScope.launch {
            pending.enqueue(photo, m.mealType, m.eatenAt)
            committed = true
            _state.update { it.copy(finishedMessage = "Foto masuk antrian. Akan dianalisa otomatis saat online.") }
        }
    }

    /** Lanjut isi manual (foto tetap disimpan) bila AI gagal. */
    fun continueManually() = _state.update {
        it.copy(phase = EditorPhase.Ready, mode = EditorMode.MANUAL, meal = it.meal.copy(menuName = it.meal.menuName.ifBlank { "Makanan" }))
    }

    private fun mutateMeal(block: (Meal) -> Meal) = _state.update { s ->
        val updated = block(s.meal)
        s.copy(meal = evaluator.evaluate(updated, s.targetCalories), dirty = true)
    }

    fun setMenuName(name: String) = _state.update { it.copy(meal = it.meal.copy(menuName = name), dirty = true) }
    fun setNote(note: String) = _state.update { it.copy(meal = it.meal.copy(userNote = note.ifBlank { null }), dirty = true) }
    fun setMealType(type: MealType) = mutateMeal { it.copy(mealType = type) }

    fun setDate(date: LocalDate) = mutateMeal { m ->
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(m.eatenAt).atZone(zone).toLocalTime()
        m.copy(eatenAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli())
    }

    fun setTime(time: LocalTime) = mutateMeal { m ->
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(m.eatenAt).atZone(zone).toLocalDate()
        m.copy(eatenAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli())
    }

    /** Ubah gram → kalori & makro dihitung ulang otomatis. */
    fun setGrams(index: Int, grams: Double) {
        viewModelScope.launch {
            val item = _state.value.meal.items.getOrNull(index) ?: return@launch
            val ref = item.foodRefId?.let { foods.getById(it) }
            val updated = NutritionCalculator.rescale(item, grams, ref)
            mutateMeal { m -> m.copy(items = m.items.toMutableList().also { it[index] = updated }) }
        }
    }

    /** Ganti nama; bila cocok dengan DB lokal, angka gizi ikut memakai DB. */
    fun rename(index: Int, name: String) {
        viewModelScope.launch {
            val item = _state.value.meal.items.getOrNull(index) ?: return@launch
            val renamed = item.copy(name = name.trim())
            val match = foods.matcher().match(renamed.name, renamed.cookingMethod)
            val updated = when {
                match != null -> NutritionCalculator.applyReference(renamed, match.reference)
                item.source == NutritionSource.DB -> renamed.copy(source = NutritionSource.MANUAL, foodRefId = null)
                else -> renamed
            }
            mutateMeal { m -> m.copy(items = m.items.toMutableList().also { it[index] = updated }) }
        }
    }

    /**
     * Mengalikan seluruh elemen piring, mis. "hanya makan setengah". Relatif terhadap porsi
     * sebelumnya, sehingga bisa dipilih bolak-balik tanpa kehilangan angka asli.
     */
    fun setPortion(factor: Double) {
        val current = _state.value.portion
        if (factor <= 0 || factor == current) return
        val ratio = factor / current
        viewModelScope.launch {
            val items = _state.value.meal.items.map { item ->
                val ref = if (item.source == NutritionSource.DB) item.foodRefId?.let { foods.getById(it) } else null
                NutritionCalculator.rescale(item, item.grams * ratio, ref)
            }
            mutateMeal { it.copy(items = items) }
            _state.update { it.copy(portion = factor) }
        }
    }

    /** Simpan perubahan dari lembar edit elemen: nama & gram sekaligus (berurutan, tanpa race). */
    fun updateItem(index: Int, name: String, grams: Double) {
        viewModelScope.launch {
            var item = _state.value.meal.items.getOrNull(index) ?: return@launch
            val newName = name.trim().ifBlank { item.name }
            if (newName != item.name) {
                val renamed = item.copy(name = newName)
                val match = foods.matcher().match(renamed.name, renamed.cookingMethod)
                item = when {
                    match != null -> NutritionCalculator.applyReference(renamed, match.reference)
                    item.source == NutritionSource.DB -> renamed.copy(source = NutritionSource.MANUAL, foodRefId = null)
                    else -> renamed
                }
            }
            val ref = if (item.source == NutritionSource.DB) item.foodRefId?.let { foods.getById(it) } else null
            val updated = NutritionCalculator.rescale(item, grams, ref)
            mutateMeal { m -> m.copy(items = m.items.toMutableList().also { if (index < it.size) it[index] = updated }) }
        }
    }

    /** Hubungkan elemen ke referensi DB tertentu (gram tetap). */
    fun linkReference(index: Int, ref: FoodReference) = mutateMeal { m ->
        val item = m.items.getOrNull(index) ?: return@mutateMeal m
        m.copy(items = m.items.toMutableList().also { it[index] = NutritionCalculator.applyReference(item.copy(name = ref.name), ref) })
    }

    fun removeItem(index: Int) = mutateMeal { m -> m.copy(items = m.items.filterIndexed { i, _ -> i != index }) }

    fun addReference(ref: FoodReference, grams: Double = ref.defaultServingG) = mutateMeal { m ->
        val item = FoodItem(
            name = ref.name,
            grams = grams,
            nutrients = NutritionCalculator.fromReference(ref, grams),
            source = NutritionSource.DB,
            foodRefId = ref.id,
            category = ref.category,
        )
        val name = if (m.menuName.isBlank()) ref.name else m.menuName
        m.copy(items = m.items + item, menuName = name)
    }

    fun addCustom(name: String, grams: Double, nutrients: Nutrients, saveToDb: Boolean) {
        viewModelScope.launch {
            var item = FoodItem(name = name.trim(), grams = grams, nutrients = nutrients, source = NutritionSource.MANUAL)
            if (saveToDb && grams > 0) {
                val ref = foods.addUserFood(name, nutrients * (100.0 / grams), grams)
                item = item.copy(foodRefId = ref.id)
            }
            mutateMeal { m -> m.copy(items = m.items + item, menuName = m.menuName.ifBlank { name.trim() }) }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            val results = if (query.isBlank()) emptyList() else foods.search(query)
            _state.update { it.copy(searchResults = results) }
        }
    }

    suspend fun referenceFor(item: FoodItem): FoodReference? = item.foodRefId?.let { foods.getById(it) }

    fun save() {
        val s = _state.value
        if (s.meal.items.isEmpty()) return
        _state.update { it.copy(phase = EditorPhase.Saving) }
        viewModelScope.launch {
            val meal = evaluator.evaluate(s.meal.copy(menuName = s.meal.menuName.ifBlank { s.meal.items.first().name }))
            meals.save(meal)
            committed = true
            pendingId?.let { pending.remove(it, deletePhoto = false) }
            _state.update { it.copy(finishedMessage = if (s.mode == EditorMode.EDIT) "Perubahan disimpan" else "Tersimpan: ${meal.menuName}") }
        }
    }

    /** Tandai/lepas favorit. Untuk catatan tersimpan langsung diperbarui di database. */
    fun toggleFavorite() {
        val newValue = !_state.value.meal.favorite
        _state.update { it.copy(meal = it.meal.copy(favorite = newValue)) }
        val id = _state.value.meal.id
        if (id != 0L) viewModelScope.launch { meals.setFavorite(id, newValue) }
    }

    /** Catat ulang makan ini sekarang (mis. menu yang sama untuk makan siang hari ini). */
    fun eatAgainNow() {
        val meal = _state.value.meal
        if (meal.id == 0L || meal.items.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val type = MealType.fromHour(hourOf(now))
            meals.duplicate(meal, now, type)
            _state.update { it.copy(finishedMessage = "Dicatat lagi: ${meal.menuName} (${type.label})") }
        }
    }

    fun delete() {
        val id = _state.value.meal.id
        if (id == 0L) return
        viewModelScope.launch {
            meals.delete(id)
            _state.update { it.copy(finishedMessage = "Catatan dihapus") }
        }
    }

    override fun onCleared() {
        // Foto baru yang batal disimpan tidak perlu memenuhi penyimpanan.
        if (!committed && mealId == 0L && pendingId == null) {
            _state.value.meal.photoPath?.let { java.io.File(it).delete() }
        }
    }
}
