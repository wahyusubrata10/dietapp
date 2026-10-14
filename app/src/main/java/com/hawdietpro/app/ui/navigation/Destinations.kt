package com.hawdietpro.app.ui.navigation

import android.net.Uri
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
import com.hawdietpro.app.domain.model.MealType

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "beranda"
    const val HISTORY = "riwayat"
    const val ANALYTICS = "analisa"
    const val PROFILE = "profil"

    const val CAMERA = "kamera?type={type}&gallery={gallery}"
    const val MEAL_EDITOR = "makan?id={id}&photo={photo}&type={type}&time={time}&hint={hint}&pending={pending}"
    const val PROFILE_EDIT = "profil/edit"
    const val SETTINGS = "pengaturan"
    const val WEIGHT = "berat"
    const val QUEUE = "antrian"
    const val UPDATE = "update"
    const val BACKUP = "backup"

    fun camera(type: MealType? = null, openGallery: Boolean = false) =
        "kamera?type=${type?.name.orEmpty()}&gallery=${if (openGallery) "1" else ""}"

    fun editMeal(id: Long) = "makan?id=$id"

    fun analyzePhoto(photoPath: String, type: MealType, time: Long, hint: String?) =
        "makan?photo=${Uri.encode(photoPath)}&type=${type.name}&time=$time&hint=${Uri.encode(hint.orEmpty())}"

    fun manualMeal(type: MealType? = null, time: Long? = null) =
        "makan?type=${type?.name.orEmpty()}&time=${time ?: ""}"

    fun fromPending(pendingId: Long, photoPath: String, type: MealType, time: Long) =
        "makan?photo=${Uri.encode(photoPath)}&type=${type.name}&time=$time&pending=$pendingId"
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
