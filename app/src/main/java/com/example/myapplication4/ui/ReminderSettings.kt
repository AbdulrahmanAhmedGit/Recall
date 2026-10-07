package com.example.myapplication4.ui

import com.example.myapplication4.R
import com.example.myapplication4.util.recallStrings
import com.example.myapplication4.util.reminderDiagnostic
import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import com.example.myapplication4.RecallViewModel
import com.example.myapplication4.data.recallPreferences
import com.example.myapplication4.notifications.*
import com.example.myapplication4.ui.design.*
import java.text.DateFormat
import java.util.Date
import androidx.datastore.preferences.core.emptyPreferences

@Composable
internal fun BackgroundReminderSettings(requestPermission: () -> Unit) {
    val s = recallStrings()

    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val blocked = remember(refresh, context) { ReminderNotifications.blockedReason(context) }
    val optimized = remember(refresh) { !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    val restricted = remember(refresh) { Build.VERSION.SDK_INT >= 28 && context.getSystemService(ActivityManager::class.java).isBackgroundRestricted }
    fun open(intent: Intent) {
        // The localized resource context is not an Activity; keep external navigation
        // on the real host so Android does not reject the launch.
        runCatching { (activity ?: context).startActivity(intent.apply { if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
            .onFailure { error = s(R.string.ui_android_settings_hint) }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.sm), verticalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
        Text(s(R.string.ui_background_delivery), style = MaterialTheme.typography.titleSmall)
        Text(blocked ?: s(R.string.ui_notifications_allowed), style = MaterialTheme.typography.bodySmall, color = if (blocked == null) MaterialTheme.colorScheme.muted else MaterialTheme.colorScheme.error)
        Text(if (restricted) s(R.string.ui_background_restricted) else if (optimized) s(R.string.ui_battery_on) else s(R.string.ui_battery_unrestricted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        if (context.getSystemService(android.app.NotificationManager::class.java).currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL) {
            Text(s(R.string.ui_dnd_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        }
        Text(s(R.string.ui_background_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        if (blocked != null) TextButton({ requestPermission(); refresh++ }) { Text(s(R.string.ui_allow_notifications)) }
        Row {
            TextButton({
                open(if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
            }) { Text(s(R.string.ui_notification_settings)) }
            TextButton({ open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text(s(R.string.ui_battery_settings)) }
        }
        TextButton({ open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))) }) { Text(s(R.string.ui_app_background_settings)) }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun ReminderDebugPanel(vm: RecallViewModel, result: (String) -> Unit) {
    val s = recallStrings()

    val context = LocalContext.current
    val prefs by context.recallPreferences.data.collectAsStateWithLifecycle(emptyPreferences())
    val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ReminderWorker.PERIODIC) }.collectAsStateWithLifecycle(emptyList())
    fun time(value: Long?) = value?.let { s.date(it, time = true) } ?: s(R.string.ui_not_yet)
    Column(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.sm), verticalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
        Text(s(R.string.ui_debug_mode), style = MaterialTheme.typography.titleMedium)
        Text(s(R.string.ui_background_work, work.firstOrNull()?.state?.let { s(when(it) { androidx.work.WorkInfo.State.ENQUEUED -> R.string.ui_work_enqueued; androidx.work.WorkInfo.State.RUNNING -> R.string.ui_work_running; androidx.work.WorkInfo.State.SUCCEEDED -> R.string.ui_work_succeeded; androidx.work.WorkInfo.State.FAILED -> R.string.ui_work_failed; androidx.work.WorkInfo.State.BLOCKED -> R.string.ui_work_blocked; androidx.work.WorkInfo.State.CANCELLED -> R.string.ui_work_cancelled }) } ?: s(R.string.ui_not_scheduled)), style = MaterialTheme.typography.bodySmall)
        Text(s(R.string.ui_last_check_time, time(prefs[ReminderDiagnostics.lastRun])), style = MaterialTheme.typography.bodySmall)
        Text(s(R.string.ui_last_reminder_time, time(prefs[ReminderDiagnostics.lastSent])), style = MaterialTheme.typography.bodySmall)
        Text(prefs[ReminderDiagnostics.reason]?.let(s::reminderDiagnostic) ?: s(R.string.ui_no_checks), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        prefs[ReminderDiagnostics.nextEligible]?.let { Text(s(R.string.ui_next_allowed_time, time(it)), style = MaterialTheme.typography.bodySmall) }
        Text(s(R.string.ui_manual_test_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        Button({ vm.testNotification(result) }) { Text(s(R.string.ui_test_notification)) }
        TextButton({ vm.onResume(); result(s(R.string.ui_background_requested)) }) { Text(s(R.string.ui_run_background)) }
        TextButton({ vm.setDebugMode(false) }) { Text(s(R.string.ui_disable_debug)) }
    }
}
