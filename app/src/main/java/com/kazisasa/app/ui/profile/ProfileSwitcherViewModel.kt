package com.kazisasa.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazisasa.app.data.repository.ProfileRepository
import com.kazisasa.app.domain.model.FitWeights
import com.kazisasa.app.domain.model.LocationPrefs
import com.kazisasa.app.domain.model.Profile
import com.kazisasa.app.domain.model.Seniority
import com.kazisasa.app.domain.model.WorkMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ProfileListUiState(
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: String? = null,
)

class ProfileSwitcherViewModel(private val profileRepository: ProfileRepository) : ViewModel() {

    val uiState: StateFlow<ProfileListUiState> = combine(
        profileRepository.observeAll(),
        profileRepository.observeActiveProfile(),
    ) { profiles, active ->
        ProfileListUiState(profiles, active?.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileListUiState())

    fun selectProfile(profileId: String) = viewModelScope.launch {
        profileRepository.setActiveProfile(profileId)
    }

    /**
     * Minimal creation path - spec §22 explicitly excludes a full profile-builder
     * wizard from v1 non-goals territory (it's not listed as excluded, but a rich
     * wizard is squarely a design/UX task, not an architecture one). Comma-separated
     * text in is deliberately simple; the designer's profile-creation screen should
     * replace this with proper chip/multi-select inputs.
     */
    fun createProfile(
        label: String,
        targetLanesCsv: String,
        coreSkillsCsv: String,
        seniority: Seniority,
        baseRegion: String,
        acceptsRemoteKenya: Boolean,
    ) = viewModelScope.launch {
        val newProfile = Profile(
            id = "profile_${UUID.randomUUID()}",
            label = label.ifBlank { "New profile" },
            targetLanes = splitCsv(targetLanesCsv),
            coreSkills = splitCsv(coreSkillsCsv),
            seniority = seniority,
            seniorityOpenness = 1,
            locationPrefs = LocationPrefs(
                baseRegion = baseRegion.ifBlank { null },
                acceptsRemoteKenya = acceptsRemoteKenya,
                acceptsRegional = false,
                acceptsInternational = false,
                acceptsRelocation = false,
            ),
            modePrefs = listOfNotNull(WorkMode.HYBRID, WorkMode.ONSITE, if (acceptsRemoteKenya) WorkMode.REMOTE_KENYA else null),
            weights = FitWeights(),
            isDefault = false,
        )
        val sortOrder = uiState.value.profiles.size
        profileRepository.upsert(newProfile, sortOrder)
        profileRepository.setActiveProfile(newProfile.id)
    }

    private fun splitCsv(value: String): List<String> =
        value.split(",").map { it.trim().lowercase().replace(' ', '_') }.filter { it.isNotBlank() }
}
