package com.kazisasa.app.domain.model

/**
 * Pure domain representation of a career profile (spec §25). `weights` makes the
 * fit engine tunable per profile without any code change - a user with two
 * profiles (e.g. "Corporate" and "Climate & Development Finance") can weight
 * sector match very differently between the two.
 */
data class Profile(
    val id: String,
    val label: String,
    val targetLanes: List<String>,
    val coreSkills: List<String>,
    val seniority: Seniority,
    /** How many Seniority levels above [seniority] still counts as "stretch" rather than "no". */
    val seniorityOpenness: Int,
    val locationPrefs: LocationPrefs,
    val modePrefs: List<WorkMode>,
    val weights: FitWeights,
    val isDefault: Boolean,
)

data class LocationPrefs(
    val baseRegion: String?,
    val acceptsRemoteKenya: Boolean,
    val acceptsRegional: Boolean,
    val acceptsInternational: Boolean,
    val acceptsRelocation: Boolean,
)

/**
 * Per-dimension importance, each in [0f, 1f]. Used as multipliers in
 * [com.kazisasa.app.domain.fit.FitEngine] - see spec §8.2 for the dimension list.
 */
data class FitWeights(
    val skillMatch: Float = 1.0f,
    val sectorMatch: Float = 0.9f,
    val seniorityMatch: Float = 0.8f,
    val locationFit: Float = 0.6f,
    val modeFit: Float = 0.5f,
    val growthSignal: Float = 0.4f,
    val recency: Float = 0.3f,
    val deadlineRisk: Float = 0.3f,
) {
    /** Sum of all weights - the normalising denominator in the scoring function. */
    fun total(): Float =
        skillMatch + sectorMatch + seniorityMatch + locationFit + modeFit + growthSignal + recency + deadlineRisk
}
