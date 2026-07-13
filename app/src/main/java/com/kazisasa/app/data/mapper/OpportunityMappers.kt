package com.kazisasa.app.data.mapper

import com.kazisasa.app.data.local.entity.CompensationEmbedded
import com.kazisasa.app.data.local.entity.LocationEmbedded
import com.kazisasa.app.data.local.entity.OpportunityEntity
import com.kazisasa.app.data.local.entity.OrganisationEmbedded
import com.kazisasa.app.data.local.entity.SourceEmbedded
import com.kazisasa.app.data.remote.dto.CompensationDto
import com.kazisasa.app.data.remote.dto.LocationDto
import com.kazisasa.app.data.remote.dto.OpportunityDto
import com.kazisasa.app.data.remote.dto.OrganisationDto
import com.kazisasa.app.data.remote.dto.SourceDto
import com.kazisasa.app.domain.model.Compensation
import com.kazisasa.app.domain.model.DeadlineConfidence
import com.kazisasa.app.domain.model.LocationInfo
import com.kazisasa.app.domain.model.LocationScope
import com.kazisasa.app.domain.model.Opportunity
import com.kazisasa.app.domain.model.OpportunityFlag
import com.kazisasa.app.domain.model.OpportunityType
import com.kazisasa.app.domain.model.Organisation
import com.kazisasa.app.domain.model.OrgType
import com.kazisasa.app.domain.model.Seniority
import com.kazisasa.app.domain.model.SourceConfidence
import com.kazisasa.app.domain.model.SourceInfo
import com.kazisasa.app.domain.model.WorkMode
import com.kazisasa.app.domain.model.ContractType
import com.kazisasa.app.domain.model.EducationLevel
import java.time.Instant

/**
 * Malformed or unrecognised values must not crash the pipeline - spec §14.6 ("the
 * app should treat live data as a feed that may occasionally fail, lag, or contain
 * incomplete fields") and §17 ("gracefully handle missing fields"). Every parse
 * here degrades to null/neutral rather than throwing.
 */
private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? {
    if (raw.isNullOrBlank()) return null
    return runCatching { enumValueOf<T>(raw.trim().uppercase()) }.getOrNull()
}

private fun isoToMillisOrNull(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
}

// ---------------------------------------------------------------------
// DTO -> Entity  (feed sync path: FeedRepository writes these into Room)
// ---------------------------------------------------------------------

fun OpportunityDto.toEntity(fetchedAtMillis: Long): OpportunityEntity = OpportunityEntity(
    id = id,
    title = title,
    opportunityType = enumOrNull<OpportunityType>(opportunityType) ?: OpportunityType.JOB,
    organisation = organisation.toEmbedded(),
    location = location.toEmbedded(),
    workMode = enumOrNull<WorkMode>(workMode),
    seniority = enumOrNull<Seniority>(seniority),
    categories = categories,
    skillsRequired = skillsRequired,
    skillsPreferred = skillsPreferred,
    postedAtMillis = isoToMillisOrNull(postedAt),
    deadlineMillis = isoToMillisOrNull(deadline),
    deadlineConfidence = enumOrNull<DeadlineConfidence>(deadlineConfidence) ?: DeadlineConfidence.UNKNOWN,
    compensation = compensation?.toEmbedded(),
    source = source.toEmbedded(),
    applyUrl = applyUrl,
    applyIsOfficial = applyIsOfficial,
    flags = flags.mapNotNull { enumOrNull<OpportunityFlag>(it) },
    eligibilityNotes = eligibilityNotes,
    summary = summary,
    rawDescriptionUrl = rawDescriptionUrl,
    fetchedAt = fetchedAtMillis,
    // v3 additions - every enumOrNull call below has an explicit type argument
    // deliberately, after three separate bare-enumOrNull(x) bugs were found
    // and fixed in this exact function during CI (see git history) - a bare
    // call infers T from the nullable target type and violates enumOrNull's
    // own T : Enum<T> bound. Never omit the explicit <Type> here.
    industry = industry,
    specialisations = specialisations,
    yearsExperienceMin = yearsExperienceMin,
    yearsExperienceMax = yearsExperienceMax,
    educationRequired = enumOrNull<EducationLevel>(educationRequired),
    educationField = educationField,
    contractType = enumOrNull<ContractType>(contractType),
)

