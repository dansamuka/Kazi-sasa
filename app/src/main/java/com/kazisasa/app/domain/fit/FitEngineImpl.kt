package com.kazisasa.app.domain.fit

import com.kazisasa.app.domain.model.Direction
import com.kazisasa.app.domain.model.FitBand
import com.kazisasa.app.domain.model.FitBreakdown
import com.kazisasa.app.domain.model.FitDimension
import com.kazisasa.app.domain.model.FitReason
import com.kazisasa.app.domain.model.Opportunity
import com.kazisasa.app.domain.model.OpportunityFlag
import com.kazisasa.app.domain.model.OpportunityType
import com.kazisasa.app.domain.model.Profile
import com.kazisasa.app.domain.model.SourceConfidence
import com.kazisasa.app.domain.model.WorkMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Rule-based, deterministic fit scoring - spec §8. Every dimension below produces
 * a [FitReason] with a plain-language [FitReason.explanation]; nothing is scored
 * without also being explained. There is no ML model and no hidden weighting:
 * every number here traces back to a profile preference or an opportunity field,
 * both of which the user can inspect.
 *
 * Band (§8.3) is derived from the *named* conditions in the spec prose - "clears
 * skill + seniority + at least one target sector/lane, no blocking caution" - not
 * from an arbitrary score cutoff. [score] exists only as a secondary sort key
 * within a band, not as the thing the UI leads with.
 */
class FitEngineImpl : FitEngine {

    override fun score(opportunity: Opportunity, profile: Profile, nowMillis: Long): FitBreakdown {
        val reasons = listOf(
            scoreSkillMatch(opportunity, profile),
            scoreSectorMatch(opportunity, profile),
            scoreSeniorityMatch(opportunity, profile),
            scoreLocationFit(opportunity, profile),
            scoreModeFit(opportunity, profile),
            scoreGrowthSignal(opportunity, profile),
            scoreRecency(opportunity, profile, nowMillis),
            scoreDeadlineRisk(opportunity, profile, nowMillis),
        )

        val weightTotal = profile.weights.total().coerceAtLeast(MIN_WEIGHT_TOTAL)
        val weightedSum = reasons.sumOf { it.weightedContribution.toDouble() }.toFloat()
        val normalized = (weightedSum / weightTotal).coerceIn(-1f, 1f)
        val rawScore = (((normalized + 1f) / 2f) * 100f).roundToInt().coerceIn(0, 100)

        val band = classifyBand(reasons, opportunity.source.confidence)
        val score = applyScoreCap(rawScore, reasons, band)

        val topReasons = reasons
            .filter { it.direction == Direction.POSITIVE }
            .sortedByDescending { it.weightedContribution }
            .take(MAX_TOP_REASONS)

        val cautions = reasons
            .filter { it.direction == Direction.CAUTION }
            .sortedByDescending { (if (it.isBlocking) BLOCKING_SORT_BIAS else 0f) + abs(it.weightedContribution) }

        return FitBreakdown(
            opportunityId = opportunity.id,
            profileId = profile.id,
            score = score,
            band = band,
            topReasons = topReasons,
            cautions = cautions,
        )
    }

    /**
     * Traces directly to spec §8.3's prose definition of each band, with two caps
     * added from recommendations doc §25 ("score caps to prevent inflated
     * recommendations"):
     *   - zero of the required skills matched is treated as more severe than a
     *     partial gap ("missing core skill with mandatory signal... max Good or
     *     Stretch" in the doc's table) - it can only ever reach STRETCH.
     *   - an unverified source can never be presented as a top (STRONG)
     *     recommendation regardless of how well the role otherwise fits ("cannot
     *     be top recommendation unless manually allowed").
     */
    private fun classifyBand(reasons: List<FitReason>, sourceConfidence: SourceConfidence): FitBand {
        val byDim = reasons.associateBy { it.dimension }
        val hasBlocking = reasons.any { it.isBlocking }
        val skillOk = byDim[FitDimension.SKILL_MATCH]?.direction != Direction.CAUTION
        val seniorityIsRightLevel = byDim[FitDimension.SENIORITY_MATCH]?.direction == Direction.POSITIVE
        val sectorMatches = byDim[FitDimension.SECTOR_MATCH]?.direction == Direction.POSITIVE
        val softGapCount = reasons.count { it.direction == Direction.CAUTION && !it.isBlocking }
        // rawContribution == -1f only when skill-match ratio is exactly 0 (see
        // scoreSkillMatch) - i.e. none of the required skills matched at all.
        val missingMandatorySkill = byDim[FitDimension.SKILL_MATCH]?.rawContribution == -1f

        val unblockedBand = when {
            hasBlocking || missingMandatorySkill -> FitBand.STRETCH
            skillOk && seniorityIsRightLevel && sectorMatches && softGapCount == 0 -> FitBand.STRONG
            skillOk && softGapCount <= 1 -> FitBand.GOOD
            else -> FitBand.STRETCH
        }

        return if (sourceConfidence == SourceConfidence.UNVERIFIED && unblockedBand == FitBand.STRONG) {
            FitBand.GOOD
        } else {
            unblockedBand
        }
    }

