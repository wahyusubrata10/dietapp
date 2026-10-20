package com.hawdietpro.app.ui.feature.barcode

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.product.PackagedProduct
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.HawScaffold
import com.hawdietpro.app.ui.components.MacroInline
import com.hawdietpro.app.ui.components.Pill
import com.hawdietpro.app.ui.components.SectionHeader
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Scan barcode kemasan → gizi dari Open Food Facts / label dibaca AI → catat sebagai makanan. */
@Composable
fun BarcodeScreen(
    onBack: () -> Unit,
    onDone: (String) -> Unit,
    viewModel: BarcodeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permission.launch(Manifest.permission.CAMERA) }
    val pickLabel = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onLabelFromGallery)
    }

    HawScaffold(title = "Scan barcode", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val phase = state.phase) {
                ScanPhase.Scanning -> ScanningContent(
                    hasPermission = hasPermission,
                    onGrant = { permission.launch(Manifest.permission.CAMERA) },
                    onDetected = viewModel::onDetected,
                    onSearch = viewModel::lookup,
                    recent = recent,
                    onOpenRecent = viewModel::open,
                )
                is ScanPhase.Looking -> Busy("Mencari ${phase.barcode}…", "Cek data tersimpan di HP, lalu Open Food Facts.")
                is ScanPhase.ReadingLabel -> Busy("AI sedang membaca label gizi…", "Biasanya beberapa detik.")
                is ScanPhase.Found -> FoundContent(
                    phase = phase,
                    state = state,
                    viewModel = viewModel,
                    onSave = { viewModel.save(onDone) },
                    onFixWithLabel = viewModel::startLabelCapture,
                )
                is ScanPhase.NotFound -> NotFoundContent(
                    phase = phase,
                    onPhotoLabel = viewModel::startLabelCapture,
                    onGallery = { pickLabel.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onManual = viewModel::startManual,
                    onRescan = viewModel::rescan,
                )
                is ScanPhase.CapturingLabel -> LabelCaptureContent(
                    hasPermission = hasPermission,
                    onGrant = { permission.launch(Manifest.permission.CAMERA) },
                    onCaptured = viewModel::onLabelPhoto,
                    onCancel = viewModel::cancelLabelCapture,
                    onGallery = { pickLabel.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
                is ScanPhase.Manual -> ManualContent(phase, viewModel)
            }
        }
    }
}

// ───────────────────────────── Kamera ─────────────────────────────

/** Kamera: mode scan (analisa barcode terus-menerus) atau mode foto label (tombol jepret). */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraBox(
    scanning: Boolean,
    onBarcode: (String) -> Unit = {},
    onCaptured: (File) -> Unit = {},
    onError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build(),
        )
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }
    DisposableEffect(scanning) {
        if (scanning) {
            controller.setImageAnalysisAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) {
                    proxy.close()
                    return@setImageAnalysisAnalyzer
                }
                scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { codes ->
                        codes.firstNotNullOfOrNull { c -> c.rawValue?.takeIf(PackagedProduct::isValidBarcode) }
                            ?.let { currentOnBarcode(it) }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
        } else {
            controller.clearImageAnalysisAnalyzer()
        }
        onDispose { controller.clearImageAnalysisAnalyzer() }
    }

    Box(
        Modifier.fillMaxWidth().aspectRatio(if (scanning) 4f / 3f else 3f / 4f).clip(RoundedCornerShape(24.dp)).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx -> PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; this.controller = controller } },
            modifier = Modifier.fillMaxSize(),
        )
        if (scanning) {
            // Bingkai bidik barcode
            Box(
                Modifier.fillMaxWidth(0.8f).aspectRatio(2.2f)
                    .border(BorderStroke(3.dp, Color.White.copy(alpha = 0.9f)), RoundedCornerShape(16.dp)),
            )
            Text(
                "Arahkan ke barcode kemasan",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        } else {
            FilledIconButton(
                onClick = {
                    val file = File(context.cacheDir, "label_${System.currentTimeMillis()}.jpg")
                    controller.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) = onCaptured(file)
                            override fun onError(exception: ImageCaptureException) = onError(exception.message ?: "Gagal mengambil foto")
                        },
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).size(68.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
            ) { Icon(Icons.Filled.CameraAlt, contentDescription = "Foto label", modifier = Modifier.size(30.dp)) }
        }
    }
}

