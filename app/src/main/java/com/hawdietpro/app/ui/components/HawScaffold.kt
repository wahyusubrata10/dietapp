package com.hawdietpro.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Scaffold standar aplikasi. [topLevel] = layar dengan bottom navigation
 * (inset bawah sudah ditangani oleh scaffold utama).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HawScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    topLevel: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // Layar utama: judul besar yang mengecil saat di-scroll (gaya iOS). Layar lain: judul ringkas.
    val scrollBehavior = if (topLevel) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    } else {
        TopAppBarDefaults.pinnedScrollBehavior()
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (topLevel) {
                LargeTopAppBar(
                    title = { Text(title, maxLines = 1) },
                    actions = actions,
                    expandedHeight = 116.dp,
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            } else {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                        }
                    },
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        contentWindowInsets = if (topLevel) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

