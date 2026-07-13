@file:OptIn(ExperimentalCoroutinesApi::class)

package com.kazisasa.app.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazisasa.app.data.repository.ProfileRepository
import com.kazisasa.app.data.repository.SavedItem
import com.kazisasa.app.data.repository.SavedRepository
import com.kazisasa.app.domain.model.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SavedUiState(val activeProfile: Profile? = null, val items: List<SavedItem> = emptyList())

/** Profile-scoped (recommendations doc §11) - each profile sees only what it saved. */
class SavedViewModel(
    private val profileRepository: ProfileRepository,
    private val savedRepository: SavedRepository,
) : ViewModel() {

    val uiState: StateFlow<SavedUiState> = profileRepository.observeActiveProfile()
        .flatMapLatest { profile ->
            if (profile == null) {
                flowOf(SavedUiState())
            } else {
                savedRepository.observeAllFor(profile.id).map { items -> SavedUiState(profile, items) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SavedUiState())

    fun unsave(opportunityId: String) = viewModelScope.launch {
        val profileId = uiState.value.activeProfile?.id ?: return@launch
        savedRepository.unsave(profileId, opportunityId)
    }
}
