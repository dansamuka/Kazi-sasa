package com.kazisasa.app.domain.model

/**
 * One dimension's contribution to a fit score, always paired with a human-readable
 * explanation - spec §8.4: "the app never asserts fit without showing its reasoning."
 *
 * @param rawContribution   unweighted signal in roughly [-1f, 1f]; positive helps,
 *                          negative is a caution. Kept for debugging/tests.
 * @param weightedContribution rawContribution * the profile's weight for this dimension.
 * @param isBlocking        true if this alone should prevent a STRONG/GOOD band
 *                          regardless of the numeric score (e.g. an expired deadline,
 *                          or a seniority gap beyond the profile's stated openness).
 */
data class FitReason(
    val dimension: FitDimension,
    val direction: Direction,
    val rawContribution: Float,
    val weightedContribution: Float,
    val isBlocking: Boolean,
    val explanation: String,
)

/**
 * The full explainability payload for one (opportunity, profile) pair - spec §8.3.
 * The UI leads with [band] + [topReasons] + [cautions], not the raw [score].
 */
data class FitBreakdown(
    val opportunityId: String,
    val profileId: String,
    val score: Int,               // 0..100, used only for sorting
    val band: FitBand,
    val topReasons: List<FitReason>,  // positive/neutral reasons, ranked by |weightedContribution|, max 3
    val cautions: List<FitReason>,    // every CAUTION-direction reason, unranked-truncated (all shown)
)
