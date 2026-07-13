package com.kazisasa.app.data.mapper

import com.kazisasa.app.data.local.entity.FitWeightsEmbedded
import com.kazisasa.app.data.local.entity.LocationPrefsEmbedded
import com.kazisasa.app.data.local.entity.ProfileEntity
import com.kazisasa.app.data.remote.dto.ProfileDto
import com.kazisasa.app.domain.model.FitWeights
import com.kazisasa.app.domain.model.LocationPrefs
import com.kazisasa.app.domain.model.Profile
import com.kazisasa.app.domain.model.Seniority
import com.kazisasa.app.domain.model.WorkMode

fun ProfileDto.toEntity(sortOrder: Int, isDefault: Boolean = true): ProfileEntity = ProfileEntity(
    id = id,
    label = label,
    targetLanes = targetLanes,
    coreSkills = coreSkills,
    seniority = runCatching { Seniority.valueOf(seniority.trim().uppercase()) }.getOrDefault(Seniority.MID),
    seniorityOpenness = seniorityOpenness,
    locationPrefs = LocationPrefsEmbedded(
        baseRegion = locationPrefs.baseRegion,
        acceptsRemoteKenya = locationPrefs.acceptsRemoteKenya,
        acceptsRegional = locationPrefs.acceptsRegional,
        acceptsInternational = locationPrefs.acceptsInternational,
        acceptsRelocation = locationPrefs.acceptsRelocation,
    ),
    modePrefs = modePrefs.mapNotNull { raw -> runCatching { WorkMode.valueOf(raw.trim().uppercase()) }.getOrNull() },
    weights = FitWeightsEmbedded(
        skillMatch = weights.skillMatch,
        sectorMatch = weights.sectorMatch,
        seniorityMatch = weights.seniorityMatch,
        locationFit = weights.locationFit,
        modeFit = weights.modeFit,
        growthSignal = weights.growthSignal,
        recency = weights.recency,
        deadlineRisk = weights.deadlineRisk,
    ),
    isDefault = isDefault,
    sortOrder = sortOrder,
)

fun ProfileEntity.toDomain(): Profile = Profile(
    id = id,
    label = label,
    targetLanes = targetLanes,
    coreSkills = coreSkills,
    seniority = seniority,
    seniorityOpenness = seniorityOpenness,
    locationPrefs = LocationPrefs(
        baseRegion = locationPrefs.baseRegion,
        acceptsRemoteKenya = locationPrefs.acceptsRemoteKenya,
        acceptsRegional = locationPrefs.acceptsRegional,
        acceptsInternational = locationPrefs.acceptsInternational,
        acceptsRelocation = locationPrefs.acceptsRelocation,
    ),
    modePrefs = modePrefs,
    weights = FitWeights(
        skillMatch = weights.skillMatch,
        sectorMatch = weights.sectorMatch,
        seniorityMatch = weights.seniorityMatch,
        locationFit = weights.locationFit,
        modeFit = weights.modeFit,
        growthSignal = weights.growthSignal,
        recency = weights.recency,
        deadlineRisk = weights.deadlineRisk,
    ),
    isDefault = isDefault,
)

fun Profile.toEntity(sortOrder: Int): ProfileEntity = ProfileEntity(
    id = id,
    label = label,
    targetLanes = targetLanes,
    coreSkills = coreSkills,
    seniority = seniority,
    seniorityOpenness = seniorityOpenness,
    locationPrefs = LocationPrefsEmbedded(
        locationPrefs.baseRegion, locationPrefs.acceptsRemoteKenya,
        locationPrefs.acceptsRegional, locationPrefs.acceptsInternational, locationPrefs.acceptsRelocation,
    ),
    modePrefs = modePrefs,
    weights = FitWeightsEmbedded(
        weights.skillMatch, weights.sectorMatch, weights.seniorityMatch, weights.locationFit,
        weights.modeFit, weights.growthSignal, weights.recency, weights.deadlineRisk,
    ),
    isDefault = isDefault,
    sortOrder = sortOrder,
)
