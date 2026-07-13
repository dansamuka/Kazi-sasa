package com.kazisasa.app.domain.model

/**
 * Pure domain representation of an opportunity - no Room, no Retrofit, no Android
 * imports. This is a deliberate refinement over the Part-B spec sketch (which had
 * FitEngine read Room @Entity classes directly): keeping the domain model
 * framework-free means FitEngine has zero Android dependency and can be compiled
 * and unit-tested with plain `kotlinc`, not just inside a full Android build.
 *
 * data/mapper/OpportunityMappers.kt converts OpportunityEntity <-> Opportunity
 * and OpportunityDto -> Opportunity.
 *
 * Dates are epoch millis (nullable) rather than a date-library type, to avoid
 * pulling java.time/desugaring or kotlinx-datetime into the domain module for
 * something the UI layer can format at the edge.
 */
data class Opportunity(
    val id: String,
    val title: String,
    val opportunityType: OpportunityType,
    val organisation: Organisation,
    val location: LocationInfo,
    val workMode: WorkMode?,
    val seniority: Seniority?,
    val categories: List<String>,
    val skillsRequired: List<String>,
    val skillsPreferred: List<String>,
    val postedAtMillis: Long?,
    val deadlineMillis: Long?,
    val deadlineConfidence: DeadlineConfidence,
    val compensation: Compensation?,
    val source: SourceInfo,
    val applyUrl: String?,
    val applyIsOfficial: Boolean,
    val flags: List<OpportunityFlag>,
    val eligibilityNotes: String?,
    val summary: String?,
    val rawDescriptionUrl: String?,
    // ---- v3 additions - see FeedDtos.kt / SCHEMA.md for provenance ----
    val industry: String?,
    val specialisations: List<String>,
    val yearsExperienceMin: Int?,
    val yearsExperienceMax: Int?,
    val educationRequired: EducationLevel?,
    val educationField: List<String>,
    val contractType: ContractType?,
)

data class Organisation(
    val name: String,
    val type: OrgType,
    val verified: Boolean,
)

data class LocationInfo(
    val raw: String?,
    val country: String?,
    val region: String?,
    val isRemoteFromKenya: Boolean,
    val scope: LocationScope?,
    val relocationCountry: String?,
)

data class Compensation(
    val min: Int?,
    val max: Int?,
    val currency: String?,
    val period: String?,
    val disclosed: Boolean,
)

data class SourceInfo(
    val name: String,
    val url: String,
    val confidence: SourceConfidence,
    val lastSeenAtMillis: Long?,
)
