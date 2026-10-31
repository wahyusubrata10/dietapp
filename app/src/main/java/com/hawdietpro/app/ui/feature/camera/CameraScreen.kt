package com.hawdietpro.app.ui.feature.camera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.hawdietpro.app.ui.components.bouncyClick
import com.hawdietpro.app.ui.theme.HawTheme
import kotlinx.coroutines.launch
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
    // Ditolak permanen ("jangan tanya lagi"): dialog izin tidak muncul lagi → arahkan ke Pengaturan aplikasi.
    var permanentlyDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permanentlyDenied = !granted && context.findActivity()?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true
    }
    // Kembali dari Pengaturan: cek ulang izin.
    LifecycleResumeEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }
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
            else -> NoPermission(
                permanentlyDenied = permanentlyDenied,
                onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onGallery = pickFromGallery,
                onManual = { onManual(state.mealType) },
            )
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
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val controller = remember {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) }
    }
    var flashOn by remember { mutableStateOf(false) }
    val flash = remember { Animatable(0f) }
    // Kunci rana sejak ditekan sampai foto tersimpan (cegah ketukan ganda → dua foto).
    var capturing by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    // Bingkai bantu "bernapas" pelan agar terasa hidup.
    val breathe by rememberInfiniteTransition(label = "frame").animateFloat(
        0.55f, 0.95f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "frameAlpha",
    )

    fun shoot() {
        if (busy || capturing) return
        capturing = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            flash.snapTo(0.85f)
            flash.animateTo(0f, tween(320))
        }
        val file = File(captureDir, "raw_${System.currentTimeMillis()}.jpg")
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    onCaptured(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    file.delete()
                    onCaptureError(exception.message ?: "Gagal mengambil foto")
                }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        // Pratinjau kamera layar penuh (ketuk untuk fokus ditangani PreviewView).
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Bingkai sudut di tengah: posisikan piring di dalamnya.
        Canvas(Modifier.align(Alignment.Center).padding(bottom = 120.dp).fillMaxWidth(0.82f).aspectRatio(1f)) {
            val len = size.minDimension * 0.16f
            val sw = 4.dp.toPx()
            val c = Color.White.copy(alpha = breathe)
            val r = 24.dp.toPx()
            // Empat sudut berbentuk "L" membulat.
            listOf(
                Offset(0f, 0f) to (1f to 1f),
                Offset(size.width, 0f) to (-1f to 1f),
                Offset(0f, size.height) to (1f to -1f),
                Offset(size.width, size.height) to (-1f to -1f),
            ).forEach { (o, d) ->
                val (dx, dy) = d
                val path = Path().apply {
                    moveTo(o.x, o.y + dy * (len + r))
                    lineTo(o.x, o.y + dy * r)
                    quadraticTo(o.x, o.y, o.x + dx * r, o.y)
                    lineTo(o.x + dx * (len + r), o.y)
                }
                drawPath(path, c, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        // Gradasi atas & bawah agar kontrol terbaca.
        Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(300.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
        )
        Row(
            Modifier.statusBarsPadding().padding(top = 10.dp).align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Foto dari atas · seluruh piring terlihat",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        IconButton(
            onClick = {
                flashOn = !flashOn
                controller.imageCaptureFlashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            },
            modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopEnd),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f), contentColor = if (flashOn) Color(0xFFFFD27A) else Color.White),
        ) { Icon(if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff, contentDescription = "Flash") }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MealTypeChips(mealType, onTypeChange, dark = true)
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundAction(Icons.Filled.PhotoLibrary, "Galeri", onGallery)
                ShutterButton(enabled = !busy && !capturing, onClick = ::shoot)
                RoundAction(Icons.Filled.EditNote, "Manual", onManual)
            }
        }
        // Kilat putih singkat saat memotret.
        if (flash.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
    }
}

/** Tombol rana: cincin putih + lingkaran dalam yang mengecil saat ditekan. */
@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val inner by animateFloatAsState(if (pressed) 0.78f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "shutter")
    Box(
        Modifier
            .size(82.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = inner; scaleY = inner }.clip(CircleShape).background(if (enabled) Color.White else Color.White.copy(alpha = 0.5f)))
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White),
            modifier = Modifier.size(54.dp),
        ) { Icon(icon, contentDescription = label) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun MealTypeChips(selected: MealType, onSelect: (MealType) -> Unit, dark: Boolean = false, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MealType.entries.forEach { type ->
            val active = type == selected
            val bg = when {
                active -> cs.primary
                dark -> Color.White.copy(alpha = 0.16f)
                else -> cs.surfaceContainerHigh
            }
            val fg = when {
                active -> cs.onPrimary
                dark -> Color.White
                else -> cs.onSurface
            }
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .bouncyClick { onSelect(type) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(type.emoji, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(6.dp))
                Text(type.label, style = MaterialTheme.typography.labelLarge, color = fg)
            }
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
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()) {
            Box {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = "Foto makanan",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.95f),
                )
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(0.6f to Color.Transparent, 1f to cs.background),
                    ),
                )
            }
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Siap dianalisa?", style = MaterialTheme.typography.headlineSmall)
                Text("Pilih waktu makan", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                MealTypeChips(state.mealType, onTypeChange, modifier = Modifier.padding(horizontal = 0.dp))
                OutlinedTextField(
                    value = state.hint,
                    onValueChange = onHintChange,
                    label = { Text("Catatan untuk AI (opsional)") },
                    placeholder = { Text("mis. nasi setengah porsi, ayam tanpa kulit") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    maxLines = 3,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(cs.primary, HawTheme.colors.accentEnd)))
                        .bouncyClick(onClick = onAnalyze),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = cs.onPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text("Analisa dengan AI", style = MaterialTheme.typography.titleMedium, color = cs.onPrimary)
                    }
                }
                OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth().height(50.dp), shape = CircleShape) {
                    Icon(Icons.Filled.Replay, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Foto ulang")
                }
            }
        }
    }
}

@Composable
private fun NoPermission(
    permanentlyDenied: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    onGallery: () -> Unit,
    onManual: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.CameraAlt, null, tint = Color.White, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Izin kamera diperlukan", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (permanentlyDenied) "Izin kamera sedang dimatikan. Buka Pengaturan → Izin → Kamera → Izinkan, lalu kembali ke sini."
            else "Izinkan kamera untuk memotret makanan, atau pilih foto dari galeri.",
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (permanentlyDenied) {
            Button(onClick = onOpenSettings, shape = CircleShape) { Text("Buka Pengaturan") }
        } else {
            Button(onClick = onGrant, shape = CircleShape) { Text("Izinkan kamera") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onGallery, shape = CircleShape) { Text("Pilih dari galeri", color = Color.White) }
        TextButton(onClick = onManual) { Text("Input manual", color = Color.White) }
    }
}

/** Cari Activity dari Context Compose (bisa terbungkus ContextWrapper). */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
