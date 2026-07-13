package com.kazisasa.app.data.repository

import com.kazisasa.app.domain.model.ContractType
import com.kazisasa.app.domain.model.Opportunity
import com.kazisasa.app.domain.model.Seniority
import com.kazisasa.app.domain.model.WorkMode

/**
 * Search/filter state for the feed screen - works identically whether browsing
 * without a profile or viewing the profile-scored feed (general-search spec
 * §7.4: filters are a feed-level concern, not a profile-scoring concern, so
 * they apply the same way in both modes).
 *
 * Structure mirrors the LinkedIn job-search categories people already know
 * (keyword + location search bar, then Date posted / Experience level /
 * Job type / Remote / Company / Industry filters) - same structural mirror
 * already built for the shareable web site (kazi-sasa-feed's
 * scripts/site/app.js), kept in sync here so the app and the web preview
 * behave the same way. Every option is a relabeling of real fields already
 * in the feed (seniority, contract_type, work_mode, opportunity_type) - see
 * [jobTypeValue]/[remoteValue] below, nothing here is fabricated.
 *
 * All fields are additive (AND'd together) - an empty set/blank string for any
 * field means "no constraint from this facet", not "match nothing".
 */
data class FeedFilters(
    val keyword: String = "",
    val location: String = "",
    val datePostedDays: Int? = null, // null = any time; 1/7/30 = past 24h/week/month, mirrors the web version's 24h/week/month options
    val experience: Set<Seniority> = emptySet(),
    val jobType: Set<String> = emptySet(), // values from jobTypeValue() below
    val remote: Set<String> = emptySet(), // "onsite" | "hybrid" | "remote"
    val company: Set<String> = emptySet(),
    val industry: Set<String> = emptySet(),
    val sortBy: SortOption = SortOption.DEFAULT,
) {
    val isActive: Boolean
        get() = keyword.isNotBlank() || location.isNotBlank() || datePostedDays != null ||
            experience.isNotEmpty() || jobType.isNotEmpty() || remote.isNotEmpty() ||
            company.isNotEmpty() || industry.isNotEmpty()

    val activeFacetCount: Int
        get() = listOf(
            keyword.isNotBlank(),
            location.isNotBlank(),
            datePostedDays != null,
            experience.isNotEmpty(),
            jobType.isNotEmpty(),
            remote.isNotEmpty(),
            company.isNotEmpty(),
            industry.isNotEmpty(),
        ).count { it }
}

/**
 * Sort is a separate concern from filtering (same split as the web version's
 * own sort dropdown, distinct from its filter pills) - bundled into the same
 * state object purely because the ViewModel already threads FeedFilters
 * through as the one source of truth for "how the list should currently look".
 *
 * DEFAULT deliberately isn't labelled "Most relevant" anywhere in the UI -
 * there's no real relevance ranking backing the unfiltered order on the
 * no-profile browse path (see kazi-sasa-feed's app.js for the same reasoning,
 * word-for-word - keeping this consistent between web and app was
 * deliberate). When a profile is active, the untriaged feed IS genuinely
 * fit-ranked already (see OpportunityRepository.observeScoredUntriagedFeed's
 * band+score sort) - DEFAULT there means "keep that real ranking", not
 * "no ranking at all". Only in browse mode is DEFAULT truly just collection
 * order.
 */
enum class SortOption { DEFAULT, MOST_RECENT }

/**
 * Mirrors kazi-sasa-feed/scripts/site/app.js's jobTypeValue() exactly -
 * internship/fellowship/grant/programme carry through as their own opportunity
 * type; a plain "job" derives its label from contract_type instead, since
 * that's the more specific signal LinkedIn's own "Job type" filter actually
 * reflects (Full-time/Part-time/Contract/Temporary/Volunteer).
 */
fun jobTypeValue(o: Opportunity): String? {
    val type = o.opportunityType.name.lowercase()
    if (type != "job") return type
    return when (o.contractType) {
        ContractType.PERMANENT -> "full_time"
        ContractType.CONTRACT -> "contract"
        ContractType.PART_TIME -> "part_time"
        ContractType.FIXED_TERM -> "temporary"
        ContractType.CONSULTANT -> "contract"
        ContractType.VOLUNTEER -> "volunteer"
        ContractType.UNKNOWN, null -> null
    }
}

/**
 * Mirrors kazi-sasa-feed/scripts/site/app.js's remoteValue() exactly -
 * remote_kenya and remote_global both collapse to a single "remote" bucket,
 * same as the web version, since most people filtering "Remote" don't care
 * about that distinction the way the fit engine does.
 */
