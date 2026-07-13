package com.kazisasa.app.ui.shortlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.kazisasa.app.di.AppContainer
import com.kazisasa.app.ui.components.EmptyState
import com.kazisasa.app.ui.components.FitBandChip
import com.kazisasa.app.ui.components.SourceConfidenceBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortlistScreen(onOpenDetail: (String) -> Unit, onOpenSkipped: () -> Unit) {
    val context = LocalContext.current
    val container = AppContainer.getInstance(context)
    val viewModel: ShortlistViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ShortlistViewModel(
                    profileRepository = container.profileRepository,
                    opportunityRepository = container.opportunityRepository,
                    triageRepository = container.triageRepository,
                    savedRepository = container.savedRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Shortlist")
                        state.activeProfile?.let { Text(it.label, style = MaterialTheme.typography.labelMedium) }
                    }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = onOpenSkipped) { Text("Skipped") }
                },
            )
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyState(
                title = "Nothing shortlisted yet",
                body = "Tap Shortlist on a card in For You to keep it here for later review.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = { it.opportunity.id }) { scored ->
                    Card(onClick = { onOpenDetail(scored.opportunity.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Shortlist only ever renders items from observeScoredKeptFor(profile),
                            // which never runs without a real profile - fit is always non-null in
                            // practice here. Safe call + let rather than !! anyway, since a crash
                            // here would be a genuinely bad user experience for a null the type
                            // system asks us to consider even though this call site can't hit it.
                            scored.fit?.band?.let { FitBandChip(it) }
                            Text(
                                scored.opportunity.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(scored.opportunity.organisation.name, style = MaterialTheme.typography.bodyMedium)
                            Row(modifier = Modifier.padding(top = 8.dp)) {
                                SourceConfidenceBadge(scored.opportunity.source.confidence)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.skip(scored.opportunity.id) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Remove") }
                                OutlinedButton(
                                    onClick = { viewModel.moveToSaved(scored) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Save") }
                            }
                        }
                    }
                }
            }
        }
    }
}
