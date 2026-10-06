package com.example.myapplication4.ui

import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.LayoutDirection
import com.example.myapplication4.BuildConfig
import com.example.myapplication4.R
import com.example.myapplication4.ui.components.*
import com.example.myapplication4.ui.design.*
import java.util.Locale

@Composable
internal fun recallInfoResources(language: String): Resources {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration, language) {
        if (language in setOf("en", "ar")) {
            context.createConfigurationContext(Configuration(configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }).resources
        } else context.resources
    }
}

/** One offline guide, shared by onboarding and Settings; sources are optional external links. */
@Composable
fun RecallInfoScreen(language: String, firstLaunch: Boolean = false, onClose: () -> Unit) {
    val resources = recallInfoResources(language)
    val uriHandler = LocalUriHandler.current
    var linkError by remember { mutableStateOf(false) }
    val direction = if (resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
    BackHandler { onClose() }
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        ScreenFrame {
            Column(Modifier.fillMaxSize()) {
                RecallTopBar(if (firstLaunch) resources.getString(R.string.info_welcome, resources.getString(R.string.app_name)) else resources.getString(R.string.info_title),
                    resources.getString(R.string.info_subtitle), onBack = if (firstLaunch) null else onClose)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RecallSpacing.lg), contentPadding = PaddingValues(bottom = RecallSpacing.lg)) {
                    item {
                        Surface(color = MaterialTheme.colorScheme.surfaceSelected, shape = RecallRadii.large) {
                            Column(Modifier.fillMaxWidth().padding(RecallSpacing.ml), verticalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) {
                                BidiAwareText(resources.getString(R.string.info_latest), style = MaterialTheme.typography.titleMedium)
                                BidiAwareText(resources.getString(R.string.info_latest_body), style = MaterialTheme.typography.bodyMedium)
                                BidiAwareText(resources.getString(R.string.info_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.muted)
                            }
                        }
                    }
                    item { InfoSection(resources, R.string.info_workflow, R.string.info_workflow_body) }
                    item { InfoSection(resources, R.string.info_science, R.string.info_science_body) }
                    item { InfoSection(resources, R.string.info_scheduler, R.string.info_scheduler_body) }
                    item { InfoSection(resources, R.string.info_ratings, R.string.info_ratings_body) }
                    item { InfoSection(resources, R.string.info_daily, R.string.info_daily_body) }
                    item { InfoSection(resources, R.string.info_privacy, R.string.info_privacy_body) }
                    item {
                        BidiAwareText(resources.getString(R.string.info_sources), style = MaterialTheme.typography.titleMedium)
                        listOf(
                            R.string.info_retrieval_source to "https://pubmed.ncbi.nlm.nih.gov/26151629/",
                            R.string.info_spacing_source to "https://pubmed.ncbi.nlm.nih.gov/16719566/",
                            R.string.info_fsrs_source to "https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm",
                        ).forEach { (label, url) ->
                            TextButton(onClick = { linkError = runCatching { uriHandler.openUri(url) }.isFailure }, modifier = Modifier.fillMaxWidth()) {
                                BidiAwareText(resources.getString(label), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        if (linkError) BidiAwareText(resources.getString(R.string.info_link_error), color = MaterialTheme.colorScheme.error)
                    }
                }
                RecallPrimaryButton(resources.getString(if (firstLaunch) R.string.info_start else R.string.info_done), Icons.Outlined.AutoAwesome, onClose,
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = RecallSpacing.sm))
            }
        }
    }
}

@Composable
private fun InfoSection(resources: Resources, title: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) {
        BidiAwareText(resources.getString(title), style = MaterialTheme.typography.titleMedium)
        BidiAwareText(resources.getString(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
