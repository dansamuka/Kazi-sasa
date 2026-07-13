package com.kazisasa.app.domain.fit

import com.kazisasa.app.domain.model.FitBreakdown
import com.kazisasa.app.domain.model.Opportunity
import com.kazisasa.app.domain.model.Profile

/**
 * Computes fit **on the device**, deliberately - spec §8.1:
 *   - the feed stays profile-agnostic and small (attributes only, no per-user scores)
 *   - scoring is transparent and auditable - no black-box ranking, no fabricated confidence
 *   - it works fully offline, against cached data
 *
 * Implementations must be pure functions of their inputs: same (opportunity, profile)
 * always yields the same FitBreakdown. This is what makes it unit-testable without a
 * device, a database, or the network - see FitEngineTest.
 */
interface FitEngine {
    fun score(opportunity: Opportunity, profile: Profile, nowMillis: Long = System.currentTimeMillis()): FitBreakdown
}
