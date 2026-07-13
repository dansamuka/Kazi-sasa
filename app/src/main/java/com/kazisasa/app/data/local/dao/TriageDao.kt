package com.kazisasa.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kazisasa.app.data.local.entity.TriageStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TriageDao {

    @Upsert
    suspend fun set(state: TriageStateEntity)

    @Query("SELECT * FROM triage_state WHERE profileId = :profileId")
    fun observeFor(profileId: String): Flow<List<TriageStateEntity>>

    @Query("SELECT * FROM triage_state WHERE profileId = :profileId AND action = 'SKIPPED'")
    fun observeSkippedFor(profileId: String): Flow<List<TriageStateEntity>>

    /** "Undo skips" for a profile - recoverable per spec §9. */
    @Query("UPDATE triage_state SET action = 'UNSEEN', updatedAt = :now WHERE profileId = :profileId AND action = 'SKIPPED'")
    suspend fun recoverSkipped(profileId: String, now: Long)

    @Query(
        """SELECT COUNT(*) FROM triage_state
           WHERE profileId = :profileId AND action IN ('KEPT', 'SKIPPED', 'DISMISSED')""",
    )
    suspend fun triagedCountFor(profileId: String): Int
}
