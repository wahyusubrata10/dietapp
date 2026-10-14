package com.hawdietpro.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hawdietpro.app.ui.feature.analytics.AnalyticsScreen
import com.hawdietpro.app.ui.feature.history.HistoryScreen
import com.hawdietpro.app.ui.feature.home.HomeScreen
import com.hawdietpro.app.ui.feature.profile.ProfileScreen
import com.hawdietpro.app.ui.navigation.Routes
import com.hawdietpro.app.ui.navigation.TopLevelDestination

@Composable
fun HawDietRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val showBottomBar = TopLevelDestination.entries.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(if (selected) dest.selectedIcon else dest.unselectedIcon, contentDescription = dest.label)
                            },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) { HomeScreen() }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.ANALYTICS) { AnalyticsScreen() }
            composable(Routes.PROFILE) { ProfileScreen() }
        }
    }
}
