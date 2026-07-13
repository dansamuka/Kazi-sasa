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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkippedScreen(onOpenDetail: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = AppContainer.getInstance(context)
    val viewModel: SkippedViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SkippedViewModel(
                    profileRepository = container.profileRepository,
                    opportunityRepository = container.opportunityRepository,
                    triageRepository = container.triageRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skipped") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    if (state.items.isNotEmpty()) {
                        TextButton(onClick = { viewModel.restoreAll() }) { Text("Restore all") }
                    }
                },
            )
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyState(
                title = "No skipped opportunities",
                body = "Anything you skip from For You shows up here, so nothing is ever permanently gone by accident.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.id }) { opportunity ->
                    Card(onClick = { onOpenDetail(opportunity.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(opportunity.title, style = MaterialTheme.typography.titleMedium)
                                Text(opportunity.organisation.name, style = MaterialTheme.typography.bodyMedium)
                            }
                            OutlinedButton(onClick = { viewModel.restore(opportunity.id) }) { Text("Restore") }
                        }
                    }
                }
            }
        }
    }
}
