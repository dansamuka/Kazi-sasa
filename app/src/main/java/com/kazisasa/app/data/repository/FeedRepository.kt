package com.kazisasa.app.data.repository

import android.content.Context
import com.kazisasa.app.data.local.dao.FeedMetaDao
import com.kazisasa.app.data.local.dao.OpportunityDao
import com.kazisasa.app.data.local.dao.ProfileDao
import com.kazisasa.app.data.local.entity.FeedMetaEntity
import com.kazisasa.app.data.mapper.toEntity
import com.kazisasa.app.data.remote.FeedApiService
import com.kazisasa.app.data.remote.dto.FeedMetaDto
import com.kazisasa.app.data.remote.dto.FeedResponseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant

sealed interface FeedRefreshResult {
    data class Success(val opportunityCount: Int) : FeedRefreshResult
    data class Failed(val reason: String, val usingCache: Boolean) : FeedRefreshResult
}

/**
 * Owns the three-tier fallback from spec §7.7 / §24.1:
 *   1. live fetch from GitHub raw
 *   2. on failure, leave the existing Room cache untouched (already the source
 *      the UI reads from - no special "switch to cache" step needed)
 *   3. on a completely empty cache (first run, or first run with no network),
 *      seed from assets/seed.json so the app is never blank
 *
 * [FeedMetaEntity.dataSource] records which of these actually served the current
 * data, which is what §7.7's "clearly communicate... whether showing cached or
 * bundled data" requirement reads from.
 */
class FeedRepository(
    private val context: Context,
    private val api: FeedApiService,
    private val opportunityDao: OpportunityDao,
    private val profileDao: ProfileDao,
    private val feedMetaDao: FeedMetaDao,
    private val feedUrl: String = FeedApiService.DEFAULT_FEED_URL,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun observeFeedMeta(): Flow<FeedMetaEntity?> = feedMetaDao.observe()

    /** Call on app launch and on manual pull-to-refresh (spec §7.7). Never throws. */
    suspend fun refresh(): FeedRefreshResult {
        val now = System.currentTimeMillis()
        val liveResult = runCatching { api.fetchFeed(feedUrl) }

        val body = liveResult.getOrNull()?.takeIf { it.isSuccessful }?.body()
        if (body != null) {
            applyFeed(body, dataSource = "live", now = now)
            feedMetaDao.upsert(feedMetaFrom(body.meta, now, succeeded = true, dataSource = "live"))
            return FeedRefreshResult.Success(body.opportunities.size)
        }

        // Live fetch failed. If the cache already has rows, we're done - the UI keeps
        // reading Room, which is unchanged. Just record the failed attempt honestly.
        val cachedCount = opportunityDao.count()
        if (cachedCount > 0) {
            val existingMeta = feedMetaDao.get()
            feedMetaDao.upsert(
                (existingMeta ?: emptyMeta(now)).copy(
                    lastFetchAtMillis = now,
                    lastFetchSucceeded = false,
                    dataSource = "cache",
                ),
            )
            return FeedRefreshResult.Failed(reason = describeFailure(liveResult), usingCache = true)
        }

        // Empty cache and no network: fall back to the bundled seed so the app is
        // never blank on first run (spec §7.7, §17 "no opportunities available").
        return runCatching { loadBundledSeed() }
            .onSuccess { seed ->
                applyFeed(seed, dataSource = "bundled_seed", now = now)
                feedMetaDao.upsert(feedMetaFrom(seed.meta, now, succeeded = false, dataSource = "bundled_seed"))
            }
            .fold(
                onSuccess = { FeedRefreshResult.Failed(reason = describeFailure(liveResult), usingCache = true) },
                onFailure = { FeedRefreshResult.Failed(reason = describeFailure(liveResult), usingCache = false) },
            )
    }

    private suspend fun applyFeed(feed: FeedResponseDto, dataSource: String, now: Long) {
        val entities = feed.opportunities.map { it.toEntity(fetchedAtMillis = now) }
        opportunityDao.upsertAll(entities)
        opportunityDao.pruneExcept(entities.map { it.id })

        // Bundled/live default profiles only ever seed the table - they never
        // overwrite a profile the user has already customised (matched by id).
        if (feed.profiles.isNotEmpty() && profileDao.count() == 0) {
            val defaultEntities = feed.profiles.mapIndexed { index, dto ->
                dto.toEntity(sortOrder = index, isDefault = true)
            }
            profileDao.upsertAll(defaultEntities)
        }
    }

    private fun loadBundledSeed(): FeedResponseDto =
        context.assets.open("seed.json").bufferedReader().use { it.readText() }
            .let { json.decodeFromString(FeedResponseDto.serializer(), it) }

    private fun feedMetaFrom(meta: FeedMetaDto, now: Long, succeeded: Boolean, dataSource: String) =
        FeedMetaEntity(
            feedVersion = meta.feedVersion,
            generatedAtMillis = runCatching { Instant.parse(meta.generatedAt).toEpochMilli() }.getOrDefault(now),
            nextExpectedUpdateMillis = meta.nextExpectedUpdate?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            lastFetchAtMillis = now,
            lastFetchSucceeded = succeeded,
            dataSource = dataSource,
            isSampleData = meta.isSampleData,
        )

    private fun emptyMeta(now: Long) = FeedMetaEntity(
        feedVersion = "unknown", generatedAtMillis = now, nextExpectedUpdateMillis = null,
        lastFetchAtMillis = now, lastFetchSucceeded = false, dataSource = "cache", isSampleData = false,
    )

    private fun describeFailure(result: Result<retrofit2.Response<FeedResponseDto>>): String =
        result.fold(
            onSuccess = { resp -> "Server returned ${resp.code()}" },
            onFailure = { e -> e.message ?: e::class.simpleName ?: "Unknown network error" },
        )
}
