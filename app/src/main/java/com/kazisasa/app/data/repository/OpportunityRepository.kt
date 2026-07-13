package com.kazisasa.app.data.repository

import com.kazisasa.app.data.local.dao.OpportunityDao
import com.kazisasa.app.data.mapper.toDomain
import com.kazisasa.app.domain.fit.FitEngine
import com.kazisasa.app.domain.model.FitBreakdown
import com.kazisasa.app.domain.model.Opportunity
import com.kazisasa.app.domain.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One opportunity plus its explainability payload for a given profile - what the
 * feed screen actually renders.
 *
 * [fit] is nullable: spec §8.4 says the app must never assert fit without a real
 * (opportunity, profile) basis to explain it, and FitBreakdown's own doc comment
 * says the same. Browsing without an active profile (the default v3 "no profile"
 * experience - see FeedViewModel/FeedScreen) is real, but it has no profile to
 * score against, so [fit] is genuinely null there - not a fabricated neutral
 * score. UI code must handle fit == null gracefully (hide the fit chip/reasons,
 * don't crash) rather than assuming it's always present.
 */
data class ScoredOpportunity(val opportunity: Opportunity, val fit: FitBreakdown?)

/**
 * Translates Room's OpportunityEntity rows into domain Opportunity objects and, for
 * the feed screen, runs them through [FitEngine] against the active profile. Fit is
 * computed here at read time rather than stored (spec §8.1) - cheap for feed-sized
 * lists and guarantees the score can never drift out of sync with the profile.
 */
class OpportunityRepository(
    private val dao: OpportunityDao,
    private val fitEngine: FitEngine,
) {
    fun observeAll(): Flow<List<Opportunity>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeById(id: String): Flow<Opportunity?> =
        dao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: String): Opportunity? = dao.getById(id)?.toDomain()

    /**
     * The untriaged feed for [profile], scored and sorted best-first. This is the
     * primary data source for the triage screen (spec §9) - each item here still
     * has TriageAction.UNSEEN for this profile.
     */
    fun observeScoredUntriagedFeed(profile: Profile): Flow<List<ScoredOpportunity>> =
        dao.observeUntriagedFor(profile.id).map { entities ->
            entities
                .map { it.toDomain() }
                .map { ScoredOpportunity(it, fitEngine.score(it, profile)) }
                // fit is provably non-null here (just constructed above on this line),
                // even though ScoredOpportunity.fit is nullable for the no-profile
                // browse path below - !! is safe and intentional at this exact spot.
                .sortedWith(
                    compareBy<ScoredOpportunity> { it.fit!!.band.ordinal } // STRONG(0) before GOOD(1) before STRETCH(2)
                        .thenByDescending { it.fit!!.score },
                )
        }

    /**
     * The Shortlist screen (recommendations doc §10): everything KEPT for this
     * profile, still scored so the "why" doesn't disappear once something moves
     * off the main feed - a user reviewing their shortlist a week later shouldn't
     * lose the reasoning that made them keep it in the first place.
     */
    fun observeScoredKeptFor(profile: Profile): Flow<List<ScoredOpportunity>> =
        dao.observeKeptFor(profile.id).map { entities ->
            entities
                .map { it.toDomain() }
                .map { ScoredOpportunity(it, fitEngine.score(it, profile)) }
                .sortedWith(compareBy<ScoredOpportunity> { it.fit!!.band.ordinal }.thenByDescending { it.fit!!.score })
        }

    /**
     * v3 "no profile" default browse mode (general-search spec §7.4/§2.3): search
     * and the main feed must be fully functional with zero profile set up - fit
     * scoring is a personalisation *enhancement* on top of a working feed, not a
     * gate in front of one. Newest-first (dao.observeAll() is already ordered
     * that way) since there's no profile to rank relevance against. [fit] is
     * null on every item here - see ScoredOpportunity's doc comment for why that's
     * the honest representation rather than a fabricated neutral score.
     */
    fun observeBrowseFeed(): Flow<List<ScoredOpportunity>> =
        dao.observeAll().map { entities -> entities.map { ScoredOpportunity(it.toDomain(), fit = null) } }

    /** The Skipped/recovery screen (recommendations doc §14) - unscored, since the point here is recovery, not ranking. */
    fun observeSkippedFor(profileId: String): Flow<List<Opportunity>> =
        dao.observeSkippedFor(profileId).map { entities -> entities.map { it.toDomain() } }

    fun scoreFor(opportunity: Opportunity, profile: Profile): FitBreakdown = fitEngine.score(opportunity, profile)
}
