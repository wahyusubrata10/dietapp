package com.hawdietpro.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val HOME = "beranda"
    const val HISTORY = "riwayat"
    const val ANALYTICS = "analisa"
    const val PROFILE = "profil"
}

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(Routes.HOME, "Beranda", Icons.Filled.Home, Icons.Outlined.Home),
    HISTORY(Routes.HISTORY, "Riwayat", Icons.Filled.History, Icons.Outlined.History),
    ANALYTICS(Routes.ANALYTICS, "Analisa", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    PROFILE(Routes.PROFILE, "Profil", Icons.Filled.Person, Icons.Outlined.Person),
}
