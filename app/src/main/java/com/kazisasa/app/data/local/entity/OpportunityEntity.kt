package com.kazisasa.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kazisasa.app.domain.model.DeadlineConfidence
import com.kazisasa.app.domain.model.LocationScope
import com.kazisasa.app.domain.model.OpportunityFlag
import com.kazisasa.app.domain.model.OpportunityType
import com.kazisasa.app.domain.model.OrgType
import com.kazisasa.app.domain.model.Seniority
import com.kazisasa.app.domain.model.SourceConfidence
import com.kazisasa.app.domain.model.WorkMode
import com.kazisasa.app.domain.model.ContractType
import com.kazisasa.app.domain.model.EducationLevel

/**
 * Local cache of a feed opportunity (spec §26). Mirrors the feed contract (§24.3)
 * closely so mapping is close to 1:1 — see data/mapper/OpportunityMappers.kt.
 *
 * [id] is stable across feed regenerations (spec §24.4: hash of source URL + title),
 * which is what lets saves/triage/reminders survive a refresh.
 */
@Entity(tableName = "opportunities")
data class OpportunityEntity(
    @PrimaryKey val id: String,
    val title: String,
    val opportunityType: OpportunityType,
    @Embedded(prefix = "org_") val organisation: OrganisationEmbedded,
    @Embedded(prefix = "loc_") val location: LocationEmbedded,
    val workMode: WorkMode?,
    val seniority: Seniority?,
    val categories: List<String>,
    val skillsRequired: List<String>,
    val skillsPreferred: List<String>,
    val postedAtMillis: Long?,
    val deadlineMillis: Long?,
    val deadlineConfidence: DeadlineConfidence,
    @Embedded(prefix = "comp_") val compensation: CompensationEmbedded?,
    @Embedded(prefix = "src_") val source: SourceEmbedded,
    val applyUrl: String?,
    val applyIsOfficial: Boolean,
    val flags: List<OpportunityFlag>,
    val eligibilityNotes: String?,
    val summary: String?,
    val rawDescriptionUrl: String?,
    /** When this row was last written by a feed sync — drives cache-staleness UI (§7.7). */
    val fetchedAt: Long,
    // ---- v3 additions - see Migrations.kt MIGRATION_1_2 for the schema
    // change that added these columns, and OpportunityMappers.kt for how
    // they're populated. All nullable/empty-default so existing rows from
    // a v1 database read back fine after the migration. ----
    val industry: String? = null,
    val specialisations: List<String> = emptyList(),
    val yearsExperienceMin: Int? = null,
    val yearsExperienceMax: Int? = null,
    val educationRequired: EducationLevel? = null,
    val educationField: List<String> = emptyList(),
    val contractType: ContractType? = null,
)

data class OrganisationEmbedded(
    val name: String,
    val type: OrgType,
    val verified: Boolean,
)

data class LocationEmbedded(
    val raw: String?,
    val country: String?,
    val region: String?,
    val isRemoteFromKenya: Boolean,
    val scope: LocationScope?,
    val relocationCountry: String?,
)

data class CompensationEmbedded(
    val min: Int?,
    val max: Int?,
    val currency: String?,
    val period: String?,
    val disclosed: Boolean,
)

data class SourceEmbedded(
    val name: String,
    val url: String,
    val confidence: SourceConfidence,
    val lastSeenAtMillis: Long?,
)
