@file:OptIn(ExperimentalCoroutinesApi::class)

package com.kazisasa.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazisasa.app.data.local.entity.FeedMetaEntity
import com.kazisasa.app.data.repository.AvailableFacets
import com.kazisasa.app.data.repository.FeedFilters
import com.kazisasa.app.data.repository.FeedRefreshResult
import com.kazisasa.app.data.repository.FeedRepository
import com.kazisasa.app.data.repository.OpportunityRepository
import com.kazisasa.app.data.repository.ProfileRepository
import com.kazisasa.app.data.repository.SavedRepository
import com.kazisasa.app.data.repository.ScoredOpportunity
import com.kazisasa.app.data.repository.TriageRepository
import com.kazisasa.app.data.repository.applyFilters
import com.kazisasa.app.domain.model.FitBand
import com.kazisasa.app.domain.model.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything that depends only on the feed itself, before digest prefs are folded in. */
private data class FeedCoreState(
    val isRefreshing: Boolean,
    val allItems: List<ScoredOpportunity>,
    val filteredItems: List<ScoredOpportunity>,
    val feedMeta: FeedMetaEntity?,
    val refreshErrorMessage: String?,
)

data class FeedUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val activeProfile: Profile? = null,
    val items: List<ScoredOpportunity> = emptyList(),
    val allItemCount: Int = 0,
    val filters: FeedFilters = FeedFilters(),
    val availableFacets: AvailableFacets = AvailableFacets(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
    val feedMeta: FeedMetaEntity? = null,
    val refreshErrorMessage: String? = null,
    val digestEnabled: Boolean = true,
    val digestDismissed: Boolean = false,
    val allItemsForDigest: List<ScoredOpportunity> = emptyList(),
) {
    /** recommendations doc §7.7/§29/§20: the app must say plainly when it's showing bundled demo data, not live listings - true if the app *fell back* to the bundled asset, or if the feed maintainer explicitly flagged the served feed as sample/demo content. */
    val isSampleData: Boolean get() = feedMeta?.dataSource == "bundled_seed" || feedMeta?.isSampleData == true

    /**
     * Weekly digest (recommendations doc §18) - the fallback re-engagement path
     * for anyone who denied notification permission. Rather than a separate
     * "new since last visit" tracking mechanism, this is simply the strongest
     * untriaged matches right now: the moment something is acted on (kept,
     * skipped, saved) it leaves the untriaged feed and naturally drops out of
     * the digest too, so the digest always reflects what's still worth a look.
     *
     * v3: the safe call on `it.fit?.band` naturally empties this list when
     * browsing without a profile (fit == null for every item there) - correct,
     * since "strongest matches" is inherently a personalisation feature with
     * nothing to say when there's no profile to match against.
     *
     * Deliberately computed from [allItemsForDigest] (the unfiltered feed), not
     * the currently-filtered [items] - your strongest matches shouldn't
     * disappear from the digest just because you're mid-search for something
     * else right now.
     */
    val digestItems: List<ScoredOpportunity> get() = allItemsForDigest.filter { it.fit?.band == FitBand.STRONG }.take(5)
}

/**
 * [ExperimentalCoroutinesApi] is required for [flatMapLatest] - kotlinx.coroutines
 * has kept it under this marker for years even though it's widely used in
 * production. Opted in at the file level rather than suppressed silently, so
 * anyone reading this file sees exactly why.
 */
