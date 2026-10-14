package com.hawdietpro.app.ui.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hawdietpro.app.domain.model.MealType
import java.io.File

@Composable
fun CameraScreen(
    onClose: () -> Unit,
    onPhotoReady: (path: String, type: MealType, time: Long, hint: String?) -> Unit,
    onManual: (MealType) -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onPicked)
    }
    val pickFromGallery = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    LaunchedEffect(Unit) {
        if (viewModel.openGalleryOnStart) {
            viewModel.consumeOpenGallery()
            pickFromGallery()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val photo = state.photoPath
        when {
            photo != null -> ReviewStep(
                photoPath = photo,
                state = state,
                onTypeChange = viewModel::setMealType,
                onHintChange = viewModel::setHint,
                onRetake = viewModel::retake,
                onAnalyze = {
                    viewModel.handOff()
                    onPhotoReady(photo, state.mealType, state.capturedAt, state.hint.takeIf { it.isNotBlank() })
                },
            )
            hasPermission -> CaptureStep(
                captureDir = viewModel.captureDir,
                busy = state.processing,
                mealType = state.mealType,
                onTypeChange = viewModel::setMealType,
                onCaptured = viewModel::onCaptured,
                onGallery = pickFromGallery,
                onManual = { onManual(state.mealType) },
                onCaptureError = viewModel::showError,
            )
            else -> NoPermission(onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) }, onGallery = pickFromGallery, onManual = { onManual(state.mealType) })
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f), contentColor = Color.White),
        ) { Icon(Icons.Filled.Close, contentDescription = "Tutup") }

        if (state.processing) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        state.error?.let { err ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(err, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = viewModel::clearError) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun CaptureStep(
    captureDir: File,
    busy: Boolean,
    mealType: MealType,
    onTypeChange: (MealType) -> Unit,
    onCaptured: (File) -> Unit,
    onGallery: () -> Unit,
    onManual: () -> Unit,
    onCaptureError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) }
    }
    var flashOn by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = controller
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Bingkai bantu agar piring berada di tengah.
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth(0.82f).aspectRatio(1f)
                    .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            )
            IconButton(
                onClick = {
                    flashOn = !flashOn
                    controller.imageCaptureFlashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                },
                modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopEnd),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f), contentColor = Color.White),
            ) { Icon(if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff, contentDescription = "Flash") }
            Text(
                "Foto dari atas, seluruh piring terlihat",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MealTypeChips(mealType, onTypeChange, dark = true)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundAction(Icons.Filled.PhotoLibrary, "Galeri", onGallery)
                Box(
                    Modifier.size(78.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)).padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        onClick = onClick@{
                            if (busy) return@onClick
                            val file = File(captureDir, "raw_${System.currentTimeMillis()}.jpg")
                            controller.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) = onCaptured(file)
                                    override fun onError(exception: ImageCaptureException) = onCaptureError(exception.message ?: "Gagal mengambil foto")
                                },
                            )
                        },
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.fillMaxSize(),
                    ) {}
                }
                RoundAction(Icons.Filled.EditNote, "Manual", onManual)
            }
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.15f), contentColor = Color.White),
            modifier = Modifier.size(52.dp),
        ) { Icon(icon, contentDescription = label) }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun MealTypeChips(selected: MealType, onSelect: (MealType) -> Unit, dark: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MealType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text("${type.emoji} ${type.label}", color = if (dark && type != selected) Color.White else androidx.compose.ui.graphics.Color.Unspecified) },
            )
        }
    }
}

@Composable
private fun ReviewStep(
    photoPath: String,
    state: CameraState,
    onTypeChange: (MealType) -> Unit,
    onHintChange: (String) -> Unit,
    onRetake: () -> Unit,
    onAnalyze: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(),
        ) {
            AsyncImage(
                model = File(photoPath),
                contentDescription = "Foto makanan",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Waktu makan", style = MaterialTheme.typography.titleMedium)
                MealTypeChips(state.mealType, onTypeChange, modifier = Modifier.padding(horizontal = 0.dp))
                OutlinedTextField(
                    value = state.hint,
                    onValueChange = onHintChange,
                    label = { Text("Catatan untuk AI (opsional)") },
                    placeholder = { Text("mis. nasi setengah porsi, ayam tanpa kulit") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large) {
                    Icon(Icons.Filled.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Analisa dengan AI", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Icon(Icons.Filled.Replay, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Foto ulang")
                }
            }
        }
    }
}

@Composable
private fun NoPermission(onGrant: () -> Unit, onGallery: () -> Unit, onManual: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Izin kamera diperlukan", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Izinkan kamera untuk memotret makanan, atau pilih foto dari galeri.",
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text("Izinkan kamera") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onGallery) { Text("Pilih dari galeri", color = Color.White) }
        TextButton(onClick = onManual) { Text("Input manual", color = Color.White) }
    }
}
