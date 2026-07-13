package com.kazisasa.app.data.repository

import com.kazisasa.app.domain.model.ContractType
import com.kazisasa.app.domain.model.DeadlineConfidence
import com.kazisasa.app.domain.model.LocationInfo
import com.kazisasa.app.domain.model.LocationScope
import com.kazisasa.app.domain.model.Opportunity
import com.kazisasa.app.domain.model.OpportunityType
import com.kazisasa.app.domain.model.Organisation
import com.kazisasa.app.domain.model.OrgType
import com.kazisasa.app.domain.model.Seniority
import com.kazisasa.app.domain.model.SourceConfidence
import com.kazisasa.app.domain.model.SourceInfo
import com.kazisasa.app.domain.model.WorkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedFiltersTest {

    private fun opp(
        id: String,
        title: String,
        org: String = "Test Org",
        seniority: Seniority? = null,
        industry: String? = null,
        country: String? = null,
        locationRaw: String? = null,
        contractType: ContractType? = null,
        opportunityType: OpportunityType = OpportunityType.JOB,
        workMode: WorkMode? = null,
        postedAtMillis: Long? = null,
    ) = ScoredOpportunity(
        opportunity = Opportunity(
            id = id, title = title,
            opportunityType = opportunityType,
            organisation = Organisation(org, OrgType.PRIVATE, verified = true),
            location = LocationInfo(raw = locationRaw ?: country, country = country, region = null, isRemoteFromKenya = false, scope = LocationScope.NATIONAL, relocationCountry = null),
            workMode = workMode, seniority = seniority,
            categories = emptyList(), skillsRequired = emptyList(), skillsPreferred = emptyList(),
            postedAtMillis = postedAtMillis, deadlineMillis = null, deadlineConfidence = DeadlineConfidence.UNKNOWN,
            compensation = null, source = SourceInfo("Test", "https://example.com", SourceConfidence.OFFICIAL, null),
            applyUrl = null, applyIsOfficial = false, flags = emptyList(), eligibilityNotes = null,
            summary = null, rawDescriptionUrl = null,
            industry = industry, specialisations = emptyList(),
            yearsExperienceMin = null, yearsExperienceMax = null,
            educationRequired = null, educationField = emptyList(), contractType = contractType,
        ),
        fit = null,
    )

    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L

    private val sample = listOf(
        opp("1", "Backend Engineer", org = "Ramp", seniority = Seniority.SENIOR, industry = "technology", country = "South Africa", contractType = ContractType.PERMANENT, workMode = WorkMode.REMOTE_GLOBAL, postedAtMillis = now - 2 * day),
        opp("2", "Head of Treasury", org = "Sun King", seniority = Seniority.LEADERSHIP, industry = "financial_services", country = "Kenya", locationRaw = "Nairobi, Kenya", contractType = ContractType.PERMANENT, workMode = WorkMode.ONSITE, postedAtMillis = now - 20 * day),
        opp("3", "Field Operations Associate", org = "One Acre Fund", seniority = Seniority.ENTRY, industry = "development_humanitarian", country = "Rwanda", contractType = ContractType.CONTRACT, workMode = WorkMode.HYBRID, opportunityType = OpportunityType.INTERNSHIP, postedAtMillis = now - 100 * day),
    )

    @Test
    fun `no active filters returns everything unchanged in default order`() {
        val result = sample.applyFilters(FeedFilters())
        assertEquals(sample, result)
    }

    @Test
    fun `keyword search matches title case-insensitively`() {
        val result = sample.applyFilters(FeedFilters(keyword = "treasury"))
        assertEquals(1, result.size)
        assertEquals("2", result.first().opportunity.id)
    }

    @Test
    fun `keyword search matches organisation name`() {
        val result = sample.applyFilters(FeedFilters(keyword = "sun king"))
        assertEquals(1, result.size)
        assertEquals("2", result.first().opportunity.id)
    }

    @Test
    fun `location is free-text substring match, not exact`() {
        val result = sample.applyFilters(FeedFilters(location = "nairobi"))
        assertEquals(1, result.size)
        assertEquals("2", result.first().opportunity.id)
    }

    @Test
    fun `location also matches against country when raw text doesn't contain it`() {
        val result = sample.applyFilters(FeedFilters(location = "rwanda"))
        assertEquals(1, result.size)
        assertEquals("3", result.first().opportunity.id)
    }

    @Test
    fun `experience filter is exact match against a set`() {
        val result = sample.applyFilters(FeedFilters(experience = setOf(Seniority.ENTRY, Seniority.LEADERSHIP)))
        assertEquals(setOf("2", "3"), result.map { it.opportunity.id }.toSet())
    }

    @Test
    fun `job type derives full_time from permanent contract`() {
        val result = sample.applyFilters(FeedFilters(jobType = setOf("full_time")))
        assertEquals(setOf("1", "2"), result.map { it.opportunity.id }.toSet())
    }

    @Test
    fun `job type surfaces internship as its own opportunity type, not a contract-derived value`() {
        val result = sample.applyFilters(FeedFilters(jobType = setOf("internship")))
        assertEquals(1, result.size)
        assertEquals("3", result.first().opportunity.id)
    }

    @Test
    fun `remote value collapses remote_kenya and remote_global into one bucket`() {
        val result = sample.applyFilters(FeedFilters(remote = setOf("remote")))
        assertEquals(1, result.size)
        assertEquals("1", result.first().opportunity.id) // REMOTE_GLOBAL -> "remote"
    }

    @Test
    fun `remote onsite and hybrid stay distinct`() {
        assertEquals(setOf("2"), sample.applyFilters(FeedFilters(remote = setOf("onsite"))).map { it.opportunity.id }.toSet())
        assertEquals(setOf("3"), sample.applyFilters(FeedFilters(remote = setOf("hybrid"))).map { it.opportunity.id }.toSet())
    }

    @Test
    fun `company filter matches exact organisation name`() {
        val result = sample.applyFilters(FeedFilters(company = setOf("One Acre Fund")))
        assertEquals(1, result.size)
        assertEquals("3", result.first().opportunity.id)
    }

    @Test
    fun `industry filter matches exactly`() {
        val result = sample.applyFilters(FeedFilters(industry = setOf("financial_services")))
        assertEquals(1, result.size)
        assertEquals("2", result.first().opportunity.id)
    }

    @Test
    fun `date posted excludes items with no verified recency when a threshold is active`() {
        val opWithNoDate = opp("4", "No Date Role", postedAtMillis = null)
        val items = sample + opWithNoDate
        val result = items.applyFilters(FeedFilters(datePostedDays = 30))
        assertTrue("item with null postedAtMillis must be excluded, not guessed", result.none { it.opportunity.id == "4" })
    }

    @Test
    fun `date posted within 7 days matches only recent items`() {
        val result = sample.applyFilters(FeedFilters(datePostedDays = 7))
        assertEquals(1, result.size)
        assertEquals("1", result.first().opportunity.id) // posted 2 days ago
    }

    @Test
    fun `multiple facets combine with AND, not OR`() {
        val result = sample.applyFilters(FeedFilters(jobType = setOf("full_time"), remote = setOf("onsite")))
        assertEquals(1, result.size)
        assertEquals("2", result.first().opportunity.id)
    }

    @Test
    fun `sort MOST_RECENT orders by postedAtMillis descending, nulls last`() {
        val opWithNoDate = opp("4", "No Date Role", postedAtMillis = null)
        val items = sample + opWithNoDate
        val result = items.applyFilters(FeedFilters(sortBy = SortOption.MOST_RECENT))
        assertEquals(listOf("1", "2", "3", "4"), result.map { it.opportunity.id })
    }

    @Test
    fun `isActive is false when every field is at its default`() {
        assertEquals(false, FeedFilters().isActive)
        assertEquals(true, FeedFilters(keyword = "x").isActive)
        assertEquals(true, FeedFilters(location = "x").isActive)
        assertEquals(true, FeedFilters(datePostedDays = 7).isActive)
        assertEquals(true, FeedFilters(experience = setOf(Seniority.MID)).isActive)
    }

    @Test
    fun `sortBy alone does not count as an active filter`() {
        assertEquals(false, FeedFilters(sortBy = SortOption.MOST_RECENT).isActive)
    }

    @Test
    fun `availableFacets derives distinct values from the actual item list`() {
        val facets = AvailableFacets.from(sample)
        assertEquals(listOf("full_time", "internship"), facets.jobType)
        assertEquals(listOf("hybrid", "onsite", "remote"), facets.remote)
        assertEquals(listOf("One Acre Fund", "Ramp", "Sun King"), facets.company)
        assertEquals(listOf("development_humanitarian", "financial_services", "technology"), facets.industry)
        assertEquals(listOf(Seniority.ENTRY, Seniority.SENIOR, Seniority.LEADERSHIP), facets.experience)
    }
}
