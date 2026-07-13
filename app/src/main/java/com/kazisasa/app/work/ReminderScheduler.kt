package com.kazisasa.app.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

sealed interface ScheduleReminderResult {
    data class Scheduled(val workRequestId: String) : ScheduleReminderResult
    object PermissionDenied : ScheduleReminderResult
    object DeadlineTooSoon : ScheduleReminderResult
    object NoDeadline : ScheduleReminderResult
}

/**
 * Spec §7.6 requires the design to account for: permission granted, permission
 * denied, a deadline too close to be useful, no known deadline, and an already-
 * expired opportunity. Each of those is a distinct return value here rather than
 * a single boolean, so the UI can show the right message for each case instead of
 * a generic failure.
 *
 * Tags (and therefore cancellation) are scoped by profileId + opportunityId, not
 * opportunityId alone - see ReminderEntity's doc comment for why.
 */
class ReminderScheduler(private val context: Context) {

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Schedules a reminder [leadTimeMillis] before [deadlineMillis]. Defaults to a
     * day's notice; callers may pass a shorter lead time for near deadlines, but if
     * even "now" would be after the deadline this returns [ScheduleReminderResult.DeadlineTooSoon].
     *
     * Does NOT request notification permission itself (implementation-recommendations
     * doc §16: permission should only be requested from inside the reminder flow, not
     * on app launch) - it only checks the *current* state. The caller (the detail
     * screen) is responsible for requesting permission first if this returns
     * [ScheduleReminderResult.PermissionDenied], then calling schedule() again.
     */
    fun schedule(
        profileId: String,
        opportunityId: String,
        title: String,
        organisation: String,
        deadlineMillis: Long?,
        leadTimeMillis: Long = TimeUnit.DAYS.toMillis(1),
        nowMillis: Long = System.currentTimeMillis(),
    ): ScheduleReminderResult {
        if (deadlineMillis == null) return ScheduleReminderResult.NoDeadline
        if (!hasNotificationPermission()) return ScheduleReminderResult.PermissionDenied

        val fireAt = deadlineMillis - leadTimeMillis
        val delay = (fireAt - nowMillis).coerceAtLeast(0)
        if (deadlineMillis <= nowMillis || fireAt <= nowMillis) {
            // Still schedule a near-immediate nudge rather than silently doing nothing,
            // unless the deadline itself has already passed.
            if (deadlineMillis <= nowMillis) return ScheduleReminderResult.DeadlineTooSoon
        }

        val data = Data.Builder()
            .putString(ReminderWorker.KEY_PROFILE_ID, profileId)
            .putString(ReminderWorker.KEY_OPPORTUNITY_ID, opportunityId)
            .putString(ReminderWorker.KEY_TITLE, title)
            .putString(ReminderWorker.KEY_ORG, organisation)
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(tagFor(profileId, opportunityId))
            .build()

        WorkManager.getInstance(context).enqueue(request)
        return ScheduleReminderResult.Scheduled(request.id.toString())
    }

    fun cancel(profileId: String, opportunityId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tagFor(profileId, opportunityId))
    }

    private fun tagFor(profileId: String, opportunityId: String) = "reminder_${profileId}_$opportunityId"
}
