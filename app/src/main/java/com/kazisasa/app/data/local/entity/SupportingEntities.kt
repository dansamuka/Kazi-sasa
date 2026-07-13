package com.kazisasa.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kazisasa.app.domain.model.TriageAction

/**
 * Per (profile, opportunity) triage state - spec §9. This is what makes triage
 * per-profile: skipping a role under "Corporate" must not hide it under "Climate".
 */
@Entity(
    tableName = "triage_state",
    primaryKeys = ["profileId", "opportunityId"],
    indices = [Index("opportunityId"), Index("profileId")],
)
data class TriageStateEntity(
    val profileId: String,
    val opportunityId: String,
    val action: TriageAction,
    val updatedAt: Long,
)

/**
 * Snapshot-on-save (spec §24.5, §7.5): favourites persist even if a later feed
 * refresh drops the listing. [snapshotJson] is a full serialised Opportunity at
 * the moment of saving, so the card still renders with everything it had.
 *
 * Keyed by (profileId, opportunityId), not opportunityId alone (implementation-
 * recommendations doc §11) - a single-key design meant two profiles on the same
 * device would silently share saved favourites, which is wrong: what Josephine
 * saves under her profile has nothing to do with what Gidraf sees under his.
 */
@Entity(
    tableName = "saved_opportunities",
    primaryKeys = ["profileId", "opportunityId"],
    indices = [Index("profileId"), Index("opportunityId")],
)
data class SavedOpportunityEntity(
    val profileId: String,
    val opportunityId: String,
    val savedAt: Long,
    val stillInLiveFeed: Boolean,
    val snapshotJson: String,
)

/**
 * Backed by a scheduled WorkManager job (spec §7.6, §26). Profile-scoped for the
 * same reason as SavedOpportunityEntity above - not called out explicitly in the
 * recommendations doc, but it's the identical bug: without profileId in the key,
 * one profile cancelling a reminder would silently cancel it for every profile
 * sharing the device.
 */
@Entity(
    tableName = "reminders",
    primaryKeys = ["profileId", "opportunityId"],
    indices = [Index("profileId"), Index("opportunityId")],
)
data class ReminderEntity(
    val profileId: String,
    val opportunityId: String,
    val remindAtMillis: Long,
    val workRequestId: String,
    /** For honest UX if permission is later revoked between scheduling and firing. */
    val permissionGrantedAtSet: Boolean,
    val createdAt: Long,
)

/** Singleton row driving the update-status UI (spec §7.7). */
@Entity(tableName = "feed_meta")
data class FeedMetaEntity(
    @PrimaryKey val singletonId: Int = 0,
    val feedVersion: String,
    val generatedAtMillis: Long,
    val nextExpectedUpdateMillis: Long?,
    val lastFetchAtMillis: Long,
    val lastFetchSucceeded: Boolean,
    /** "live" | "cache" | "bundled_seed" - spec §7.7 / §24.1. */
    val dataSource: String,
    /** Feed-maintainer-asserted, independent of dataSource (recommendations doc §20) - a "live" feed can still be flagged as sample/demo content. */
    val isSampleData: Boolean = false,
)
