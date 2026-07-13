package com.kazisasa.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kazisasa.app.data.local.entity.OpportunityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OpportunityDao {

    @Query("SELECT * FROM opportunities ORDER BY postedAtMillis DESC")
    fun observeAll(): Flow<List<OpportunityEntity>>

    @Query("SELECT * FROM opportunities WHERE id = :id")
    suspend fun getById(id: String): OpportunityEntity?

    @Query("SELECT * FROM opportunities WHERE id = :id")
    fun observeById(id: String): Flow<OpportunityEntity?>

    @Upsert
    suspend fun upsertAll(items: List<OpportunityEntity>)

    /**
     * Opportunities this profile hasn't triaged yet (spec §9: state starts UNSEEN).
     * A LEFT JOIN so opportunities with no triage_state row at all still show up.
     */
    @Query(
        """SELECT o.* FROM opportunities o
           LEFT JOIN triage_state t
             ON t.opportunityId = o.id AND t.profileId = :profileId
           WHERE t.action IS NULL OR t.action = 'UNSEEN'
           ORDER BY o.postedAtMillis DESC""",
    )
    fun observeUntriagedFor(profileId: String): Flow<List<OpportunityEntity>>

    /**
     * The Shortlist screen (recommendations doc §10): everything this profile has
     * kept, including everything saved (saving always implies KEPT - see
     * SavedRepository.save). An INNER JOIN here is correct, not a bug: unlike the
     * untriaged query, we only want rows that *do* have a matching triage_state.
     */
    @Query(
        """SELECT o.* FROM opportunities o
           INNER JOIN triage_state t ON t.opportunityId = o.id AND t.profileId = :profileId
           WHERE t.action = 'KEPT'
           ORDER BY t.updatedAt DESC""",
    )
    fun observeKeptFor(profileId: String): Flow<List<OpportunityEntity>>

    /** The Skipped/recovery screen (recommendations doc §14). */
    @Query(
        """SELECT o.* FROM opportunities o
           INNER JOIN triage_state t ON t.opportunityId = o.id AND t.profileId = :profileId
           WHERE t.action = 'SKIPPED'
           ORDER BY t.updatedAt DESC""",
    )
    fun observeSkippedFor(profileId: String): Flow<List<OpportunityEntity>>

    /**
     * Refresh keeps rows still referenced by a save or a live reminder even if the
     * upstream feed dropped them - see spec §24.5 (snapshot-on-save covers the
     * *saved card itself*; this keeps the live cache row consistent with it too).
     */
    @Query(
        """DELETE FROM opportunities
           WHERE id NOT IN (:keepIds)
           AND id NOT IN (SELECT opportunityId FROM saved_opportunities)
           AND id NOT IN (SELECT opportunityId FROM reminders)""",
    )
    suspend fun pruneExcept(keepIds: List<String>)

    @Query("SELECT COUNT(*) FROM opportunities")
    suspend fun count(): Int
}
