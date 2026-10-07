package com.example.myapplication4.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.myapplication4.MainActivity
import com.example.myapplication4.R

object ReminderNotifications {
    const val CHANNEL = "reviews"
    const val OPEN_REVIEW = "com.example.myapplication4.REVIEW_DUE"
    fun blockedReason(context: Context): String? {
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return context.getString(R.string.ui_notification_blocked)
        if (Build.VERSION.SDK_INT >= 26 && context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return context.getString(R.string.ui_channel_blocked)
        return null
    }
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.review_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
    @android.annotation.SuppressLint("MissingPermission")
    fun post(context: Context, due: Int, test: Boolean = false, windowStart: Boolean = false): String? {
        blockedReason(context)?.let { return it }
        val open = PendingIntent.getActivity(context, 10, Intent(context, MainActivity::class.java).setAction(OPEN_REVIEW)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getBroadcast(context, 11, Intent(context, ReminderActionReceiver::class.java).setAction(ReminderActionReceiver.PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return try {
            val title = if (test) context.getString(R.string.ui_test_notification_title) else context.resources.getQuantityString(R.plurals.review_ready, due, due)
            val body = if (test) context.getString(R.string.ui_test_notification_body) else if (windowStart) context.getString(R.string.ui_window_notification_body) else context.getString(R.string.review_notification_copy)
            context.getSystemService(NotificationManager::class.java).notify(if (test) 1002 else 1001,
                NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(false)
                    .addAction(0, context.getString(R.string.ui_review_now), open).addAction(0, context.getString(R.string.ui_notification_pause), pause).build())
            null
        } catch (_: SecurityException) { context.getString(R.string.ui_notification_permission_error) }
    }
}
