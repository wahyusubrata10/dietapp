package com.hawdietpro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Foto makanan layar penuh: cubit untuk memperbesar, geser saat diperbesar, ketuk dua kali untuk
 * memperbesar/kembali. Tombol kembali atau ✕ menutup.
 */
@Composable
fun PhotoViewer(path: String, description: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var box by remember { mutableStateOf(IntSize.Zero) }

        // Batasi geseran agar tepi foto tidak lepas dari layar.
        fun clamp(o: Offset, s: Float): Offset {
            val maxX = box.width * (s - 1f) / 2f
            val maxY = box.height * (s - 1f) / 2f
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }

        val transform = rememberTransformableState { zoom, pan, _ ->
            val next = (scale * zoom).coerceIn(1f, MAX_ZOOM)
            scale = next
            offset = clamp(offset + pan, next)
        }
        val context = LocalContext.current
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { box = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                // Titik yang diketuk tetap di tempatnya setelah diperbesar.
                                val center = Offset(box.width / 2f, box.height / 2f)
                                scale = DOUBLE_TAP_ZOOM
                                offset = clamp((center - tap) * (DOUBLE_TAP_ZOOM - 1f), DOUBLE_TAP_ZOOM)
                            }
                        },
                    )
                }
                .transformable(transform),
        ) {
            AsyncImage(
                model = remember(path) { ImageRequest.Builder(context).data(File(path)).crossfade(180).build() },
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
            ) { Icon(Icons.Filled.Close, contentDescription = "Tutup foto") }
        }
    }
}
