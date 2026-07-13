package com.kazisasa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kazisasa.app.domain.model.SourceConfidence

/** Spec §7.11 / recommendations doc §21: user-facing labels match the doc's table exactly. */
@Composable
fun SourceConfidenceBadge(confidence: SourceConfidence, modifier: Modifier = Modifier) {
    val (label, tint) = when (confidence) {
        SourceConfidence.OFFICIAL -> "Official source" to Color(0xFF2E7D5B)
        SourceConfidence.AGGREGATED -> "Trusted aggregator" to Color(0xFF5B6B8C)
        SourceConfidence.COMMUNITY -> "Community-sourced" to Color(0xFF9A7B1E)
        SourceConfidence.UNVERIFIED -> "Unverified" to Color(0xFFB3401F)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        modifier = modifier
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Whether the pre-exit caution (spec §7.11) should be shown before leaving to [Opportunity.applyUrl]. */
fun shouldShowSourceCaution(confidence: SourceConfidence, applyIsOfficial: Boolean): Boolean =
    !applyIsOfficial && confidence != SourceConfidence.OFFICIAL
