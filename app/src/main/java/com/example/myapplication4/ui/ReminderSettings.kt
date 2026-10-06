package com.example.myapplication4.ui

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
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val blocked = remember(refresh) { ReminderNotifications.blockedReason(context) }
    val optimized = remember(refresh) { !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    val restricted = remember(refresh) { Build.VERSION.SDK_INT >= 28 && context.getSystemService(ActivityManager::class.java).isBackgroundRestricted }
    fun open(intent: Intent) { runCatching { context.startActivity(intent) }.onFailure { error = "Open Android Settings → Apps → Recall to change this setting." } }
    Column(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.sm), verticalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
        Text("Background delivery", style = MaterialTheme.typography.titleSmall)
        Text(blocked ?: "Android notifications are allowed", style = MaterialTheme.typography.bodySmall, color = if (blocked == null) MaterialTheme.colorScheme.muted else MaterialTheme.colorScheme.error)
        Text(if (restricted) "Background activity is restricted" else if (optimized) "Battery optimization is on" else "Battery optimization is unrestricted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        if (context.getSystemService(android.app.NotificationManager::class.java).currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL) {
            Text("Do Not Disturb may silence notification sounds or banners.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        }
        Text("Recall checks in the background without internet or charging. Android may delay checks during battery saving or Doze. For fewer delays, choose Unrestricted battery use and remove Recall from sleeping apps. After force-stopping Recall, open it again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        if (blocked != null) TextButton({ requestPermission(); refresh++ }) { Text("Allow notifications") }
        Row {
            TextButton({
                open(if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
            }) { Text("Notification settings") }
            TextButton({ open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("Battery settings") }
        }
        TextButton({ open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))) }) { Text("App / background settings") }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun ReminderDebugPanel(vm: RecallViewModel, result: (String) -> Unit) {
    val context = LocalContext.current
    val prefs by context.recallPreferences.data.collectAsStateWithLifecycle(emptyPreferences())
    val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ReminderWorker.PERIODIC) }.collectAsStateWithLifecycle(emptyList())
    fun time(value: Long?) = value?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "Not yet"
    Column(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.sm), verticalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
        Text("Debug mode", style = MaterialTheme.typography.titleMedium)
        Text("Background work: " + (work.firstOrNull()?.state?.name ?: "Not scheduled"), style = MaterialTheme.typography.bodySmall)
        Text("Last check: " + time(prefs[ReminderDiagnostics.lastRun]), style = MaterialTheme.typography.bodySmall)
        Text("Last reminder: " + time(prefs[ReminderDiagnostics.lastSent]), style = MaterialTheme.typography.bodySmall)
        Text(prefs[ReminderDiagnostics.reason] ?: "No check has run yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        prefs[ReminderDiagnostics.nextEligible]?.let { Text("Next allowed time: " + time(it), style = MaterialTheme.typography.bodySmall) }
        Text("The manual test uses the real notification channel but ignores due counts, quiet times and pauses. It does not change your reminder cooldown.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        Button({ vm.testNotification(result) }) { Text("Test notification") }
        TextButton({ vm.onResume(); result("Background check requested.") }) { Text("Run background check") }
        TextButton({ vm.setDebugMode(false) }) { Text("Disable debug mode") }
    }
}
