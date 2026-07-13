package com.kazisasa.app.ui.detail

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.kazisasa.app.di.AppContainer
import com.kazisasa.app.domain.model.Direction
import com.kazisasa.app.domain.model.SourceConfidence
import com.kazisasa.app.ui.components.FitBandChip
import com.kazisasa.app.ui.components.SourceConfidenceBadge
import com.kazisasa.app.ui.components.shouldShowSourceCaution
import com.kazisasa.app.work.ScheduleReminderResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpportunityDetailScreen(
    opportunityId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = AppContainer.getInstance(context)
    val viewModel: OpportunityDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                OpportunityDetailViewModel(
                    opportunityId = opportunityId,
                    opportunityRepository = container.opportunityRepository,
                    profileRepository = container.profileRepository,
                    savedRepository = container.savedRepository,
                    reminderRepository = container.reminderRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    var showSourceCaution by remember { mutableStateOf(false) }

    // Permission is requested HERE, inline, only when the user taps "Remind me" -
    // never on app launch (recommendations doc §16). Whichever way the system
    // dialog resolves, we just call setReminder() again; ReminderRepository's own
    // permission check then produces the right ScheduleReminderResult either way,
    // so there's no separate granted/denied branch to maintain here.
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.setReminder() }

    fun onRemindMeTapped() {
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !viewModel.hasNotificationPermission()
        if (needsRuntimePermission) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setReminder()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Opportunity") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        val o = state.opportunity
        val fit = state.fit
        // v3 fix: this used to gate on `fit == null` too, showing a permanent
        // "Loading..." message when browsing without a profile - fit is null
        // there, not "not yet loaded" (see OpportunityDetailViewModel: fit is
        // computed only when a profile exists). Only the opportunity itself
        // is a genuine loading condition; fit is optional content within the
        // screen below, same pattern as OpportunityCard.
        if (o == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Loading...")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            fit?.let { FitBandChip(it.band) }
            Text(o.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
            Text(
                o.organisation.name + (o.location.region?.let { " - $it" } ?: ""),
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(modifier = Modifier.padding(top = 8.dp)) { SourceConfidenceBadge(o.source.confidence) }

            o.summary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
            }

            if (fit != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text("Why this fits", style = MaterialTheme.typography.titleMedium)
                (fit.topReasons + fit.cautions).forEach { reason ->
                    val prefix = if (reason.direction == Direction.CAUTION) "! " else "+ "
                    val color = if (reason.direction == Direction.CAUTION) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    Text(
                        "$prefix${reason.explanation}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            o.eligibilityNotes?.let {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text("Eligibility notes", style = MaterialTheme.typography.titleMedium)
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { viewModel.toggleSave() }, modifier = Modifier.weight(1f)) {
                    Text(if (state.isSaved) "Saved" else "Save")
                }
                OutlinedButton(
                    onClick = { if (state.reminder == null) onRemindMeTapped() else viewModel.cancelReminder() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.reminder != null) "Reminder set" else "Remind me")
                }
            }

            reminderStatusMessage(state.lastReminderResult)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            }

            Button(
                onClick = {
                    if (shouldShowSourceCaution(o.source.confidence, o.applyIsOfficial)) {
                        showSourceCaution = true
                    } else {
                        openApplyLink(context, o.applyUrl)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                enabled = o.applyUrl != null,
            ) {
                Text(if (o.applyUrl != null) "Open application" else "No application link available")
            }

            Text(
                "Kazi Sasa never submits applications on your behalf - this opens the source's own page.",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // Recommendations doc §22's exact suggested copy - a scam/dead-link caution
        // for anything not confirmed official, shown before the person ever leaves the app.
        if (showSourceCaution) {
            AlertDialog(
                onDismissRequest = { showSourceCaution = false },
                title = { Text("Check before you continue") },
                text = {
                    Text(
                        "This listing is not from a verified official source (it's ${sourceLabel(o.source.confidence)}). " +
                            "Do not pay application fees or share sensitive personal details unless you confirm the employer independently.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showSourceCaution = false; openApplyLink(context, o.applyUrl) }) {
                        Text("Continue anyway")
                    }
                },
                dismissButton = { TextButton(onClick = { showSourceCaution = false }) { Text("Cancel") } },
            )
        }
    }
}

private fun sourceLabel(confidence: SourceConfidence): String = when (confidence) {
    SourceConfidence.AGGREGATED -> "a trusted aggregator, not the employer's own page"
    SourceConfidence.COMMUNITY -> "a community-sourced lead"
    SourceConfidence.UNVERIFIED -> "unverified"
    SourceConfidence.OFFICIAL -> "official" // shouldShowSourceCaution never triggers this case, kept for exhaustiveness
}

/**
 * Copy follows recommendations doc §17's principle directly: never promise
 * delivery ("we will definitely remind you"), always name the real uncertainty
 * (battery optimisation, OS batching) instead.
 */
private fun reminderStatusMessage(result: ScheduleReminderResult?): String? = when (result) {
    is ScheduleReminderResult.Scheduled ->
        "Reminder scheduled. Android may delay notifications depending on battery and device settings."
    ScheduleReminderResult.PermissionDenied ->
        "Notification permission is off, so this reminder can't fire. You can enable it in system settings, or rely on the weekly shortlist digest instead."
    ScheduleReminderResult.DeadlineTooSoon -> "This deadline has already passed."
    ScheduleReminderResult.NoDeadline -> "This opportunity has no listed deadline to remind you about."
    null -> null
}

private fun openApplyLink(context: android.content.Context, url: String?) {
    if (url == null) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
