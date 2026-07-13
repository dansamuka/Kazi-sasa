package com.kazisasa.app.ui.profile

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.kazisasa.app.di.AppContainer
import com.kazisasa.app.domain.model.Seniority
import kotlinx.coroutines.launch

/** Now a bottom-nav tab (recommendations doc §10's suggested nav) - selecting a profile switches it in place rather than navigating away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitcherScreen() {
    val context = LocalContext.current
    val container = AppContainer.getInstance(context)
    val viewModel: ProfileSwitcherViewModel = viewModel(
        factory = viewModelFactory { initializer { ProfileSwitcherViewModel(container.profileRepository) } },
    )
    val state by viewModel.uiState.collectAsState()
    var showCreateForm by remember { mutableStateOf(state.profiles.isEmpty()) }
    val digestEnabled by container.profileRepository.observeWeeklyDigestEnabled().collectAsState(initial = true)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profiles") }) },
    ) { padding ->
        // Bug fix: CreateProfileForm used to render as a sibling Column below this
        // LazyColumn, inside a non-scrolling outer Column - once the LazyColumn
        // claimed the available height, the form (title, 4 text fields, seniority
        // radio group, checkbox, Create/Cancel buttons) had nowhere to scroll to,
        // so the Create button was frequently off-screen and unreachable. Moving
        // the form to be an item *inside* this same LazyColumn means the whole
        // screen scrolls as one list - the form is now always fully reachable
        // regardless of screen size or how many profiles already exist.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.profiles, key = { it.id }) { profile ->
                Card(
                    onClick = { viewModel.selectProfile(profile.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(profile.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                profile.targetLanes.joinToString(", ") { it.replace('_', ' ') },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        RadioButton(
                            selected = profile.id == state.activeProfileId,
                            onClick = { viewModel.selectProfile(profile.id) },
                        )
                    }
                }
            }

            if (!showCreateForm) {
                item {
                    TextButton(onClick = { showCreateForm = true }) { Text("+ Add another profile") }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }

            if (showCreateForm) {
                item {
                    CreateProfileForm(
                        onCreate = { label, lanes, skills, seniority, region, remoteKenya ->
                            viewModel.createProfile(label, lanes, skills, seniority, region, remoteKenya)
                            showCreateForm = false
                        },
                        onCancel = { if (state.profiles.isNotEmpty()) showCreateForm = false },
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
            }

            // Recommendations doc §18: the digest banner has a per-session "Dismiss"
            // in FeedScreen; this is the persistent on/off switch for it.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Weekly shortlist digest", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "A banner on For You highlighting your strongest untriaged matches - useful if you've turned off notifications.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = digestEnabled,
                        onCheckedChange = { enabled -> scope.launch { container.profileRepository.setWeeklyDigestEnabled(enabled) } },
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }

            // Recommendations doc §33's suggested copy, verbatim.
            item {
                Column {
                    Text("Privacy", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Kazi Sasa stores profiles, saved roles, triage decisions, and reminders on your device. " +
                            "The app fetches opportunity data from a public GitHub-hosted feed. It does not submit " +
                            "applications, collect payments, or send your CV to employers.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateProfileForm(
    onCreate: (String, String, String, Seniority, String, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var lanes by remember { mutableStateOf("") }
    var skills by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("Nairobi") }
    var seniority by remember { mutableStateOf(Seniority.MID) }
    var remoteKenya by remember { mutableStateOf(true) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("New profile", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = label, onValueChange = { label = it },
            label = { Text("Profile name (e.g. Climate & Development Finance)") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = lanes, onValueChange = { lanes = it },
            label = { Text("Target sectors, comma-separated") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = skills, onValueChange = { skills = it },
            label = { Text("Core skills, comma-separated") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = region, onValueChange = { region = it },
            label = { Text("Base region") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        Text("Seniority", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Seniority.entries.forEach { level ->
            Row {
                RadioButton(selected = seniority == level, onClick = { seniority = level })
                Text(level.name.lowercase().replaceFirstChar { it.uppercase() }, modifier = Modifier.padding(top = 12.dp))
            }
        }

        Row(modifier = Modifier.padding(top = 8.dp)) {
            Checkbox(checked = remoteKenya, onCheckedChange = { remoteKenya = it })
            Text("Open to remote-from-Kenya roles", modifier = Modifier.padding(top = 12.dp))
        }

        Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { onCreate(label, lanes, skills, seniority, region, remoteKenya) }) { Text("Create") }
        }
    }
}
