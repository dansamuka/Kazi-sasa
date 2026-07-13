package com.kazisasa.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kazisasa.app.domain.model.Seniority
import com.kazisasa.app.domain.model.WorkMode

/** Local record of a career profile (spec §7.1, §25). */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val label: String,
    val targetLanes: List<String>,
    val coreSkills: List<String>,
    val seniority: Seniority,
    val seniorityOpenness: Int,
    @Embedded(prefix = "loc_") val locationPrefs: LocationPrefsEmbedded,
    val modePrefs: List<WorkMode>,
    @Embedded(prefix = "w_") val weights: FitWeightsEmbedded,
    val isDefault: Boolean,
    val sortOrder: Int,
)

data class LocationPrefsEmbedded(
    val baseRegion: String?,
    val acceptsRemoteKenya: Boolean,
    val acceptsRegional: Boolean,
    val acceptsInternational: Boolean,
    val acceptsRelocation: Boolean,
)

data class FitWeightsEmbedded(
    val skillMatch: Float,
    val sectorMatch: Float,
    val seniorityMatch: Float,
    val locationFit: Float,
    val modeFit: Float,
    val growthSignal: Float,
    val recency: Float,
    val deadlineRisk: Float,
)
