package com.kazisasa.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kazisasa.app.MainActivity

/**
 * Fires one deadline reminder. Spec §7.6 is explicit that reminders are *not*
 * guaranteed if the OS blocks, delays, or batches notifications - this worker does
 * everything within its control (checks the live permission at fire time, not just
 * at schedule time) but cannot promise delivery, and the app must not imply it does.
 */
class ReminderWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val profileId = inputData.getString(KEY_PROFILE_ID) ?: return Result.failure()
        val opportunityId = inputData.getString(KEY_OPPORTUNITY_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "A saved opportunity"
        val org = inputData.getString(KEY_ORG) ?: ""

        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        // Permission may have been revoked after the reminder was scheduled. We can't
        // notify the user *right now* if so, but we don't crash or retry forever either.
        if (!hasPermission) return Result.success()

        ensureChannel()

        val notificationKey = "${profileId}_$opportunityId"
        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_OPPORTUNITY_ID, opportunityId)
            putExtra(MainActivity.EXTRA_OPEN_PROFILE_ID, profileId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationKey.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // placeholder - swap for the app's own icon
            .setContentTitle("Deadline approaching: $title")
            .setContentText(if (org.isNotBlank()) "at $org - open Kazi Sasa to review" else "Open Kazi Sasa to review")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationKey.hashCode(), notification)
        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Deadline reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders for saved opportunity deadlines"
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "deadline_reminders"
        const val KEY_PROFILE_ID = "profile_id"
        const val KEY_OPPORTUNITY_ID = "opportunity_id"
        const val KEY_TITLE = "title"
        const val KEY_ORG = "org"
    }
}
