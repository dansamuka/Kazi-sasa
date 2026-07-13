package com.kazisasa.app.data.repository

import com.kazisasa.app.data.local.dao.OpportunityDao
import com.kazisasa.app.data.local.dao.SavedOpportunityDao
import com.kazisasa.app.data.local.dao.TriageDao
import com.kazisasa.app.data.local.entity.SavedOpportunityEntity
import com.kazisasa.app.data.local.entity.TriageStateEntity
import com.kazisasa.app.data.mapper.fromSnapshotJsonToOpportunity
import com.kazisasa.app.data.mapper.toSnapshotJson
import com.kazisasa.app.domain.model.Opportunity
import com.kazisasa.app.domain.model.TriageAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A saved item, flagged if it has fallen out of the live feed (spec §17: "no longer in live feed - verify at source"). */
data class SavedItem(val opportunity: Opportunity, val savedAtMillis: Long, val stillInLiveFeed: Boolean)

/**
 * Saving and triage are deliberately two different concerns now (implementation-
 * recommendations doc §12, "preferred solution"): this repository owns the
 * saved_opportunities table only, and always keeps triage_state in sync with it
 * rather than encoding "saved" as a triage value.
 *
 *   save   -> writes the snapshot AND sets triage to KEPT (a save always counts
 *             as keeping - there's no such thing as a saved-but-not-kept item)
 *   unsave -> deletes the snapshot but leaves triage at KEPT, not UNSEEN (doc §13:
 *             "the user already showed some interest, so it should not return
 *             as a completely new item") - it drops out of Saved but stays
 *             visible in the Shortlist.
 */
class SavedRepository(
    private val savedDao: SavedOpportunityDao,
    private val opportunityDao: OpportunityDao,
    private val triageDao: TriageDao,
) {
    fun observeAllFor(profileId: String): Flow<List<SavedItem>> =
        savedDao.observeAllFor(profileId).map { rows ->
            rows.map { SavedItem(it.snapshotJson.fromSnapshotJsonToOpportunity(), it.savedAt, it.stillInLiveFeed) }
        }

    fun observeIsSaved(profileId: String, opportunityId: String): Flow<Boolean> =
        savedDao.observeIsSaved(profileId, opportunityId)

    suspend fun save(opportunity: Opportunity, profileId: String) {
        val now = System.currentTimeMillis()
        savedDao.save(
            SavedOpportunityEntity(
                profileId = profileId,
                opportunityId = opportunity.id,
                savedAt = now,
                stillInLiveFeed = true,
                snapshotJson = opportunity.toSnapshotJson(),
            ),
        )
        triageDao.set(TriageStateEntity(profileId, opportunity.id, TriageAction.KEPT, now))
    }

    suspend fun unsave(profileId: String, opportunityId: String) {
        savedDao.unsave(profileId, opportunityId)
        triageDao.set(TriageStateEntity(profileId, opportunityId, TriageAction.KEPT, System.currentTimeMillis()))
    }

    /** Called after a feed refresh to keep the "no longer live" badge accurate (spec §17). Checked across all profiles at once. */
    suspend fun reconcileWithLiveFeed(liveOpportunityIds: Set<String>) {
        savedDao.allSavedOpportunityIds().forEach { id -> savedDao.setStillInLiveFeed(id, id in liveOpportunityIds) }
    }
}
