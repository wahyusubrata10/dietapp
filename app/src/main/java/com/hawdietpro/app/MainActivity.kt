package com.hawdietpro.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hawdietpro.app.core.Notifications
import com.hawdietpro.app.ui.DeepLink
import com.hawdietpro.app.ui.HawDietRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val deepLinks = MutableStateFlow<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            HawDietRoot(deepLinks = deepLinks, onDeepLinkHandled = { deepLinks.value = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val mealId = intent.getLongExtra(Notifications.EXTRA_OPEN_MEAL, 0L)
        when {
            mealId != 0L -> deepLinks.value = DeepLink.Meal(mealId)
            intent.getBooleanExtra(Notifications.EXTRA_OPEN_UPDATE, false) -> deepLinks.value = DeepLink.Update
        }
    }
}
