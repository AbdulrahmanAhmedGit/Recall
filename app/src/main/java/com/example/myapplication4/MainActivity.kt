package com.example.myapplication4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.remember
import com.example.myapplication4.util.RecallLocale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication4.ui.RecallRoot
import com.example.myapplication4.ui.design.RecallTheme

class MainActivity : ComponentActivity() {
    private val recallViewModel: RecallViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleReminderIntent(intent)
        setContent { RecallApp(recallViewModel) }
    }
    override fun onResume() { super.onResume(); recallViewModel.onResume() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleReminderIntent(intent) }
    private fun handleReminderIntent(intent: Intent?) {
        if (intent?.action == com.example.myapplication4.notifications.ReminderNotifications.OPEN_REVIEW) {
            recallViewModel.openReviewLink()
            intent.action = null
        }
    }
}

@Composable
fun RecallApp(viewModel: RecallViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dark = when (settings.themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Configuration contexts are not Activity owners. Retain the real hosts for
    // permission requests, document pickers, and other Activity-bound UI integrations.
    val activity = LocalActivity.current
    val resultOwner = checkNotNull(LocalActivityResultRegistryOwner.current)
    val localized = remember(context, configuration, settings.language) {
        RecallLocale.context(context, settings.language)
    }
    val rtl = localized.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
    RecallTheme(darkTheme = dark, dynamicColor = settings.dynamicColor, language = localized.resources.configuration.locales[0].language) {
        CompositionLocalProvider(LocalContext provides localized,
            LocalActivity provides activity,
            LocalActivityResultRegistryOwner provides resultOwner,
            LocalConfiguration provides localized.resources.configuration,
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            RecallRoot(viewModel)
        }
    }
}