    /**
     * Keeps the displayed number honest relative to the band (recommendations
     * doc §25) - a STRETCH item should never carry a score that reads as
     * "obviously better than" a GOOD item, and an expired deadline specifically
     * caps at 30 per the doc's table rather than the general STRETCH cap.
     */
    private fun applyScoreCap(rawScore: Int, reasons: List<FitReason>, band: FitBand): Int {
        val deadlineExpired = reasons.any { it.dimension == FitDimension.DEADLINE_RISK && it.isBlocking }
        if (deadlineExpired) return rawScore.coerceAtMost(EXPIRED_DEADLINE_SCORE_CAP)
        if (band == FitBand.STRETCH) return rawScore.coerceAtMost(STRETCH_SCORE_CAP)
        return rawScore
    }

    // ---------------------------------------------------------------------
    // Dimension scorers - one function per row of spec §8.2's table.
    // ---------------------------------------------------------------------

    private fun scoreSkillMatch(o: Opportunity, p: Profile): FitReason {
        val weight = p.weights.skillMatch
        val required = o.skillsRequired.map { it.lowercase().trim() }.filter { it.isNotBlank() }
        if (required.isNotEmpty()) {
            val core = p.coreSkills.map { it.lowercase().trim() }.toSet()
            val matched = required.count { it in core }
            val ratio = matched.toFloat() / required.size
            val raw = ((ratio - 0.5f) * 2f).coerceIn(-1f, 1f)
            return when {
                ratio >= 0.75f -> reason(FitDimension.SKILL_MATCH, Direction.POSITIVE, raw, weight, false,
                    "Matches $matched of ${required.size} required skills")
                ratio >= 0.4f -> reason(FitDimension.SKILL_MATCH, Direction.NEUTRAL, raw, weight, false,
                    "Matches $matched of ${required.size} required skills")
                else -> {
                    val missing = required.filterNot { it in core }.take(3)
                    reason(FitDimension.SKILL_MATCH, Direction.CAUTION, raw, weight, false,
                        "Missing key skills: ${missing.joinToString(", ")}")
                }
            }
        }

        // v3 fallback: skills_required is empty for virtually every opportunity in
        // the real feed today - no collector currently extracts structured
        // required-skills text from a job description, so the branch above almost
        // never runs in practice. Rather than the old behaviour (a blanket NEUTRAL
        // here, which silently made "no skill signal" indistinguishable from "no
        // structured data yet" and let unrelated roles pass the skillOk gate for
        // free), fuzzy-match the role's specialisations against the profile's
        // declared core skills - a real signal we do have from every source.
        val coreSkills = p.coreSkills.map { it.lowercase().trim() }.filter { it.isNotBlank() }
        val specs = o.specialisations.map { it.lowercase().trim() }.filter { it.isNotBlank() }
        if (coreSkills.isEmpty() || specs.isEmpty()) {
            return reason(FitDimension.SKILL_MATCH, Direction.NEUTRAL, 0f, weight, false,
                "No specific skills listed for this role")
        }
        val matchedSkills = coreSkills.filter { skill -> specs.any { fuzzyMatches(skill, it) } }
        val ratio = matchedSkills.size.toFloat() / coreSkills.size
        return when {
            ratio >= 0.5f -> reason(FitDimension.SKILL_MATCH, Direction.POSITIVE, ratio.coerceIn(0f, 1f), weight, false,
                "Specialisation overlap: ${matchedSkills.take(2).joinToString(", ") { it.replace('_', ' ') }}")
            matchedSkills.isNotEmpty() -> reason(FitDimension.SKILL_MATCH, Direction.NEUTRAL, 0.15f, weight, false,
                "Some specialisation overlap: ${matchedSkills.first().replace('_', ' ')}")
            else -> reason(FitDimension.SKILL_MATCH, Direction.NEUTRAL, 0f, weight, false,
                "No clear skill signal for this role")
        }
    }

