package com.kazisasa.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * These classes are a direct, field-for-field mirror of spec §24.2-§24.3 and §25.
 * Keep them boring and literal - any cleverness (defaults, renamed fields, derived
 * values) belongs in the data.mapper package, not here, so a diff against SCHEMA.md
 * always shows exactly what changed.
 */

@Serializable
data class FeedResponseDto(
    val meta: FeedMetaDto,
    val profiles: List<ProfileDto> = emptyList(),
    val opportunities: List<OpportunityDto>,
)

@Serializable
data class FeedMetaDto(
    @SerialName("feed_version") val feedVersion: String,
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("next_expected_update") val nextExpectedUpdate: String? = null,
    @SerialName("opportunity_count") val opportunityCount: Int = 0,
    @SerialName("source_count") val sourceCount: Int = 0,
    @SerialName("schema_url") val schemaUrl: String? = null,
    /** Recommendations doc §20: the app must never present sample opportunities as verified live roles. */
    @SerialName("is_sample_data") val isSampleData: Boolean = false,
)

@Serializable
data class OpportunityDto(
    val id: String,
    val title: String,
    @SerialName("opportunity_type") val opportunityType: String,
    val organisation: OrganisationDto,
    val location: LocationDto,
    @SerialName("work_mode") val workMode: String? = null,
    val seniority: String? = null,
    val categories: List<String> = emptyList(),
    @SerialName("skills_required") val skillsRequired: List<String> = emptyList(),
    @SerialName("skills_preferred") val skillsPreferred: List<String> = emptyList(),
    @SerialName("posted_at") val postedAt: String? = null,
    val deadline: String? = null,
    @SerialName("deadline_confidence") val deadlineConfidence: String = "unknown",
    val compensation: CompensationDto? = null,
    val source: SourceDto,
    @SerialName("apply_url") val applyUrl: String? = null,
    @SerialName("apply_is_official") val applyIsOfficial: Boolean = false,
    val flags: List<String> = emptyList(),
    @SerialName("eligibility_notes") val eligibilityNotes: String? = null,
    val summary: String? = null,
    @SerialName("raw_description_url") val rawDescriptionUrl: String? = null,
    // ---- v3 additions (SCHEMA.md "v3 additions") - all optional, an app
    // reading an older v2-shaped feed just gets nulls/empty lists here. ----
    val industry: String? = null,
    val specialisations: List<String> = emptyList(),
    @SerialName("years_experience_min") val yearsExperienceMin: Int? = null,
    @SerialName("years_experience_max") val yearsExperienceMax: Int? = null,
    @SerialName("education_required") val educationRequired: String? = null,
    @SerialName("education_field") val educationField: List<String> = emptyList(),
    @SerialName("contract_type") val contractType: String? = null,
)

@Serializable
data class OrganisationDto(
    val name: String,
    val type: String = "unverified",
    val verified: Boolean = false,
)

@Serializable
data class LocationDto(
    val raw: String? = null,
    val country: String? = null,
    val region: String? = null,
    @SerialName("is_remote_from_kenya") val isRemoteFromKenya: Boolean = false,
    val scope: String? = null,
    @SerialName("relocation_country") val relocationCountry: String? = null,
)

@Serializable
data class CompensationDto(
    val min: Int? = null,
    val max: Int? = null,
    val currency: String? = null,
    val period: String? = null,
    val disclosed: Boolean = false,
)

@Serializable
data class SourceDto(
    val name: String,
    val url: String,
    val confidence: String = "unverified",
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

// ---- Bundled default profiles (spec §25) ----

@Serializable
data class ProfileDto(
    val id: String,
    val label: String,
    @SerialName("target_lanes") val targetLanes: List<String> = emptyList(),
    @SerialName("core_skills") val coreSkills: List<String> = emptyList(),
    val seniority: String,
    @SerialName("seniority_openness") val seniorityOpenness: Int = 1,
    @SerialName("location_prefs") val locationPrefs: LocationPrefsDto,
    @SerialName("mode_prefs") val modePrefs: List<String> = emptyList(),
    val weights: FitWeightsDto = FitWeightsDto(),
)

@Serializable
data class LocationPrefsDto(
    @SerialName("base_region") val baseRegion: String? = null,
    @SerialName("accepts_remote_kenya") val acceptsRemoteKenya: Boolean = true,
    @SerialName("accepts_regional") val acceptsRegional: Boolean = false,
    @SerialName("accepts_international") val acceptsInternational: Boolean = false,
    @SerialName("accepts_relocation") val acceptsRelocation: Boolean = false,
)

@Serializable
data class FitWeightsDto(
    @SerialName("skill_match") val skillMatch: Float = 1.0f,
    @SerialName("sector_match") val sectorMatch: Float = 0.9f,
    @SerialName("seniority_match") val seniorityMatch: Float = 0.8f,
    @SerialName("location_fit") val locationFit: Float = 0.6f,
    @SerialName("mode_fit") val modeFit: Float = 0.5f,
    @SerialName("growth_signal") val growthSignal: Float = 0.4f,
    val recency: Float = 0.3f,
    @SerialName("deadline_risk") val deadlineRisk: Float = 0.3f,
)
