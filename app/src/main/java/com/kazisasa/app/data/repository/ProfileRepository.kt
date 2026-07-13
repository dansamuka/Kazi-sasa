package com.kazisasa.app.data.repository

import com.kazisasa.app.data.datastore.ActiveProfileStore
import com.kazisasa.app.data.local.dao.ProfileDao
import com.kazisasa.app.data.mapper.toDomain
import com.kazisasa.app.data.mapper.toEntity
import com.kazisasa.app.domain.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Spec §7.1: "the user should be able to understand which profile is currently
 * active." [observeActiveProfile] is the single source of truth every screen reads
 * from - switching profiles anywhere updates the feed, triage, and saved badges
 * everywhere, because they all observe this instead of holding local state.
 */
class ProfileRepository(
    private val dao: ProfileDao,
    private val activeProfileStore: ActiveProfileStore,
) {
    fun observeAll(): Flow<List<Profile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Falls back to the first available profile if none has been explicitly chosen yet. */
    fun observeActiveProfile(): Flow<Profile?> =
        combine(dao.observeAll(), activeProfileStore.activeProfileId) { profiles, activeId ->
            val chosen = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
            chosen?.toDomain()
        }

    suspend fun setActiveProfile(profileId: String) = activeProfileStore.setActiveProfileId(profileId)

    /** Backs the weekly digest fallback for denied notification permission (recommendations doc §18). */
    fun observeWeeklyDigestEnabled(): Flow<Boolean> = activeProfileStore.weeklyDigestEnabled

    suspend fun setWeeklyDigestEnabled(enabled: Boolean) = activeProfileStore.setWeeklyDigestEnabled(enabled)

    suspend fun upsert(profile: Profile, sortOrder: Int) = dao.upsert(profile.toEntity(sortOrder))

    suspend fun getById(id: String): Profile? = dao.getById(id)?.toDomain()

    suspend fun hasAnyProfile(): Boolean = dao.count() > 0
}
