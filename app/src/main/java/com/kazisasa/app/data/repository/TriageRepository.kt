package com.kazisasa.app.data.repository

import com.kazisasa.app.data.local.dao.TriageDao
import com.kazisasa.app.data.local.entity.TriageStateEntity
import com.kazisasa.app.domain.model.TriageAction
import kotlinx.coroutines.flow.Flow

/**
 * Spec §9: per (profile, opportunity) state, UNSEEN -> KEPT | SKIPPED | SAVED | DISMISSED.
 * Triage is intentionally *not* a one-way ratchet - [recoverSkipped] exists because
 * the spec requires skips to be recoverable, not a silent, permanent hide.
 */
class TriageRepository(private val dao: TriageDao) {

    fun observeFor(profileId: String): Flow<List<TriageStateEntity>> = dao.observeFor(profileId)

    suspend fun setAction(profileId: String, opportunityId: String, action: TriageAction) {
        dao.set(
            TriageStateEntity(
                profileId = profileId,
                opportunityId = opportunityId,
                action = action,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun keep(profileId: String, opportunityId: String) = setAction(profileId, opportunityId, TriageAction.KEPT)
    suspend fun skip(profileId: String, opportunityId: String) = setAction(profileId, opportunityId, TriageAction.SKIPPED)
    suspend fun dismiss(profileId: String, opportunityId: String) = setAction(profileId, opportunityId, TriageAction.DISMISSED)

    suspend fun recoverSkipped(profileId: String) = dao.recoverSkipped(profileId, System.currentTimeMillis())

    suspend fun triagedCount(profileId: String): Int = dao.triagedCountFor(profileId)
}
