package com.kazisasa.app.ui.saved

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kazisasa.app.di.AppContainer
import com.kazisasa.app.ui.components.EmptyState
import com.kazisasa.app.ui.components.SourceConfidenceBadge

/** Now a bottom-nav tab (recommendations doc §10's suggested nav), not a pushed screen - no back button needed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(onOpenDetail: (String) -> Unit) {
    val context = LocalContext.current
    val container = AppContainer.getInstance(context)
    val viewModel: SavedViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SavedViewModel(container.profileRepository, container.savedRepository) }
        },
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Saved")
                        state.activeProfile?.let { Text(it.label, style = MaterialTheme.typography.labelMedium) }
                    }
                },
            )
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyState(
                title = "No saved opportunities yet",
                body = "Opportunities you save show up here, even if they're later removed from the live feed.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.opportunity.id }) { saved ->
                    Card(
                        onClick = { onOpenDetail(saved.opportunity.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(saved.opportunity.title, style = MaterialTheme.typography.titleMedium)
                            Text(saved.opportunity.organisation.name, style = MaterialTheme.typography.bodyMedium)
                            Row(modifier = Modifier.padding(top = 8.dp)) {
                                SourceConfidenceBadge(saved.opportunity.source.confidence)
                            }
                            if (!saved.stillInLiveFeed) {
                                Text(
                                    "No longer in the live feed - verify details at the source before applying.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
