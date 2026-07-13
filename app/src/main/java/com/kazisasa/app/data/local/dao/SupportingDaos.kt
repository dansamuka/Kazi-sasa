package com.kazisasa.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kazisasa.app.data.local.entity.FeedMetaEntity
import com.kazisasa.app.data.local.entity.ReminderEntity
import com.kazisasa.app.data.local.entity.SavedOpportunityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedOpportunityDao {

    /** Profile-scoped (recommendations doc §11) - never a cross-profile list. */
    @Query("SELECT * FROM saved_opportunities WHERE profileId = :profileId ORDER BY savedAt DESC")
    fun observeAllFor(profileId: String): Flow<List<SavedOpportunityEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_opportunities WHERE profileId = :profileId AND opportunityId = :opportunityId)")
    fun observeIsSaved(profileId: String, opportunityId: String): Flow<Boolean>

    @Upsert
    suspend fun save(entity: SavedOpportunityEntity)

    @Query("DELETE FROM saved_opportunities WHERE profileId = :profileId AND opportunityId = :opportunityId")
    suspend fun unsave(profileId: String, opportunityId: String)

    /** Marks a saved snapshot as no longer present in the live feed (spec §17), across every profile that saved it. */
    @Query("UPDATE saved_opportunities SET stillInLiveFeed = :stillPresent WHERE opportunityId = :opportunityId")
    suspend fun setStillInLiveFeed(opportunityId: String, stillPresent: Boolean)

    @Query("SELECT DISTINCT opportunityId FROM saved_opportunities")
    suspend fun allSavedOpportunityIds(): List<String>
}

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE profileId = :profileId")
    fun observeAllFor(profileId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE profileId = :profileId AND opportunityId = :opportunityId")
    fun observeFor(profileId: String, opportunityId: String): Flow<ReminderEntity?>

    @Upsert
    suspend fun upsert(entity: ReminderEntity)

    @Delete
    suspend fun delete(entity: ReminderEntity)

    @Query("DELETE FROM reminders WHERE profileId = :profileId AND opportunityId = :opportunityId")
    suspend fun deleteFor(profileId: String, opportunityId: String)
}

@Dao
interface FeedMetaDao {

    @Query("SELECT * FROM feed_meta WHERE singletonId = 0")
    fun observe(): Flow<FeedMetaEntity?>

    @Query("SELECT * FROM feed_meta WHERE singletonId = 0")
    suspend fun get(): FeedMetaEntity?

    @Upsert
    suspend fun upsert(entity: FeedMetaEntity)
}
