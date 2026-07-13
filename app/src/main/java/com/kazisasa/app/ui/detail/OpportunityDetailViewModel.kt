package com.kazisasa.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazisasa.app.data.local.entity.ReminderEntity
import com.kazisasa.app.data.repository.OpportunityRepository
import com.kazisasa.app.data.repository.ProfileRepository
import com.kazisasa.app.data.repository.ReminderRepository
import com.kazisasa.app.data.repository.SavedRepository
import com.kazisasa.app.domain.model.FitBreakdown
import com.kazisasa.app.domain.model.Opportunity
import com.kazisasa.app.domain.model.Profile
import com.kazisasa.app.work.ScheduleReminderResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class DetailUiState(
    val opportunity: Opportunity? = null,
    val fit: FitBreakdown? = null,
    val isSaved: Boolean = false,
    val reminder: ReminderEntity? = null,
    val lastReminderResult: ScheduleReminderResult? = null,
)

/**
 * The "expanded fit-reason view" called out in spec §20's handoff expectations -
 * unlike the feed card (top 1 reason), this screen surfaces every reason and every
 * caution, because this is where the person actually decides whether to apply.
 *
 * Save/reminder state is scoped to the *active* profile throughout (recommendations
 * doc §11) - isSaved and reminder both re-derive via flatMapLatest whenever the
 * active profile changes, rather than being fetched once for whichever profile
 * happened to be active when the screen opened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpportunityDetailViewModel(
    private val opportunityId: String,
    private val opportunityRepository: OpportunityRepository,
    private val profileRepository: ProfileRepository,
    private val savedRepository: SavedRepository,
    private val reminderRepository: ReminderRepository,
) : ViewModel() {

    private val lastResult = MutableStateFlow<ScheduleReminderResult?>(null)

    private val activeProfileId: StateFlow<String?> = profileRepository.observeActiveProfile()
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val profileScopedState = profileRepository.observeActiveProfile().flatMapLatest { profile ->
        if (profile == null) {
            kotlinx.coroutines.flow.flowOf(Pair(false, null as ReminderEntity?))
        } else {
            combine(
                savedRepository.observeIsSaved(profile.id, opportunityId),
                reminderRepository.observeFor(profile.id, opportunityId),
            ) { isSaved, reminder -> Pair(isSaved, reminder) }
        }
    }

    val uiState: StateFlow<DetailUiState> = combine(
        opportunityRepository.observeById(opportunityId),
        profileRepository.observeActiveProfile(),
        profileScopedState,
        lastResult,
    ) { opportunity: Opportunity?, profile: Profile?, scoped: Pair<Boolean, ReminderEntity?>, result: ScheduleReminderResult? ->
        val fit = if (opportunity != null && profile != null) {
            opportunityRepository.scoreFor(opportunity, profile)
        } else {
            null
        }
        DetailUiState(opportunity, fit, scoped.first, scoped.second, result)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailUiState())

    fun toggleSave() = viewModelScope.launch {
        val opportunity = uiState.value.opportunity ?: return@launch
        val profileId = activeProfileId.value ?: return@launch
        if (uiState.value.isSaved) {
            savedRepository.unsave(profileId, opportunity.id)
        } else {
            savedRepository.save(opportunity, profileId)
        }
    }

    /**
     * Called only after the caller has confirmed notification permission is
     * granted (or the platform doesn't require it) - see OpportunityDetailScreen's
     * permission launcher. This function itself makes no permission request; it
     * just schedules, and ScheduleReminderResult.PermissionDenied is still a
     * possible outcome if the user denies the just-shown system prompt.
     */
    fun setReminder() = viewModelScope.launch {
        val opportunity = uiState.value.opportunity ?: return@launch
        val profileId = activeProfileId.value ?: return@launch
        lastResult.value = reminderRepository.setReminder(opportunity, profileId)
    }

    fun cancelReminder() = viewModelScope.launch {
        val profileId = activeProfileId.value ?: return@launch
        reminderRepository.cancelReminder(profileId, opportunityId)
        lastResult.value = null
    }

    fun hasNotificationPermission(): Boolean = reminderRepository.hasNotificationPermission()
}
