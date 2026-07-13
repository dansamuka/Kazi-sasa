package com.kazisasa.app.data.local

import androidx.room.TypeConverter
import com.kazisasa.app.domain.model.DeadlineConfidence
import com.kazisasa.app.domain.model.LocationScope
import com.kazisasa.app.domain.model.OpportunityFlag
import com.kazisasa.app.domain.model.OpportunityType
import com.kazisasa.app.domain.model.OrgType
import com.kazisasa.app.domain.model.Seniority
import com.kazisasa.app.domain.model.SourceConfidence
import com.kazisasa.app.domain.model.TriageAction
import com.kazisasa.app.domain.model.WorkMode
import com.kazisasa.app.domain.model.ContractType
import com.kazisasa.app.domain.model.EducationLevel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Explicit converters (rather than relying on Room's newer implicit-enum support)
 * so this compiles the same way regardless of the exact Room version pinned in
 * gradle/libs.versions.toml - one less thing to debug on first sync.
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- List<String> ----
    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else json.decodeFromString(value)

    // ---- List<WorkMode> ----
    @TypeConverter
    fun fromWorkModeList(value: List<WorkMode>): String = json.encodeToString(value.map { it.name })

    @TypeConverter
    fun toWorkModeList(value: String): List<WorkMode> =
        if (value.isBlank()) emptyList() else json.decodeFromString<List<String>>(value).map { WorkMode.valueOf(it) }

    // ---- List<OpportunityFlag> ----
    @TypeConverter
    fun fromFlagList(value: List<OpportunityFlag>): String = json.encodeToString(value.map { it.name })

    @TypeConverter
    fun toFlagList(value: String): List<OpportunityFlag> =
        if (value.isBlank()) emptyList() else json.decodeFromString<List<String>>(value).map { OpportunityFlag.valueOf(it) }

    // ---- Nullable single enums ----
    @TypeConverter fun fromOpportunityType(v: OpportunityType): String = v.name
    @TypeConverter fun toOpportunityType(v: String): OpportunityType = OpportunityType.valueOf(v)

    @TypeConverter fun fromOrgType(v: OrgType): String = v.name
    @TypeConverter fun toOrgType(v: String): OrgType = OrgType.valueOf(v)

    @TypeConverter fun fromWorkModeNullable(v: WorkMode?): String? = v?.name
    @TypeConverter fun toWorkModeNullable(v: String?): WorkMode? = v?.let { WorkMode.valueOf(it) }

    @TypeConverter fun fromSeniorityNullable(v: Seniority?): String? = v?.name
    @TypeConverter fun toSeniorityNullable(v: String?): Seniority? = v?.let { Seniority.valueOf(it) }

    @TypeConverter fun fromSeniority(v: Seniority): String = v.name
    @TypeConverter fun toSeniority(v: String): Seniority = Seniority.valueOf(v)

    @TypeConverter fun fromLocationScopeNullable(v: LocationScope?): String? = v?.name
    @TypeConverter fun toLocationScopeNullable(v: String?): LocationScope? = v?.let { LocationScope.valueOf(it) }

    @TypeConverter fun fromSourceConfidence(v: SourceConfidence): String = v.name
    @TypeConverter fun toSourceConfidence(v: String): SourceConfidence = SourceConfidence.valueOf(v)

    @TypeConverter fun fromDeadlineConfidence(v: DeadlineConfidence): String = v.name
    @TypeConverter fun toDeadlineConfidence(v: String): DeadlineConfidence = DeadlineConfidence.valueOf(v)

    @TypeConverter fun fromTriageAction(v: TriageAction): String = v.name
    @TypeConverter fun toTriageAction(v: String): TriageAction = TriageAction.valueOf(v)

    // ---- v3 additions ----
    @TypeConverter fun fromContractTypeNullable(v: ContractType?): String? = v?.name
    @TypeConverter fun toContractTypeNullable(v: String?): ContractType? = v?.let { ContractType.valueOf(it) }

    @TypeConverter fun fromEducationLevelNullable(v: EducationLevel?): String? = v?.name
    @TypeConverter fun toEducationLevelNullable(v: String?): EducationLevel? = v?.let { EducationLevel.valueOf(it) }
}
