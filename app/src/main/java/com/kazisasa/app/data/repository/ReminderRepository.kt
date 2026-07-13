package com.kazisasa.app.data.repository

import com.kazisasa.app.data.local.dao.ReminderDao
import com.kazisasa.app.data.local.entity.ReminderEntity
import com.kazisasa.app.domain.model.Opportunity
import com.kazisasa.app.work.ReminderScheduler
import com.kazisasa.app.work.ScheduleReminderResult
import kotlinx.coroutines.flow.Flow

/** Profile-scoped for the same reason as SavedRepository - see ReminderEntity's doc comment. */
class ReminderRepository(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
) {
    fun observeFor(profileId: String, opportunityId: String): Flow<ReminderEntity?> =
        dao.observeFor(profileId, opportunityId)

    fun hasNotificationPermission(): Boolean = scheduler.hasNotificationPermission()

    suspend fun setReminder(opportunity: Opportunity, profileId: String): ScheduleReminderResult {
        val result = scheduler.schedule(
            profileId = profileId,
            opportunityId = opportunity.id,
            title = opportunity.title,
            organisation = opportunity.organisation.name,
            deadlineMillis = opportunity.deadlineMillis,
        )
        if (result is ScheduleReminderResult.Scheduled) {
            dao.upsert(
                ReminderEntity(
                    profileId = profileId,
                    opportunityId = opportunity.id,
                    remindAtMillis = opportunity.deadlineMillis ?: System.currentTimeMillis(),
                    workRequestId = result.workRequestId,
                    permissionGrantedAtSet = true,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        return result
    }

    suspend fun cancelReminder(profileId: String, opportunityId: String) {
        scheduler.cancel(profileId, opportunityId)
        dao.deleteFor(profileId, opportunityId)
    }
}
