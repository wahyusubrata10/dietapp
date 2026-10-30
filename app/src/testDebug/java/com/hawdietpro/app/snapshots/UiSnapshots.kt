package com.hawdietpro.app.snapshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.hawdietpro.app.ui.feature.home.HomeActions
import com.hawdietpro.app.ui.feature.home.HomeContent
import com.hawdietpro.app.ui.navigation.HawBottomBar
import com.hawdietpro.app.ui.navigation.QuickAddContent
import com.hawdietpro.app.ui.theme.HawDietTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Merender layar utama ke PNG (build/snapshots) untuk ditinjau tanpa emulator.
 * Dijalankan di CI oleh workflow "UI snapshots"; hasilnya dipublikasikan ke branch ui-snapshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h880dp-xxhdpi")
class UiSnapshots {

    @get:Rule
    val compose = createComposeRule()

    private fun snap(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent {
            HawDietTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        // Animasi (masuk, angka, cincin) semuanya terbatas → tunggu sampai diam.
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(System.getProperty("snapshotDir") ?: "build/snapshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun home_dark() = snap("home_dark") { HomeContent(SampleData.home(), HomeActions()) }

    @Test
    fun home_light() = snap("home_light", dark = false) { HomeContent(SampleData.home(), HomeActions()) }

    @Test
    @Config(qualifiers = "w400dp-h2200dp-xxhdpi")
    fun home_dark_full() = snap("home_dark_full") { HomeContent(SampleData.home(), HomeActions()) }

    @Test
    @Config(qualifiers = "w400dp-h2200dp-xxhdpi")
    fun home_light_full() = snap("home_light_full", dark = false) { HomeContent(SampleData.home(), HomeActions()) }

    @Test
    fun home_empty_dark() = snap("home_empty_dark") { HomeContent(SampleData.homeEmpty(), HomeActions()) }

    @Test
    @Config(qualifiers = "w400dp-h300dp-xxhdpi")
    fun bottom_bar_dark() = snap("bottom_bar_dark") {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            HawBottomBar(currentRoute = { it == "beranda" }, onNavigate = {}, onAdd = {})
        }
    }

    @Test
    fun quick_add_dark() = snap("quick_add_dark") { QuickAddContent(onAction = {}) }
}