private fun OrganisationDto.toEmbedded() = OrganisationEmbedded(
    name = name,
    type = enumOrNull<OrgType>(type) ?: OrgType.UNVERIFIED,
    verified = verified,
)

private fun LocationDto.toEmbedded() = LocationEmbedded(
    raw = raw,
    country = country,
    region = region,
    isRemoteFromKenya = isRemoteFromKenya,
    scope = enumOrNull<LocationScope>(scope),
    relocationCountry = relocationCountry,
)

private fun CompensationDto.toEmbedded() = CompensationEmbedded(
    min = min, max = max, currency = currency, period = period, disclosed = disclosed,
)

private fun SourceDto.toEmbedded() = SourceEmbedded(
    name = name,
    url = url,
    confidence = enumOrNull<SourceConfidence>(confidence) ?: SourceConfidence.UNVERIFIED,
    lastSeenAtMillis = isoToMillisOrNull(lastSeenAt),
)

// ---------------------------------------------------------------------
// Entity -> Domain  (read path: repositories expose Opportunity, never OpportunityEntity, to the UI/FitEngine)
// ---------------------------------------------------------------------

fun OpportunityEntity.toDomain(): Opportunity = Opportunity(
    id = id,
    title = title,
    opportunityType = opportunityType,
    organisation = Organisation(organisation.name, organisation.type, organisation.verified),
    location = LocationInfo(
        raw = location.raw,
        country = location.country,
        region = location.region,
        isRemoteFromKenya = location.isRemoteFromKenya,
        scope = location.scope,
        relocationCountry = location.relocationCountry,
    ),
    workMode = workMode,
    seniority = seniority,
    categories = categories,
    skillsRequired = skillsRequired,
    skillsPreferred = skillsPreferred,
    postedAtMillis = postedAtMillis,
    deadlineMillis = deadlineMillis,
    deadlineConfidence = deadlineConfidence,
    compensation = compensation?.let { Compensation(it.min, it.max, it.currency, it.period, it.disclosed) },
    source = SourceInfo(source.name, source.url, source.confidence, source.lastSeenAtMillis),
    applyUrl = applyUrl,
    applyIsOfficial = applyIsOfficial,
    flags = flags,
    eligibilityNotes = eligibilityNotes,
    summary = summary,
    rawDescriptionUrl = rawDescriptionUrl,
    industry = industry,
    specialisations = specialisations,
    yearsExperienceMin = yearsExperienceMin,
    yearsExperienceMax = yearsExperienceMax,
    educationRequired = educationRequired,
    educationField = educationField,
    contractType = contractType,
)

/** Reverse of the above - used only to materialise a saved snapshot back into a cache row (spec §24.5). */
fun Opportunity.toEntity(fetchedAtMillis: Long): OpportunityEntity = OpportunityEntity(
    id = id,
    title = title,
    opportunityType = opportunityType,
    organisation = OrganisationEmbedded(organisation.name, organisation.type, organisation.verified),
    location = LocationEmbedded(
        location.raw, location.country, location.region,
        location.isRemoteFromKenya, location.scope, location.relocationCountry,
    ),
    workMode = workMode,
    seniority = seniority,
    categories = categories,
    skillsRequired = skillsRequired,
    skillsPreferred = skillsPreferred,
    postedAtMillis = postedAtMillis,
    deadlineMillis = deadlineMillis,
    deadlineConfidence = deadlineConfidence,
    compensation = compensation?.let { CompensationEmbedded(it.min, it.max, it.currency, it.period, it.disclosed) },
    source = SourceEmbedded(source.name, source.url, source.confidence, source.lastSeenAtMillis),
    applyUrl = applyUrl,
    applyIsOfficial = applyIsOfficial,
    flags = flags,
    eligibilityNotes = eligibilityNotes,
    summary = summary,
    rawDescriptionUrl = rawDescriptionUrl,
    fetchedAt = fetchedAtMillis,
    industry = industry,
    specialisations = specialisations,
    yearsExperienceMin = yearsExperienceMin,
    yearsExperienceMax = yearsExperienceMax,
    educationRequired = educationRequired,
    educationField = educationField,
    contractType = contractType,
)
