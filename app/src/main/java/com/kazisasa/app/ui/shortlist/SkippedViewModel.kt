@file:OptIn(ExperimentalCoroutinesApi::class)

package com.kazisasa.app.ui.shortlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazisasa.app.data.repository.OpportunityRepository
import com.kazisasa.app.data.repository.ProfileRepository
import com.kazisasa.app.data.repository.TriageRepository
import com.kazisasa.app.domain.model.Opportunity
import com.kazisasa.app.domain.model.Profile
import com.kazisasa.app.domain.model.TriageAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SkippedUiState(val activeProfile: Profile? = null, val items: List<Opportunity> = emptyList())

/**
 * Recommendations doc §14: "a user needs a visible route to recover skipped
 * opportunities... without clearing app data." Per-item Restore, plus a
 * Restore-all for when someone skipped a whole batch by mistake.
 */
class SkippedViewModel(
    private val profileRepository: ProfileRepository,
    private val opportunityRepository: OpportunityRepository,
    private val triageRepository: TriageRepository,
) : ViewModel() {

    val uiState: StateFlow<SkippedUiState> = profileRepository.observeActiveProfile()
        .flatMapLatest { profile ->
            if (profile == null) {
                flowOf(SkippedUiState())
            } else {
                opportunityRepository.observeSkippedFor(profile.id)
                    .map { items -> SkippedUiState(activeProfile = profile, items = items) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SkippedUiState())

    fun restore(opportunityId: String) = viewModelScope.launch {
        val profileId = uiState.value.activeProfile?.id ?: return@launch
        // Restoring a single item to UNSEEN, not KEPT - it goes back to the main
        // feed to be triaged again, rather than jumping straight to the shortlist.
        triageRepository.setAction(profileId, opportunityId, TriageAction.UNSEEN)
    }

    fun restoreAll() = viewModelScope.launch {
        val profileId = uiState.value.activeProfile?.id ?: return@launch
        triageRepository.recoverSkipped(profileId)
    }
}
