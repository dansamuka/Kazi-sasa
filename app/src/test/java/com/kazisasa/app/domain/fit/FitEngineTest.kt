package com.kazisasa.app.domain.fit

import com.kazisasa.app.domain.model.Compensation
import com.kazisasa.app.domain.model.DeadlineConfidence
import com.kazisasa.app.domain.model.FitBand
import com.kazisasa.app.domain.model.FitDimension
import com.kazisasa.app.domain.model.FitWeights
import com.kazisasa.app.domain.model.LocationInfo
import com.kazisasa.app.domain.model.LocationPrefs
import com.kazisasa.app.domain.model.LocationScope
import com.kazisasa.app.domain.model.Opportunity
import com.kazisasa.app.domain.model.OpportunityType
import com.kazisasa.app.domain.model.Organisation
import com.kazisasa.app.domain.model.OrgType
import com.kazisasa.app.domain.model.Profile
import com.kazisasa.app.domain.model.Seniority
import com.kazisasa.app.domain.model.SourceConfidence
import com.kazisasa.app.domain.model.SourceInfo
import com.kazisasa.app.domain.model.WorkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These five scenarios are exactly what was hand-verified with a standalone
 * `kotlinc` run during development (see the project README, "What's verified vs.
 * not") - reproduced here as real JUnit tests so `./gradlew test` keeps proving
 * it on every change, not just once at authoring time.
 *
 * FitEngine has zero Android dependencies by design (spec Part B refinement),
 * so this test needs no Robolectric, no instrumentation, no emulator - it's a
 * plain JVM unit test.
 */
class FitEngineTest {

    private val engine: FitEngine = FitEngineImpl()
    private val now = 1_752_134_400_000L // fixed instant so tests are deterministic regardless of when they run
    private val day = 86_400_000L

    private fun profile(
        seniority: Seniority = Seniority.SENIOR,
        seniorityOpenness: Int = 1,
        acceptsRelocation: Boolean = true,
        modePrefs: List<WorkMode> = listOf(WorkMode.REMOTE_KENYA, WorkMode.HYBRID, WorkMode.ONSITE),
    ) = Profile(
        id = "profile_climate_finance",
        label = "Climate & Development Finance",
        targetLanes = listOf("climate_finance", "development", "programme_management"),
        coreSkills = listOf("financial_modelling", "credit_analysis", "excel", "impact_measurement"),
        seniority = seniority,
        seniorityOpenness = seniorityOpenness,
        locationPrefs = LocationPrefs(
            baseRegion = "Nairobi", acceptsRemoteKenya = true,
            acceptsRegional = true, acceptsInternational = true, acceptsRelocation = acceptsRelocation,
        ),
        modePrefs = modePrefs,
        weights = FitWeights(),
        isDefault = true,
    )

    private fun opportunity(
        id: String,
        categories: List<String>,
        skillsRequired: List<String> = emptyList(),
        seniority: Seniority? = Seniority.MID,
        workMode: WorkMode? = WorkMode.HYBRID,
        region: String? = "Nairobi",
        relocationCountry: String? = null,
        postedDaysAgo: Long? = 2,
        deadlineInDays: Long? = 14,
        sourceConfidence: SourceConfidence = SourceConfidence.OFFICIAL,
        industry: String? = null,
        specialisations: List<String> = emptyList(),
    ) = Opportunity(
        id = id,
        title = "Test Opportunity $id",
        opportunityType = OpportunityType.JOB,
        organisation = Organisation("Acumen", OrgType.NGO, verified = true),
        location = LocationInfo(
            raw = region, country = "KE", region = region,
            isRemoteFromKenya = workMode == WorkMode.REMOTE_KENYA,
            scope = LocationScope.NATIONAL, relocationCountry = relocationCountry,
        ),
        workMode = workMode,
        seniority = seniority,
        categories = categories,
        skillsRequired = skillsRequired,
        skillsPreferred = emptyList(),
        postedAtMillis = postedDaysAgo?.let { now - it * day },
        deadlineMillis = deadlineInDays?.let { now + it * day },
        deadlineConfidence = DeadlineConfidence.EXPLICIT,
        compensation = null,
        source = SourceInfo("ReliefWeb", "https://reliefweb.int/job/x", sourceConfidence, now),
        applyUrl = "https://acumen.org/careers/x",
        applyIsOfficial = true,
        flags = emptyList(),
        eligibilityNotes = null,
        summary = "Sample opportunity for testing.",
        rawDescriptionUrl = null,
        industry = industry,
        specialisations = specialisations,
        yearsExperienceMin = null,
        yearsExperienceMax = null,
        educationRequired = null,
        educationField = emptyList(),
        contractType = null,
    )

    @Test
    fun `full match is STRONG with zero cautions`() {
        val opp = opportunity(
            id = "1", categories = listOf("climate_finance", "development"),
            skillsRequired = listOf("financial_modelling", "credit_analysis", "excel"),
            seniority = Seniority.SENIOR, workMode = WorkMode.HYBRID, region = "Nairobi",
            postedDaysAgo = 1, deadlineInDays = 12,
        )
        val result = engine.score(opp, profile(), now)
        assertEquals(FitBand.STRONG, result.band)
        assertTrue("expected no cautions, got ${result.cautions}", result.cautions.isEmpty())
    }

    @Test
    fun `seniority gap beyond openness plus unwanted relocation is STRETCH with a blocking caution`() {
        val opp = opportunity(
            id = "2", categories = listOf("programme_management"),
            skillsRequired = listOf("financial_modelling"),
            seniority = Seniority.LEADERSHIP, workMode = WorkMode.ONSITE,
            region = "Lagos", relocationCountry = "Nigeria",
            postedDaysAgo = 5, deadlineInDays = 20,
        )
        val result = engine.score(opp, profile(acceptsRelocation = false), now)
        assertEquals(FitBand.STRETCH, result.band)
        assertTrue(result.cautions.any { it.isBlocking })
    }

    @Test
    fun `expired deadline forces STRETCH even on an otherwise perfect match`() {
        val opp = opportunity(
            id = "3", categories = listOf("climate_finance"),
            skillsRequired = listOf("credit_analysis", "excel"),
            seniority = Seniority.SENIOR, workMode = WorkMode.HYBRID, region = "Nairobi",
            postedDaysAgo = 10, deadlineInDays = -3,
        )
        val result = engine.score(opp, profile(), now)
        assertEquals(FitBand.STRETCH, result.band)
        assertTrue(result.cautions.any { it.dimension == FitDimension.DEADLINE_RISK && it.isBlocking })
    }

    @Test
    fun `missing fields across the board do not crash and are not punished to zero`() {
        val opp = opportunity(
            id = "4", categories = emptyList(), skillsRequired = emptyList(),
            seniority = null, workMode = null, region = null,
            postedDaysAgo = null, deadlineInDays = null,
        )
        val result = engine.score(opp, profile(), now)
        assertTrue("expected a mid-range score, got ${result.score}", result.score in 30..70)
    }

    @Test
    fun `single soft gap is GOOD, not STRONG and not STRETCH`() {
        val opp = opportunity(
            id = "5", categories = listOf("climate_finance"),
            skillsRequired = listOf("financial_modelling", "credit_analysis", "excel"),
            seniority = Seniority.SENIOR, workMode = WorkMode.ONSITE, region = "Nairobi",
            postedDaysAgo = 2, deadlineInDays = 15,
        )
        val result = engine.score(opp, profile(modePrefs = listOf(WorkMode.REMOTE_KENYA)), now)
        assertEquals(FitBand.GOOD, result.band)
    }

    @Test
    fun `scoring is a pure deterministic function of its inputs`() {
        val opp = opportunity(id = "6", categories = listOf("climate_finance"), skillsRequired = listOf("excel"))
        val p = profile()
        assertEquals(engine.score(opp, p, now), engine.score(opp, p, now))
    }

    // --- Added per implementation-recommendations doc §27 ---

    @Test
    fun `good fit - decent match with exactly one soft gap, named explicitly`() {
        val opp = opportunity(
            id = "7", categories = listOf("climate_finance"),
            skillsRequired = listOf("financial_modelling", "credit_analysis"),
            seniority = Seniority.MID, workMode = WorkMode.HYBRID, region = "Nairobi",
        )
        val result = engine.score(opp, profile(), now) // profile is SENIOR -> opportunity MID is one level below: a soft, non-blocking gap
        assertEquals(FitBand.GOOD, result.band)
    }

    @Test
    fun `location mismatch - different region, not remote, not relocation-flagged, is a soft caution`() {
        val opp = opportunity(
            id = "8", categories = listOf("climate_finance", "development"),
            skillsRequired = listOf("financial_modelling", "credit_analysis", "excel"),
            seniority = Seniority.SENIOR, workMode = WorkMode.ONSITE, region = "Kisumu",
        )
        val result = engine.score(opp, profile(), now) // profile's base region is Nairobi
        assertTrue(result.cautions.any { it.dimension == FitDimension.LOCATION_FIT })
        assertEquals(FitBand.GOOD, result.band) // one soft gap, nothing blocking
    }

    @Test
    fun `remote-from-Kenya role scores at least as well as an equivalent onsite role in a different region`() {
        // Differential comparison rather than checking topReasons directly: with a
        // full skill+seniority+sector match, SKILL_MATCH/SENIORITY_MATCH/SECTOR_MATCH
        // legitimately outweigh LOCATION_FIT for the top-3 display slots (that's
        // correct - it reflects the profile's own weights, skillMatch=1.0 > locationFit=0.6).
        // What this test actually needs to verify is the *effect* of the location
        // signal, which shows up in the score and in the absence of a location caution.
        val onsiteElsewhere = opportunity(
            id = "9a", categories = listOf("climate_finance"),
            skillsRequired = listOf("financial_modelling", "credit_analysis", "excel"),
            seniority = Seniority.SENIOR, workMode = WorkMode.ONSITE, region = "Kisumu",
        )
        val remoteFromKenya = opportunity(
            id = "9b", categories = listOf("climate_finance"),
            skillsRequired = listOf("financial_modelling", "credit_analysis", "excel"),
            seniority = Seniority.SENIOR, workMode = WorkMode.REMOTE_KENYA, region = null,
        )
        val p = profile()
        val onsiteResult = engine.score(onsiteElsewhere, p, now)
        val remoteResult = engine.score(remoteFromKenya, p, now)

        assertTrue(
            "a remote-from-Kenya role matching the profile's accepted mode should score >= an equivalent onsite role in another region",
            remoteResult.score >= onsiteResult.score,
        )
        assertTrue(
            "the remote-from-Kenya role should carry no location caution",
            remoteResult.cautions.none { it.dimension == FitDimension.LOCATION_FIT },
        )
    }

    @Test
    fun `missing every required skill caps the band at STRETCH even with everything else lining up`() {
        val opp = opportunity(
            id = "10", categories = listOf("climate_finance", "development"),
            skillsRequired = listOf("actuarial_science", "underwriting"), // none of these are in the test profile's core skills
            seniority = Seniority.SENIOR, workMode = WorkMode.HYBRID, region = "Nairobi",
        )
        val result = engine.score(opp, profile(), now)
        assertEquals(FitBand.STRETCH, result.band)
    }

    @Test
    fun `unverified source caps the band at GOOD even on an otherwise perfect match`() {
        val opp = opportunity(
            id = "11", categories = listOf("climate_finance", "development"),
            skillsRequired = listOf("financial_modelling", "credit_analysis", "excel"),
            seniority = Seniority.SENIOR, workMode = WorkMode.HYBRID, region = "Nairobi",
            sourceConfidence = SourceConfidence.UNVERIFIED,
        )
        val result = engine.score(opp, profile(), now)
        assertEquals(
            "an unverified source must never be presented as a STRONG (top) recommendation",
            FitBand.GOOD, result.band,
        )
    }

    @Test
    fun `closing soon but not yet expired is urgency, not the same as an expired deadline`() {
        val opp = opportunity(
            id = "12", categories = listOf("climate_finance"),
            skillsRequired = listOf("financial_modelling"),
            seniority = Seniority.SENIOR, workMode = WorkMode.HYBRID, region = "Nairobi",
            deadlineInDays = 1,
        )
        val result = engine.score(opp, profile(), now)
        val deadlineReason = result.cautions.firstOrNull { it.dimension == FitDimension.DEADLINE_RISK }
        assertTrue("expected a non-blocking urgency caution, got $deadlineReason", deadlineReason != null && !deadlineReason.isBlocking)
        assertTrue("an urgent-but-open deadline should not hit the expired-deadline score cap", result.score > 30)
    }

    @Test
    fun `switching profiles changes the score for the exact same opportunity`() {
        val opp = opportunity(
            id = "13", categories = listOf("climate_finance"),
            skillsRequired = listOf("financial_modelling", "credit_analysis", "excel"),
            seniority = Seniority.SENIOR, workMode = WorkMode.HYBRID, region = "Nairobi",
        )
        val climateProfile = profile()
        val corporateProfile = climateProfile.copy(
            targetLanes = listOf("hr", "administration", "retail"),
            coreSkills = listOf("ms_office", "communication", "scheduling"),
        )
        val scoreForClimateProfile = engine.score(opp, climateProfile, now)
        val scoreForCorporateProfile = engine.score(opp, corporateProfile, now)
        assertTrue(
            "the same opportunity should score differently for two very differently-weighted profiles",
            scoreForClimateProfile.score != scoreForCorporateProfile.score || scoreForClimateProfile.band != scoreForCorporateProfile.band,
        )
    }

    // --- v3 fit-engine rewrite: regression tests for the real bug this fixed ---
    // (reported directly: a "climate/investment/finance/manufacturing" profile
    // was seeing generic tech/HR/marketing roles at Ramp/Notion labelled "Good
    // fit" purely on work-mode match, because sector matching used exact string
    // equality between free-text profile lanes and taxonomy-namespaced feed ids,
    // which essentially never matched - so the sector caution was silently
    // ignored by classifyBand instead of blocking a false "Good fit".)

    @Test
    fun `free-text lane fuzzy-matches a taxonomy industry id - finance matches financial_services`() {
        val opp = opportunity(
            id = "14", categories = emptyList(), specialisations = listOf("general_finance"),
            industry = "financial_services", skillsRequired = emptyList(),
        )
        val financeProfile = profile().copy(targetLanes = listOf("investment", "finance", "manufacturing"))
        val result = engine.score(opp, financeProfile, now)
        val sectorReason = result.topReasons.firstOrNull { it.dimension == FitDimension.SECTOR_MATCH }
        assertTrue("expected a positive sector match via fuzzy 'finance' ~ 'financial_services', got $sectorReason", sectorReason != null)
    }

    @Test
    fun `genuine total sector mismatch is blocking and caps the band at STRETCH, even with a matching work mode`() {
        // This is the exact real-world case: a technology/HR-industry role with a
        // matching hybrid work mode, for a profile with zero overlap in any of
        // categories, specialisations, or industry. Old behaviour: GOOD (work
        // mode alone was enough, since skillOk defaulted true on empty
        // skillsRequired and the lone sector caution never blocked). New
        // behaviour: a verified zero-overlap sector mismatch is blocking.
        val opp = opportunity(
            id = "15", categories = emptyList(), specialisations = listOf("talent_acquisition"),
            industry = "human_resources", skillsRequired = emptyList(),
            seniority = Seniority.MID, workMode = WorkMode.HYBRID,
        )
        val financeProfile = profile().copy(targetLanes = listOf("investment", "finance", "manufacturing"))
        val result = engine.score(opp, financeProfile, now)
        assertEquals(
            "a role with zero fuzzy-matchable connection to any declared sector must never read as GOOD/STRONG",
            FitBand.STRETCH, result.band,
        )
        assertTrue(
            "the sector mismatch should be the blocking caution",
            result.cautions.any { it.dimension == FitDimension.SECTOR_MATCH && it.isBlocking },
        )
    }

    @Test
    fun `opportunity with no category, specialisation, or industry data at all stays neutral, not blocked`() {
        // Distinguishes "no data to compare" from "compared and found no overlap" -
        // absence of data must never be punished as if it were a verified mismatch.
        val opp = opportunity(
            id = "16", categories = emptyList(), specialisations = emptyList(),
            industry = null, skillsRequired = emptyList(),
        )
        val financeProfile = profile().copy(targetLanes = listOf("investment", "finance", "manufacturing"))
        val result = engine.score(opp, financeProfile, now)
        val sectorReason = (result.topReasons + result.cautions).firstOrNull { it.dimension == FitDimension.SECTOR_MATCH }
        assertTrue(
            "expected a neutral, non-blocking sector reason when the opportunity has no sector data at all, got $sectorReason",
            sectorReason == null || !sectorReason.isBlocking,
        )
    }

    @Test
    fun `specialisation-based skill fallback gives a real signal when skillsRequired is empty`() {
        // The vast majority of real feed opportunities have skillsRequired = []
        // (no collector currently extracts structured required-skills text) - this
        // fallback is what makes SKILL_MATCH meaningful in practice rather than an
        // always-true no-op.
        val opp = opportunity(
            id = "17", categories = emptyList(), skillsRequired = emptyList(),
            specialisations = listOf("credit_analysis", "financial_modelling"),
        )
        val result = engine.score(opp, profile(), now) // profile's coreSkills includes financial_modelling, credit_analysis
        val skillReason = result.topReasons.firstOrNull { it.dimension == FitDimension.SKILL_MATCH }
        assertTrue("expected a positive skill-match signal via specialisation overlap, got $skillReason", skillReason != null)
    }
}