fun remoteValue(o: Opportunity): String? = when (o.workMode) {
    WorkMode.ONSITE -> "onsite"
    WorkMode.HYBRID -> "hybrid"
    // REMOTE_KENYA/REMOTE_REGIONAL/REMOTE_GLOBAL all collapse to one "remote"
    // bucket for filtering purposes, same as the web version's remoteValue()
    // - most people filtering "Remote" don't care about the kenya/regional/
    // global distinction the way the fit engine does. Missing REMOTE_REGIONAL
    // here was a real compile error (non-exhaustive when) caught by the first
    // live CI run - WorkMode has five values, this originally only handled four.
    WorkMode.REMOTE_KENYA, WorkMode.REMOTE_REGIONAL, WorkMode.REMOTE_GLOBAL -> "remote"
    null -> null
}

private fun withinDatePosted(o: Opportunity, days: Int?): Boolean {
    if (days == null) return true
    val posted = o.postedAtMillis ?: return false // can't verify recency - exclude rather than guess, same honesty principle as the web version
    val cutoffMillis = days.toLong() * 24 * 60 * 60 * 1000
    return (System.currentTimeMillis() - posted) <= cutoffMillis
}

/**
 * Applies [filters] to a list of scored opportunities. Pure function, no Room/
 * Android dependency - trivially unit-testable (see FeedFiltersTest.kt) and
 * usable identically from the browse-mode item list and the profile-scored
 * item list, since both are `List<ScoredOpportunity>`.
 *
 * Client-side filtering over the already-loaded feed rather than a DAO-level
 * SQL rewrite - the full feed is a few hundred to a couple thousand rows at
 * most (bundled/cached locally already), so this is well within what a simple
 * in-memory filter handles instantly; a SQL-level rewrite would only start to
 * matter at a scale this app isn't at.
 */
fun List<ScoredOpportunity>.applyFilters(filters: FeedFilters): List<ScoredOpportunity> {
    val keyword = filters.keyword.trim()
    val location = filters.location.trim()

    val filtered = if (!filters.isActive) this else filter { scored ->
        val o = scored.opportunity

        val matchesKeyword = keyword.isBlank() ||
            o.title.contains(keyword, ignoreCase = true) ||
            o.organisation.name.contains(keyword, ignoreCase = true) ||
            (o.summary?.contains(keyword, ignoreCase = true) ?: false) ||
            (o.industry?.contains(keyword, ignoreCase = true) ?: false) ||
            o.specialisations.any { it.contains(keyword, ignoreCase = true) }

        val matchesLocation = location.isBlank() ||
            (o.location.raw?.contains(location, ignoreCase = true) ?: false) ||
            (o.location.country?.contains(location, ignoreCase = true) ?: false)

        val matchesDate = withinDatePosted(o, filters.datePostedDays)
        val matchesExperience = filters.experience.isEmpty() || o.seniority in filters.experience
        val matchesJobType = filters.jobType.isEmpty() || jobTypeValue(o) in filters.jobType
        val matchesRemote = filters.remote.isEmpty() || remoteValue(o) in filters.remote
        val matchesCompany = filters.company.isEmpty() || o.organisation.name in filters.company
        val matchesIndustry = filters.industry.isEmpty() || o.industry in filters.industry

        matchesKeyword && matchesLocation && matchesDate && matchesExperience &&
            matchesJobType && matchesRemote && matchesCompany && matchesIndustry
    }

    return when (filters.sortBy) {
        SortOption.DEFAULT -> filtered
        SortOption.MOST_RECENT -> filtered.sortedByDescending { it.opportunity.postedAtMillis ?: Long.MIN_VALUE }
    }
}

/**
 * Distinct, non-null facet values actually present in [this] list, for
 * populating filter dropdown options dynamically - never hardcode a taxonomy
 * list in the UI layer, since the feed's real content should drive what's
 * offered (recommendations doc's general "don't offer a filter option with
 * zero matching results" principle). Deliberately built from the full
 * unfiltered item set (not the currently-filtered one) so picking one pill's
 * option doesn't make another pill's options disappear.
 */
data class AvailableFacets(
    val experience: List<Seniority>,
    val jobType: List<String>,
    val remote: List<String>,
    val company: List<String>,
    val industry: List<String>,
) {
    companion object {
        fun from(items: List<ScoredOpportunity>): AvailableFacets = AvailableFacets(
            experience = items.mapNotNull { it.opportunity.seniority }.distinct().sortedBy { it.ordinal },
            jobType = items.mapNotNull { jobTypeValue(it.opportunity) }.distinct().sorted(),
            remote = items.mapNotNull { remoteValue(it.opportunity) }.distinct().sorted(),
            company = items.map { it.opportunity.organisation.name }.distinct().sorted(),
            industry = items.mapNotNull { it.opportunity.industry }.distinct().sorted(),
        )
    }
}