@Composable
private fun NeedPermission(onGrant: () -> Unit) {
    HawCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Izin kamera diperlukan", style = MaterialTheme.typography.titleMedium)
            Text("Untuk memindai barcode. Anda juga bisa mengetik nomor barcode di bawah.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onGrant) { Text("Izinkan kamera") }
        }
    }
}

// ───────────────────────────── Tahap-tahap ─────────────────────────────

@Composable
private fun ScanningContent(
    hasPermission: Boolean,
    onGrant: () -> Unit,
    onDetected: (String) -> Unit,
    onSearch: (String) -> Unit,
    recent: List<PackagedProduct>,
    onOpenRecent: (PackagedProduct) -> Unit,
) {
    if (hasPermission) CameraBox(scanning = true, onBarcode = onDetected) else NeedPermission(onGrant)
    var manual by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = manual,
            onValueChange = { manual = it.filter(Char::isDigit).take(14) },
            label = { Text("Atau ketik nomor barcode") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = { onSearch(manual) }, enabled = PackagedProduct.isValidBarcode(manual)) {
            Icon(Icons.Filled.Search, contentDescription = "Cari")
        }
    }
    Text(
        "Data gizi dari Open Food Facts (gratis, diisi komunitas dari label). Hanya nomor barcode yang dikirim ke internet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (recent.isNotEmpty()) {
        SectionHeader("Pernah di-scan")
        recent.forEach { p ->
            HawCard(onClick = { onOpenRecent(p) }, contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(
                            "${Format.kcal(p.per100.calories)}/100 g · ${PackagedProduct.sourceLabel(p.source)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Busy(title: String, subtitle: String) {
    HawCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoundContent(
    phase: ScanPhase.Found,
    state: BarcodeUi,
    viewModel: BarcodeViewModel,
    onSave: () -> Unit,
    onFixWithLabel: () -> Unit,
) {
    val p = phase.product
    val cs = MaterialTheme.colorScheme
    HawCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(p.displayName, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(PackagedProduct.sourceLabel(p.source), cs.primary)
                if (phase.cached) Pill("Tersimpan di HP", cs.tertiary)
            }
            Text("Barcode ${p.barcode}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Text(
                buildString {
                    append("Per 100 g: ${Format.kcal(p.per100.calories)}")
                    p.servingG?.let { append(" · takaran saji ${it.roundToInt()} g") }
                    p.packageG?.let { append(" · isi ${it.roundToInt()} g") }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (p.suspicious) {
        Surface(color = cs.errorContainer, shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WarningAmber, null, tint = cs.onErrorContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Angka kalori tidak cocok dengan protein/karbo/lemaknya. Data mungkin salah ketik. Sebaiknya foto label gizinya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onErrorContainer,
                )
            }
        }
    }

    Text("Berapa yang dimakan?", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val grams = viewModel.gramsValue()
        val options = buildList {
            p.servingG?.let { add("1 takaran saji (${it.roundToInt()} g)" to it) }
            p.packageG?.let {
                add("½ kemasan (${(it / 2).roundToInt()} g)" to it / 2)
                add("1 kemasan (${it.roundToInt()} g)" to it)
            }
            add("100 g" to 100.0)
        }.distinctBy { it.second.roundToInt() }
        options.forEach { (label, value) ->
            FilterChip(
                selected = grams != null && grams.roundToInt() == value.roundToInt(),
                onClick = { viewModel.setGrams(value.roundToInt().toString()) },
                label = { Text(label) },
            )
        }
    }
    OutlinedTextField(
        value = state.grams,
        onValueChange = viewModel::setGrams,
        label = { Text("Jumlah (gram / ml)") },
        singleLine = true,
        isError = state.grams.isNotBlank() && viewModel.gramsValue() == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    viewModel.gramsValue()?.let { g ->
        val n = p.nutrientsFor(g)
        HawCard(color = cs.primaryContainer.copy(alpha = 0.5f)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Format.kcal(n.calories), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                MacroInline(n)
                Text(
                    "Gula ${Format.oneDecimal(n.sugar)} g · Natrium ${n.sodium.roundToInt()} mg",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }

    Text("Waktu makan", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MealType.entries.forEach { t ->
            FilterChip(selected = state.mealType == t, onClick = { viewModel.setMealType(t) }, label = { Text("${t.emoji} ${t.label}") })
        }
    }
    Button(
        onClick = onSave,
        enabled = viewModel.gramsValue() != null && !state.saving,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Catat") }
    Row {
        TextButton(onClick = onFixWithLabel) { Text("Data salah? Foto label") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = viewModel::rescan) { Text("Scan lagi") }
    }
}

@Composable
private fun NotFoundContent(
    phase: ScanPhase.NotFound,
    onPhotoLabel: () -> Unit,
    onGallery: () -> Unit,
    onManual: () -> Unit,
    onRescan: () -> Unit,
) {
    HawCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Data gizi belum tersedia", style = MaterialTheme.typography.titleMedium)
            Text(phase.message, style = MaterialTheme.typography.bodyMedium)
            Text("Barcode ${phase.barcode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Foto tabel Informasi Nilai Gizi di kemasan, AI akan membacanya. Produk ini lalu diingat, jadi scan berikutnya langsung muncul.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Button(onClick = onPhotoLabel, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Icon(Icons.Filled.CameraAlt, null)
        Spacer(Modifier.width(8.dp))
        Text("Foto label gizi")
    }
    OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.PhotoLibrary, null)
        Spacer(Modifier.width(8.dp))
        Text("Pilih foto label dari galeri")
    }
    OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.EditNote, null)
        Spacer(Modifier.width(8.dp))
        Text("Isi angka label sendiri")
    }
    TextButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) { Text("Scan produk lain") }
}

@Composable
private fun LabelCaptureContent(
    hasPermission: Boolean,
    onGrant: () -> Unit,
    onCaptured: (File) -> Unit,
    onCancel: () -> Unit,
    onGallery: () -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    Text(
        "Foto tabel \"Informasi Nilai Gizi\": dekat, lurus, terang, dan baris \"Takaran saji\" ikut terlihat.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (hasPermission) {
        CameraBox(scanning = false, onCaptured = onCaptured, onError = { error = it })
    } else {
        NeedPermission(onGrant)
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Row {
        TextButton(onClick = onGallery) { Text("Pilih dari galeri") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text("Batal") }
    }
}

@Composable
private fun ManualContent(phase: ScanPhase.Manual, viewModel: BarcodeViewModel) {
    var form by remember { mutableStateOf(ManualLabel(name = phase.partial?.name.orEmpty())) }
    var invalid by remember { mutableStateOf(false) }
    Text("Isi sesuai label (per takaran saji)", style = MaterialTheme.typography.titleMedium)
    Text("Barcode ${phase.barcode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    @Composable
    fun field(label: String, value: String, number: Boolean = true, onChange: (String) -> Unit) = OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (number) it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) else it.take(60)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
    field("Nama produk", form.name, number = false) { form = form.copy(name = it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { field("Takaran saji (g/ml) *", form.servingG) { form = form.copy(servingG = it) } }
        Box(Modifier.weight(1f)) { field("Isi kemasan (g/ml)", form.packageG) { form = form.copy(packageG = it) } }
    }
    field("Energi total (kkal) *", form.calories) { form = form.copy(calories = it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { field("Protein (g)", form.protein) { form = form.copy(protein = it) } }
        Box(Modifier.weight(1f)) { field("Karbo (g)", form.carbs) { form = form.copy(carbs = it) } }
        Box(Modifier.weight(1f)) { field("Lemak (g)", form.fat) { form = form.copy(fat = it) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { field("Gula (g)", form.sugar) { form = form.copy(sugar = it) } }
        Box(Modifier.weight(1f)) { field("Natrium (mg)", form.sodium) { form = form.copy(sodium = it) } }
    }
    if (invalid) Text("Takaran saji dan energi wajib diisi.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    Button(onClick = { invalid = !viewModel.saveManual(form) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Simpan produk") }
    TextButton(onClick = viewModel::rescan, modifier = Modifier.fillMaxWidth()) { Text("Batal") }
}