class FeedViewModel(
    private val profileRepository: ProfileRepository,
    private val opportunityRepository: OpportunityRepository,
    private val triageRepository: TriageRepository,
    private val savedRepository: SavedRepository,
    private val feedRepository: FeedRepository,
) : ViewModel() {

    private val transientError = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val digestDismissed = MutableStateFlow(false)
    private val filters = MutableStateFlow(FeedFilters())

    /**
     * [flatMapLatest] on the active profile means switching profiles automatically
     * re-subscribes to a freshly-scored, freshly-filtered feed for the new profile -
     * spec §7.1: switching profiles re-scores and re-triages the feed against that
     * profile's weights. No manual "refresh on switch" call needed anywhere.
     *
     * v3 addition: when there is no active profile, this now subscribes to
     * [OpportunityRepository.observeBrowseFeed] instead of short-circuiting to an
     * empty state - general-search spec §7.4/§2.3: search and the main feed must
     * be fully functional with zero profile set up. A profile is a personalisation
     * *enhancement*, never a gate in front of a working feed. [FeedUiState.items]
     * genuinely has content with fit == null in this mode; see ScoredOpportunity's
     * doc comment for why null (not a fabricated neutral score) is the honest
     * representation.
     *
     * v3 addition: [filters] applies identically whether browsing or viewing the
     * profile-scored feed (search/filter is a feed-level concern, not a scoring
     * concern) - see FeedFilters.kt.
     *
     * Built as two nested typed `combine` calls (5-arg, then 3-arg) rather than one
     * large vararg `combine`, deliberately - the vararg overload only stays type-safe
     * when every flow shares one type, which isn't true here, and this file's
     * Android dependencies mean it can't be compile-checked outside Android Studio.
     * Nesting typed combines keeps every value statically typed, no casts anywhere.
     */
    val uiState: StateFlow<FeedUiState> = profileRepository.observeActiveProfile()
        .flatMapLatest { profile ->
            val itemsFlow = if (profile == null) {
                opportunityRepository.observeBrowseFeed()
            } else {
                opportunityRepository.observeScoredUntriagedFeed(profile)
            }

            val core = combine(
                itemsFlow,
                feedRepository.observeFeedMeta(),
                refreshing,
                transientError,
                filters,
            ) { items, meta, isRefreshing, error, currentFilters ->
                FeedCoreState(isRefreshing, items, items.applyFilters(currentFilters), meta, error)
            }

            combine(
                core,
                profileRepository.observeWeeklyDigestEnabled(),
                digestDismissed,
            ) { c, digestEnabled, dismissed ->
                FeedUiState(
                    isLoading = false,
                    isRefreshing = c.isRefreshing,
                    activeProfile = profile,
                    items = c.filteredItems,
                    allItemCount = c.allItems.size,
                    filters = filters.value,
                    availableFacets = AvailableFacets.from(c.allItems),
                    feedMeta = c.feedMeta,
                    refreshErrorMessage = c.refreshErrorMessage,
                    digestEnabled = digestEnabled,
                    digestDismissed = dismissed,
                    allItemsForDigest = c.allItems,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeedUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            transientError.value = null
            when (val result = feedRepository.refresh()) {
                is FeedRefreshResult.Failed -> {
                    transientError.value = if (result.usingCache) {
                        "Showing saved data - couldn't refresh (${result.reason})"
                    } else {
                        "Couldn't load opportunities: ${result.reason}"
                    }
                }
                is FeedRefreshResult.Success -> Unit
            }
            refreshing.value = false
        }
    }

    fun keep(opportunityId: String) = act { profileId -> triageRepository.keep(profileId, opportunityId) }
    fun skip(opportunityId: String) = act { profileId -> triageRepository.skip(profileId, opportunityId) }

    fun save(scored: ScoredOpportunity) = viewModelScope.launch {
        val profileId = uiState.value.activeProfile?.id
        if (profileId == null) {
            // Saving is genuinely tied to a profile in the current schema (see
            // SavedRepository.save's profileId parameter) - full profile-independent
            // save would need a schema change, out of scope for this pass. Until
            // then, give honest feedback instead of a silent no-op when tapped
            // during no-profile browsing.
            transientError.value = "Create a profile to save opportunities - browsing and search work without one, saving doesn't yet."
            return@launch
        }
        savedRepository.save(scored.opportunity, profileId)
    }

    fun recoverSkipped() = act { profileId -> triageRepository.recoverSkipped(profileId) }

    fun dismissDigest() { digestDismissed.value = true }

    fun setDigestEnabled(enabled: Boolean) = viewModelScope.launch {
        profileRepository.setWeeklyDigestEnabled(enabled)
    }

    /**
     * v3 addition: search/filter, works identically in browse and profile-scored
     * modes (see FeedFilters.kt / this file's uiState doc comment). The UI passes
     * a full new [FeedFilters] each call (built from its own local state) rather
     * than exposing per-facet setters here - keeps this ViewModel from needing to
     * know about individual UI widgets.
     */
    fun setFilters(newFilters: FeedFilters) {
        filters.value = newFilters
    }

    fun clearFilters() {
        filters.value = FeedFilters()
    }

    private fun act(block: suspend (profileId: String) -> Unit) {
        viewModelScope.launch {
            val profileId = uiState.value.activeProfile?.id ?: return@launch
            block(profileId)
        }
    }
}
