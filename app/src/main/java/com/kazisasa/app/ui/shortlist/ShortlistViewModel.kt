@file:OptIn(ExperimentalCoroutinesApi::class)

package com.kazisasa.app.ui.shortlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazisasa.app.data.repository.OpportunityRepository
import com.kazisasa.app.data.repository.ProfileRepository
import com.kazisasa.app.data.repository.SavedRepository
import com.kazisasa.app.data.repository.ScoredOpportunity
import com.kazisasa.app.data.repository.TriageRepository
import com.kazisasa.app.domain.model.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShortlistUiState(
    val activeProfile: Profile? = null,
    val items: List<ScoredOpportunity> = emptyList(),
)

/**
 * Recommendations doc §10: "if Keep changes the triage state and hides the
 * opportunity from the main feed, the user needs a place to find kept
 * opportunities. Without this, Keep becomes a black hole." This screen is that
 * place.
 */
class ShortlistViewModel(
    private val profileRepository: ProfileRepository,
    private val opportunityRepository: OpportunityRepository,
    private val triageRepository: TriageRepository,
    private val savedRepository: SavedRepository,
) : ViewModel() {

    val uiState: StateFlow<ShortlistUiState> = profileRepository.observeActiveProfile()
        .flatMapLatest { profile ->
            if (profile == null) {
                flowOf(ShortlistUiState())
            } else {
                opportunityRepository.observeScoredKeptFor(profile)
                    .map { items -> ShortlistUiState(activeProfile = profile, items = items) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShortlistUiState())

    /** Moving something back out of the shortlist without saving it - distinct from skip (doc §12: KEPT stays KEPT until acted on again). */
    fun moveToSaved(scored: ScoredOpportunity) = viewModelScope.launch {
        val profileId = uiState.value.activeProfile?.id ?: return@launch
        savedRepository.save(scored.opportunity, profileId)
    }

    fun skip(opportunityId: String) = viewModelScope.launch {
        val profileId = uiState.value.activeProfile?.id ?: return@launch
        triageRepository.skip(profileId, opportunityId)
    }
}
