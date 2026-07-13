package com.kazisasa.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.kazisasa.app.data.repository.FeedFilters
import com.kazisasa.app.data.repository.SortOption
import com.kazisasa.app.di.AppContainer
import com.kazisasa.app.domain.model.Seniority
import com.kazisasa.app.ui.components.EmptyState
import com.kazisasa.app.ui.components.OpportunityCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(onOpenDetail: (String) -> Unit, onCreateProfile: () -> Unit) {
    val context = LocalContext.current
    val container = AppContainer.getInstance(context)
    val viewModel: FeedViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                FeedViewModel(
                    profileRepository = container.profileRepository,
                    opportunityRepository = container.opportunityRepository,
                    triageRepository = container.triageRepository,
                    savedRepository = container.savedRepository,
                    feedRepository = container.feedRepository,
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
                        Text("Kazi Sasa")
                        // recommendations doc §28: "every major screen should show the
                        // active profile label clearly" - "Viewing: X" is its suggested phrasing.
                        state.activeProfile?.let {
                            Text("Viewing: ${it.label}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // LinkedIn-structured search: keyword+location bar, then a filter
            // pill row (Date posted / Experience level / Job type / Remote /
            // Company / Industry) - same category mirror already built for the
            // shareable web site (kazi-sasa-feed's scripts/site/app.js), kept
            // in sync here deliberately. Works identically in browse and
            // profile-scored modes (general-search spec §7.4). Hidden only
            // during the initial loading spinner.
            if (!state.isLoading) {
                FeedFilterBar(state = state, onFiltersChange = { viewModel.setFilters(it) })
            }

            when {
                state.isLoading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }

                // v3: a missing profile is no longer a hard gate (general-search spec
                // §7.4/§2.3 - "search and the main feed must be fully functional with
                // zero profile set up"). Only show a genuinely-empty state when there's
                // also genuinely no feed data at all (not just no *filtered* results);
                // otherwise fall through to the list below.
                state.activeProfile == null && state.allItemCount == 0 -> EmptyState(
                    title = "No opportunities yet",
                    body = "Waiting on the first feed sync - pull to refresh, or check your connection.",
                    actionLabel = "Refresh",
                    onAction = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                )

                // Genuinely no data at all (not a filtered-to-zero result - that case
                // is handled separately below with a filter-specific message).
                state.allItemCount == 0 -> EmptyState(
                    title = "You're all caught up",
                    body = if (state.isSampleData) {
                        "Showing starter sample opportunities. Connect to the internet and refresh for current listings."
                    } else {
                        "No new opportunities to triage right now. Pull to refresh, or check Shortlist for what you've kept."
                    },
                    actionLabel = "Refresh",
                    onAction = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                )

                // Real data exists, but the current search/filter combination matched
                // nothing - distinct message + a way back out, rather than reusing the
                // "no opportunities" copy which would be misleading here.
                state.items.isEmpty() -> EmptyState(
                    title = "No matches for this search",
                    body = "Try clearing a filter or broadening your search term.",
                    actionLabel = "Clear filters",
                    onAction = { viewModel.clearFilters() },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { FeedStatusBanner(state, onRefresh = { viewModel.refresh() }) }

                    if (state.activeProfile == null) {
                        item { NoProfileBanner(onCreateProfile = onCreateProfile) }
                    }

                    if (state.digestEnabled && !state.digestDismissed && state.digestItems.isNotEmpty()) {
                        item { DigestBanner(state.digestItems.size, onDismiss = { viewModel.dismissDigest() }) }
                    }

                    // Results header: count + sort control, same structural spot
                    // as the web version's own results-header bar directly above
                    // its card list.
                    item {
                        ResultsHeader(
                            count = state.items.size,
                            sortBy = state.filters.sortBy,
                            onSortChange = { viewModel.setFilters(state.filters.copy(sortBy = it)) },
                        )
                    }

                    items(state.items, key = { it.opportunity.id }) { scored ->
                        OpportunityCard(
                            scored = scored,
                            onOpenDetail = { onOpenDetail(scored.opportunity.id) },
                            onKeep = { viewModel.keep(scored.opportunity.id) },
                            onSkip = { viewModel.skip(scored.opportunity.id) },
                            onSave = { viewModel.save(scored) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * LinkedIn-structured search bar: a two-box keyword+location row, then a
 * filter pill row below it (Date posted / Experience level / Job type /
 * Remote / Company / Industry). Every option is built dynamically from
 * [FeedUiState.availableFacets] - never a hardcoded list. Same bar renders
 * identically whether browsing without a profile or viewing the profile-
 * scored feed (general-search spec §7.4).
 */
@Composable
private fun FeedFilterBar(state: FeedUiState, onFiltersChange: (FeedFilters) -> Unit) {
    // Local echoes so typing feels instant - the ViewModel is still the
    // source of truth (resynced via remember(state.filters.X) on any
    // external change, e.g. Clear all), this just avoids a Flow round-trip
    // per keystroke before the TextField visually updates.
    var keyword by remember(state.filters.keyword) { mutableStateOf(state.filters.keyword) }
    var location by remember(state.filters.location) { mutableStateOf(state.filters.location) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = keyword,
                onValueChange = {
                    keyword = it
                    onFiltersChange(state.filters.copy(keyword = it))
                },
                placeholder = { Text("Job titles, skills, or companies") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = location,
                onValueChange = {
                    location = it
                    onFiltersChange(state.filters.copy(location = it))
                },
                placeholder = { Text("Location") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        val facets = state.availableFacets
        val hasFacets = facets.experience.isNotEmpty() || facets.jobType.isNotEmpty() ||
            facets.remote.isNotEmpty() || facets.company.isNotEmpty() || facets.industry.isNotEmpty()

        if (hasFacets) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    DatePostedChip(
                        selectedDays = state.filters.datePostedDays,
                        onSelect = { days -> onFiltersChange(state.filters.copy(datePostedDays = days)) },
                    )
                }
                if (facets.experience.isNotEmpty()) {
                    item {
                        FacetFilterChip(
                            label = "Experience level",
                            selectedCount = state.filters.experience.size,
                            options = facets.experience.map { experienceLabel(it) to it },
                            isSelected = { it in state.filters.experience },
                            onToggle = { value ->
                                val newSet = if (value in state.filters.experience) state.filters.experience - value else state.filters.experience + value
                                onFiltersChange(state.filters.copy(experience = newSet))
                            },
                        )
                    }
                }
                if (facets.jobType.isNotEmpty()) {
                    item {
                        FacetFilterChip(
                            label = "Job type",
                            selectedCount = state.filters.jobType.size,
                            options = facets.jobType.map { jobTypeLabel(it) to it },
                            isSelected = { it in state.filters.jobType },
                            onToggle = { value ->
                                val newSet = if (value in state.filters.jobType) state.filters.jobType - value else state.filters.jobType + value
                                onFiltersChange(state.filters.copy(jobType = newSet))
                            },
                        )
                    }
                }
                if (facets.remote.isNotEmpty()) {
                    item {
                        FacetFilterChip(
                            label = "On-site/remote",
                            selectedCount = state.filters.remote.size,
                            options = facets.remote.map { remoteLabel(it) to it },
                            isSelected = { it in state.filters.remote },
                            onToggle = { value ->
                                val newSet = if (value in state.filters.remote) state.filters.remote - value else state.filters.remote + value
                                onFiltersChange(state.filters.copy(remote = newSet))
                            },
                        )
                    }
                }
                if (facets.company.isNotEmpty()) {
                    item {
                        FacetFilterChip(
                            label = "Company",
                            selectedCount = state.filters.company.size,
                            options = facets.company.map { it to it },
                            isSelected = { it in state.filters.company },
                            onToggle = { value ->
                                val newSet = if (value in state.filters.company) state.filters.company - value else state.filters.company + value
                                onFiltersChange(state.filters.copy(company = newSet))
                            },
                        )
                    }
                }
                if (facets.industry.isNotEmpty()) {
                    item {
                        FacetFilterChip(
                            label = "Industry",
                            selectedCount = state.filters.industry.size,
                            options = facets.industry.map { humanize(it) to it },
                            isSelected = { it in state.filters.industry },
                            onToggle = { value ->
                                val newSet = if (value in state.filters.industry) state.filters.industry - value else state.filters.industry + value
                                onFiltersChange(state.filters.copy(industry = newSet))
                            },
                        )
                    }
                }
                if (state.filters.isActive) {
                    item {
                        TextButton(onClick = {
                            keyword = ""; location = ""
                            onFiltersChange(FeedFilters(sortBy = state.filters.sortBy)) // sort is not itself a filter - preserved across Clear all, same as the web version
                        }) {
                            Text("Clear (${state.filters.activeFacetCount})")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Results count + sort control, sitting directly above the card list - same
 * structural spot as the web version's own results-header. DEFAULT is never
 * labelled "Most relevant" here for the same honesty reason as the web
 * version: there's no real relevance ranking behind it in browse mode (see
 * SortOption's own doc comment in FeedFilters.kt for the profile-mode nuance).
 */
@Composable
private fun ResultsHeader(count: Int, sortBy: SortOption, onSortChange: (SortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$count ${if (count == 1) "result" else "results"}", style = MaterialTheme.typography.titleSmall)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(if (sortBy == SortOption.MOST_RECENT) "Most recent" else "Default order")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Default order") }, onClick = { onSortChange(SortOption.DEFAULT); expanded = false })
                DropdownMenuItem(text = { Text("Most recent") }, onClick = { onSortChange(SortOption.MOST_RECENT); expanded = false })
            }
        }
    }
}

/** Date posted - LinkedIn's own filter is single-select (Any time / Past 24
 * hours / Past week / Past month), so this uses radio buttons rather than
 * the checkbox pattern the other facet chips use. */
@Composable
private fun DatePostedChip(selectedDays: Int?, onSelect: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf<Pair<String, Int?>>("Any time" to null, "Past 24 hours" to 1, "Past week" to 7, "Past month" to 30)
    Box {
        FilterChip(
            selected = selectedDays != null,
            onClick = { expanded = true },
            label = { Text(options.firstOrNull { it.second == selectedDays }?.first ?: "Date posted") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, value) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { RadioButton(selected = selectedDays == value, onClick = { onSelect(value); expanded = false }) },
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}

/**
 * One multi-select facet dropdown, anchored to a [FilterChip]. Generic over
 * [T] so the same composable serves Experience level (an enum) and Job
 * type/Remote/Company/Industry (raw strings) without duplicating the
 * dropdown/checkbox plumbing five times.
 */
@Composable
private fun <T> FacetFilterChip(
    label: String,
    selectedCount: Int,
    options: List<Pair<String, T>>,
    isSelected: (T) -> Boolean,
    onToggle: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedCount > 0,
            onClick = { expanded = true },
            label = { Text(if (selectedCount > 0) "$label ($selectedCount)" else label) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (displayName, value) ->
                DropdownMenuItem(
                    text = { Text(displayName) },
                    leadingIcon = {
                        Checkbox(checked = isSelected(value), onCheckedChange = { onToggle(value) })
                    },
                    onClick = { onToggle(value) },
                )
            }
        }
    }
}

private fun experienceLabel(s: Seniority): String = when (s) {
    Seniority.ENTRY -> "Entry level"
    Seniority.MID -> "Associate"
    Seniority.SENIOR -> "Mid-Senior level"
    Seniority.LEADERSHIP -> "Director+"
}

// Mirrors kazi-sasa-feed/scripts/site/app.js's JOB_TYPE_LABELS exactly.
private fun jobTypeLabel(value: String): String = when (value) {
    "full_time" -> "Full-time"
    "part_time" -> "Part-time"
    "contract" -> "Contract"
    "temporary" -> "Temporary"
    "volunteer" -> "Volunteer"
    "internship" -> "Internship"
    "fellowship" -> "Fellowship"
    "grant" -> "Grant"
    "programme" -> "Programme"
    else -> humanize(value)
}

// Mirrors kazi-sasa-feed/scripts/site/app.js's REMOTE_LABELS exactly.
private fun remoteLabel(value: String): String = when (value) {
    "onsite" -> "On-site"
    "hybrid" -> "Hybrid"
    "remote" -> "Remote"
    else -> humanize(value)
}

private fun humanize(id: String): String =
    id.replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

/**
 * v3 addition - replaces the old hard "No profile yet" gate. Browsing works fully
 * without a profile (general-search spec §7.4); this is a dismissible-feeling
 * nudge, not a wall. Tapping it navigates to profile creation; the person can
 * also just keep scrolling and ignore it entirely.
 */
@Composable
private fun NoProfileBanner(onCreateProfile: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Browsing without a profile", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Set up a profile for personalised fit scores, shortlisting, and saved opportunities.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onCreateProfile) { Text("Set up") }
        }
    }
}

/**
 * Recommendations doc §29: the app must communicate live/cached/sample state,
 * last-updated time, and refresh failures in plain language - copy here follows
 * the doc's suggested examples closely.
 */
@Composable
private fun FeedStatusBanner(state: FeedUiState, onRefresh: () -> Unit) {
    val message = when {
        state.refreshErrorMessage != null -> state.refreshErrorMessage
        state.isSampleData -> "Starter data shown. Connect to the internet and refresh for current listings."
        state.feedMeta?.dataSource == "cache" -> "Offline mode: showing cached opportunities from the last successful update."
        state.feedMeta?.dataSource == "live" -> "Live feed updated just now."
        else -> null
    }
    if (message == null) return

    val isProblem = state.refreshErrorMessage != null || state.isSampleData
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                (if (isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.08f),
                RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(message, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        if (isProblem) TextButton(onClick = onRefresh) { Text("Refresh") }
    }
}

/** Recommendations doc §18 - the weekly digest, rendered as an always-available banner rather than a notification. */
@Composable
private fun DigestBanner(count: Int, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("This week's shortlist", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$count strong ${if (count == 1) "match" else "matches"} waiting below - worth a look even if you're short on time.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
