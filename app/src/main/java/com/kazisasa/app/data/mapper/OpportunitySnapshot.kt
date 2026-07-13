package com.kazisasa.app.data.mapper

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Deliberately its own serialisable shape rather than reusing OpportunityEntity or
 * putting @Serializable on the domain model directly (spec §24.5). This keeps:
 *   - Room entities Room-only (no serialization annotations mixed in)
 *   - the domain model framework-free (no serialization annotations either)
 * at the cost of one small mirror class, kept here in the mapper layer where
 * format-conversion code belongs.
 */
@Serializable
private data class OpportunitySnapshotDto(
    val id: String,
    val title: String,
    val opportunityType: String,
    val orgName: String,
    val orgType: String,
    val orgVerified: Boolean,
    val locationRaw: String? = null,
    val country: String? = null,
    val region: String? = null,
    val isRemoteFromKenya: Boolean = false,
    val locationScope: String? = null,
    val relocationCountry: String? = null,
    val workMode: String? = null,
    val seniority: String? = null,
    val categories: List<String> = emptyList(),
    val skillsRequired: List<String> = emptyList(),
    val skillsPreferred: List<String> = emptyList(),
    val postedAtMillis: Long? = null,
    val deadlineMillis: Long? = null,
    val deadlineConfidence: String = "UNKNOWN",
    val compMin: Int? = null,
    val compMax: Int? = null,
    val compCurrency: String? = null,
    val compPeriod: String? = null,
    val compDisclosed: Boolean = false,
    val sourceName: String,
    val sourceUrl: String,
    val sourceConfidence: String,
    val sourceLastSeenAtMillis: Long? = null,
    val applyUrl: String? = null,
    val applyIsOfficial: Boolean = false,
    val flags: List<String> = emptyList(),
    val eligibilityNotes: String? = null,
    val summary: String? = null,
    val rawDescriptionUrl: String? = null,
    // v3 additions - default-valued so a snapshot saved before this change
    // still deserialises fine (ignoreUnknownKeys/missing-key defaults both
    // apply here since snapshotJson has ignoreUnknownKeys = true).
    val industry: String? = null,
    val specialisations: List<String> = emptyList(),
    val yearsExperienceMin: Int? = null,
    val yearsExperienceMax: Int? = null,
    val educationRequired: String? = null,
    val educationField: List<String> = emptyList(),
    val contractType: String? = null,
)

private val snapshotJson = Json { ignoreUnknownKeys = true }

fun Opportunity.toSnapshotJson(): String {
    val dto = OpportunitySnapshotDto(
        id = id, title = title, opportunityType = opportunityType.name,
        orgName = organisation.name, orgType = organisation.type.name, orgVerified = organisation.verified,
        locationRaw = location.raw, country = location.country, region = location.region,
        isRemoteFromKenya = location.isRemoteFromKenya, locationScope = location.scope?.name,
        relocationCountry = location.relocationCountry,
        workMode = workMode?.name, seniority = seniority?.name,
        categories = categories, skillsRequired = skillsRequired, skillsPreferred = skillsPreferred,
        postedAtMillis = postedAtMillis, deadlineMillis = deadlineMillis,
        deadlineConfidence = deadlineConfidence.name,
        compMin = compensation?.min, compMax = compensation?.max, compCurrency = compensation?.currency,
        compPeriod = compensation?.period, compDisclosed = compensation?.disclosed ?: false,
        sourceName = source.name, sourceUrl = source.url, sourceConfidence = source.confidence.name,
        sourceLastSeenAtMillis = source.lastSeenAtMillis,
        applyUrl = applyUrl, applyIsOfficial = applyIsOfficial,
        flags = flags.map { it.name }, eligibilityNotes = eligibilityNotes,
        summary = summary, rawDescriptionUrl = rawDescriptionUrl,
        industry = industry, specialisations = specialisations,
        yearsExperienceMin = yearsExperienceMin, yearsExperienceMax = yearsExperienceMax,
        educationRequired = educationRequired?.name, educationField = educationField,
        contractType = contractType?.name,
    )
    return snapshotJson.encodeToString(dto)
}

fun String.fromSnapshotJsonToOpportunity(): Opportunity {
    val d = snapshotJson.decodeFromString<OpportunitySnapshotDto>(this)
    return Opportunity(
        id = d.id, title = d.title,
        opportunityType = runCatching { OpportunityType.valueOf(d.opportunityType) }.getOrDefault(OpportunityType.JOB),
        organisation = Organisation(
            d.orgName,
            runCatching { OrgType.valueOf(d.orgType) }.getOrDefault(OrgType.UNVERIFIED),
            d.orgVerified,
        ),
        location = LocationInfo(
            d.locationRaw, d.country, d.region, d.isRemoteFromKenya,
            d.locationScope?.let { runCatching { LocationScope.valueOf(it) }.getOrNull() },
            d.relocationCountry,
        ),
        workMode = d.workMode?.let { runCatching { WorkMode.valueOf(it) }.getOrNull() },
        seniority = d.seniority?.let { runCatching { Seniority.valueOf(it) }.getOrNull() },
        categories = d.categories, skillsRequired = d.skillsRequired, skillsPreferred = d.skillsPreferred,
        postedAtMillis = d.postedAtMillis, deadlineMillis = d.deadlineMillis,
        deadlineConfidence = runCatching { DeadlineConfidence.valueOf(d.deadlineConfidence) }.getOrDefault(DeadlineConfidence.UNKNOWN),
        compensation = if (d.compMin != null || d.compMax != null || d.compDisclosed) {
            Compensation(d.compMin, d.compMax, d.compCurrency, d.compPeriod, d.compDisclosed)
        } else null,
        source = SourceInfo(
            d.sourceName, d.sourceUrl,
            runCatching { SourceConfidence.valueOf(d.sourceConfidence) }.getOrDefault(SourceConfidence.UNVERIFIED),
            d.sourceLastSeenAtMillis,
        ),
        applyUrl = d.applyUrl, applyIsOfficial = d.applyIsOfficial,
        flags = d.flags.mapNotNull { runCatching { OpportunityFlag.valueOf(it) }.getOrNull() },
        eligibilityNotes = d.eligibilityNotes, summary = d.summary, rawDescriptionUrl = d.rawDescriptionUrl,
        industry = d.industry, specialisations = d.specialisations,
        yearsExperienceMin = d.yearsExperienceMin, yearsExperienceMax = d.yearsExperienceMax,
        educationRequired = d.educationRequired?.let { runCatching { EducationLevel.valueOf(it) }.getOrNull() },
        educationField = d.educationField,
        contractType = d.contractType?.let { runCatching { ContractType.valueOf(it) }.getOrNull() },
    )
}