    /**
     * v3 rewrite. The old version compared `o.categories` against `p.targetLanes`
     * with exact set intersection (`cats.intersect(lanes)`) - this silently failed
     * almost universally in practice, because profile lanes are free text a person
     * typed ("finance", "investment") while feed categories/specialisations/
     * industry are taxonomy-namespaced snake_case ids ("financial_services",
     * "credit_analysis") that essentially never equal the free text by `==`.
     * Verified against a real 609-item feed snapshot (2026-07-11): the old exact
     * match found genuine overlap on almost nothing, and because a single
     * "Outside your usual sectors" caution alone was never enough to block GOOD
     * (see classifyBand), completely unrelated roles (e.g. a Notion technical
     * recruiter posting, for a profile targeting climate/finance/manufacturing)
     * were routinely classified "Good fit" on work-mode match alone.
     *
     * Now: fuzzy-matches each profile lane against categories + specialisations +
     * industry (see [fuzzyMatches]), and - the more important change - a genuine
     * total mismatch (zero fuzzy overlap across all three signals, with the
     * profile having actually declared target lanes) is now `isBlocking = true`,
     * the same severity `classifyBand` already gives a missing mandatory skill.
     * This is a deliberate design choice, not a tuning tweak: a role with no
     * fuzzy-matchable connection to any of your declared sectors should read as
     * STRETCH ("still shown, but a real stretch"), never STRONG/GOOD, regardless
     * of how well other dimensions (work mode, recency) happen to line up.
     */
    private fun scoreSectorMatch(o: Opportunity, p: Profile): FitReason {
        val weight = p.weights.sectorMatch
        val lanes = p.targetLanes.map { it.lowercase().trim() }.filter { it.isNotBlank() }
        if (lanes.isEmpty()) {
            return reason(FitDimension.SECTOR_MATCH, Direction.NEUTRAL, 0f, weight, false,
                "No target sectors set on this profile")
        }
        val candidates = (o.categories + o.specialisations + listOfNotNull(o.industry))
            .map { it.lowercase().trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (candidates.isEmpty()) {
            // No category/specialisation/industry data on the opportunity at all -
            // this is "unknown", not "compared and found no overlap". Only a
            // genuine, verified mismatch (data exists, none of it fuzzy-matches)
            // should carry the blocking severity below - absence of data is never
            // evidence of mismatch (same principle the rest of this engine follows
            // for missing fields elsewhere).
            return reason(FitDimension.SECTOR_MATCH, Direction.NEUTRAL, 0f, weight, false,
                "Sector not specified for this role")
        }
        val matchedLanes = lanes.filter { lane -> candidates.any { fuzzyMatches(lane, it) } }

        return when {
            matchedLanes.size >= 2 -> reason(FitDimension.SECTOR_MATCH, Direction.POSITIVE, 1f, weight, false,
                "In ${matchedLanes.take(2).joinToString(" & ") { it.replace('_', ' ') }} - target lanes for you")
            matchedLanes.size == 1 -> reason(FitDimension.SECTOR_MATCH, Direction.POSITIVE, 0.6f, weight, false,
                "In ${matchedLanes.first().replace('_', ' ')} - a target lane")
            else -> reason(FitDimension.SECTOR_MATCH, Direction.CAUTION, -1f, weight, true,
                "Outside your target sectors (${lanes.joinToString(", ")})")
        }
    }

    private fun scoreSeniorityMatch(o: Opportunity, p: Profile): FitReason {
        val weight = p.weights.seniorityMatch
        val oSen = o.seniority
            ?: return reason(FitDimension.SENIORITY_MATCH, Direction.NEUTRAL, 0f, weight, false,
                "Seniority level not specified")
        val diff = oSen.ordinal - p.seniority.ordinal
        return when {
            diff == 0 -> reason(FitDimension.SENIORITY_MATCH, Direction.POSITIVE, 1f, weight, false,
                "Right level for your experience")
            diff == -1 -> reason(FitDimension.SENIORITY_MATCH, Direction.NEUTRAL, 0.2f, weight, false,
                "Slightly below your current level")
            diff <= -2 -> reason(FitDimension.SENIORITY_MATCH, Direction.CAUTION, -0.3f, weight, false,
                "Well below your experience level")
            diff in 1..p.seniorityOpenness -> reason(FitDimension.SENIORITY_MATCH, Direction.CAUTION, -0.2f, weight, false,
                "One level above you - a stretch")
            else -> reason(FitDimension.SENIORITY_MATCH, Direction.CAUTION, -0.8f, weight, true,
                "$diff levels above your experience - a significant stretch")
        }
    }

    private fun scoreLocationFit(o: Opportunity, p: Profile): FitReason {
        val weight = p.weights.locationFit
        val prefs = p.locationPrefs
        val mode = o.workMode

        if (mode == WorkMode.REMOTE_KENYA && prefs.acceptsRemoteKenya) {
            return reason(FitDimension.LOCATION_FIT, Direction.POSITIVE, 0.8f, weight, false,
                "Remote from Kenya - matches your preferences")
        }
        if (mode == WorkMode.REMOTE_REGIONAL && prefs.acceptsRegional) {
            return reason(FitDimension.LOCATION_FIT, Direction.POSITIVE, 0.7f, weight, false,
                "Regional remote role - within your range")
        }
        if (mode == WorkMode.REMOTE_GLOBAL && prefs.acceptsInternational) {
            return reason(FitDimension.LOCATION_FIT, Direction.POSITIVE, 0.7f, weight, false,
                "Global remote role - within your range")
        }

        val relocCountry = o.location.relocationCountry
        if (relocCountry != null) {
            return if (prefs.acceptsRelocation) {
                reason(FitDimension.LOCATION_FIT, Direction.POSITIVE, 0.5f, weight, false,
                    "Relocation-worthy to $relocCountry - matches your openness to relocate")
            } else {
                reason(FitDimension.LOCATION_FIT, Direction.CAUTION, -0.6f, weight, true,
                    "Requires relocation to $relocCountry, outside your preferences")
            }
        }

        val sameRegion = prefs.baseRegion != null && o.location.region != null &&
            prefs.baseRegion.equals(o.location.region, ignoreCase = true)
        if (sameRegion) {
            return reason(FitDimension.LOCATION_FIT, Direction.POSITIVE, 0.9f, weight, false,
                "Based in ${o.location.region}, matches your base")
        }

        if (o.location.region != null) {
            return reason(FitDimension.LOCATION_FIT, Direction.CAUTION, -0.4f, weight, false,
                "Based in ${o.location.region}, away from your base")
        }

        return reason(FitDimension.LOCATION_FIT, Direction.NEUTRAL, 0f, weight, false, "Location not specified")
    }

    private fun scoreModeFit(o: Opportunity, p: Profile): FitReason {
        val weight = p.weights.modeFit
        val mode = o.workMode
            ?: return reason(FitDimension.MODE_FIT, Direction.NEUTRAL, 0f, weight, false, "Work mode not specified")
        return if (mode in p.modePrefs) {
            reason(FitDimension.MODE_FIT, Direction.POSITIVE, 0.6f, weight, false,
                "Matches your preferred work mode (${workModeLabel(mode)})")
        } else {
            reason(FitDimension.MODE_FIT, Direction.CAUTION, -0.4f, weight, false,
                "${workModeLabel(mode).replaceFirstChar { it.uppercase() }} isn't among your preferred work modes")
        }
    }

    private fun scoreGrowthSignal(o: Opportunity, p: Profile): FitReason {
        val weight = p.weights.growthSignal
        val isGrowthType = o.opportunityType == OpportunityType.FELLOWSHIP || o.opportunityType == OpportunityType.GRANT
        val isHiddenGem = OpportunityFlag.HIDDEN_GEM in o.flags
        val lanes = p.targetLanes.map { it.lowercase() }.toSet()
        val matchingLane = o.categories.firstOrNull { it.lowercase() in lanes }

        return when {
            (isGrowthType || isHiddenGem) && matchingLane != null ->
                reason(FitDimension.GROWTH_SIGNAL, Direction.POSITIVE, 0.8f, weight, false,
                    "Could open a pathway further into ${matchingLane.replace('_', ' ')}")
            isGrowthType ->
                reason(FitDimension.GROWTH_SIGNAL, Direction.POSITIVE, 0.3f, weight, false,
                    "A ${o.opportunityType.name.lowercase()} - often a strong pathway opportunity")
            else -> reason(FitDimension.GROWTH_SIGNAL, Direction.NEUTRAL, 0f, weight, false, "No specific pathway signal")
        }
    }

    private fun scoreRecency(o: Opportunity, p: Profile, nowMillis: Long): FitReason {
        val weight = p.weights.recency
        val posted = o.postedAtMillis
            ?: return reason(FitDimension.RECENCY, Direction.NEUTRAL, 0f, weight, false, "Posting date unknown")
        val days = (nowMillis - posted) / DAY_MS
        return when {
            days < 0 -> reason(FitDimension.RECENCY, Direction.NEUTRAL, 0f, weight, false, "Posting date unknown")
            days <= 3 -> reason(FitDimension.RECENCY, Direction.POSITIVE, 1f, weight, false, "Posted ${dayLabel(days)}")
            days <= 14 -> reason(FitDimension.RECENCY, Direction.POSITIVE, 0.4f, weight, false, "Posted ${dayLabel(days)}")
            days <= 30 -> reason(FitDimension.RECENCY, Direction.NEUTRAL, 0f, weight, false, "Posted ${dayLabel(days)}")
            else -> reason(FitDimension.RECENCY, Direction.CAUTION, -0.4f, weight, false,
                "Posted ${dayLabel(days)} - may be filling")
        }
    }

    private fun scoreDeadlineRisk(o: Opportunity, p: Profile, nowMillis: Long): FitReason {
        val weight = p.weights.deadlineRisk
        val deadline = o.deadlineMillis
            ?: return reason(FitDimension.DEADLINE_RISK, Direction.NEUTRAL, 0f, weight, false, "No listed deadline")
        val daysLeft = (deadline - nowMillis) / DAY_MS
        return when {
            daysLeft < 0 -> reason(FitDimension.DEADLINE_RISK, Direction.CAUTION, -1f, weight, true, "Deadline has passed")
            daysLeft == 0L -> reason(FitDimension.DEADLINE_RISK, Direction.CAUTION, -0.3f, weight, false, "Closes today - apply now")
            daysLeft <= 2 -> reason(FitDimension.DEADLINE_RISK, Direction.CAUTION, -0.2f, weight, false,
                "Closes in $daysLeft days - act fast")
            daysLeft <= 10 -> reason(FitDimension.DEADLINE_RISK, Direction.POSITIVE, 0.6f, weight, false,
                "$daysLeft days to apply")
            daysLeft <= 30 -> reason(FitDimension.DEADLINE_RISK, Direction.POSITIVE, 0.3f, weight, false,
                "Deadline in $daysLeft days")
            else -> reason(FitDimension.DEADLINE_RISK, Direction.NEUTRAL, 0.1f, weight, false,
                "Deadline in $daysLeft days")
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun reason(
        dimension: FitDimension,
        direction: Direction,
        raw: Float,
        weight: Float,
        isBlocking: Boolean,
        explanation: String,
    ) = FitReason(
        dimension = dimension,
        direction = direction,
        rawContribution = raw,
        weightedContribution = raw * weight,
        isBlocking = isBlocking,
        explanation = explanation,
    )

    /**
     * Cheap fuzzy match between free text (profile lanes/skills, user-typed) and
     * taxonomy-namespaced snake_case ids (feed categories/specialisations/
     * industry, e.g. "financial_services", "credit_analysis"). No stemming
     * library, no ML - three deliberately simple rules, in order:
     *   1. exact match after normalising underscores to spaces
     *   2. substring match either direction (e.g. "investment" is a substring of
     *      normalised "investment banking")
     *   3. shared 5-character prefix, guarding both strings to be >= 5 chars so
     *      short words don't produce false positives (e.g. "hr" would trivially
     *      "share a prefix" with almost anything if this guard weren't here) -
     *      this is what lets "finance" catch "financial services" even though
     *      "finance" is not literally a substring of "financial" (the shared
     *      "financ" stem is 6 characters, well past the 5-char guard).
     * Validated against a real 609-item feed snapshot before being written here
     * (see the fit-engine v3 rewrite notes on scoreSectorMatch) - not a
     * theoretical heuristic, checked against actual title/industry/specialisation
     * data and a real profile's free-text lanes.
     */
    private fun fuzzyMatches(a: String, b: String): Boolean {
        val na = a.lowercase().trim().replace('_', ' ').replace('-', ' ')
        val nb = b.lowercase().trim().replace('_', ' ').replace('-', ' ')
        if (na.isBlank() || nb.isBlank()) return false
        if (na == nb) return true
        if (na.length >= 3 && nb.contains(na)) return true
        if (nb.length >= 3 && na.contains(nb)) return true
        if (na.length >= 5 && nb.length >= 5 && na.take(5) == nb.take(5)) return true
        return false
    }

    private fun dayLabel(days: Long): String = when (days) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$days days ago"
    }

    private fun workModeLabel(mode: WorkMode): String = when (mode) {
        WorkMode.ONSITE -> "on-site"
        WorkMode.HYBRID -> "hybrid"
        WorkMode.REMOTE_KENYA -> "remote (Kenya)"
        WorkMode.REMOTE_REGIONAL -> "remote (regional)"
        WorkMode.REMOTE_GLOBAL -> "remote (global)"
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val MIN_WEIGHT_TOTAL = 0.0001f
        const val MAX_TOP_REASONS = 3
        const val BLOCKING_SORT_BIAS = 1000f
        const val EXPIRED_DEADLINE_SCORE_CAP = 30
        const val STRETCH_SCORE_CAP = 45
    }
}
