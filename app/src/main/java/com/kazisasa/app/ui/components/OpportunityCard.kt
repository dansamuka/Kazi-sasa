package com.kazisasa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kazisasa.app.data.repository.ScoredOpportunity
import com.kazisasa.app.domain.model.FitBand
import com.kazisasa.app.domain.model.SourceConfidence
import com.kazisasa.app.domain.model.WorkMode
import com.kazisasa.app.ui.theme.KaziSasaTheme

/**
 * v4 design spec §5 - full rewrite. The single highest-value change in the
 * whole v4 pass (see spec §9: "if only one phase ships, ship this one").
 *
 * Structure, in order, matches spec §5.1's anatomy diagram exactly:
 *   1. Fit Rail (spec §4) - a 3dp vertical bar, the whole redesign's
 *      signature element. Present only when [scored].fit is non-null;
 *      genuinely absent (not grey, not faint) when browsing without a
 *      profile - see ScoredOpportunity's own doc comment for why null
 *      fit is the honest representation there, not a fabricated score.
 *   2. Title + verified mark, on one line (spec §5.2: verified-ness is a
 *      fact about the ORGANISATION, so it sits next to the title).
 *   3. Meta line: org · location · relative time (mono, via
 *      [formatRelativeTime] - mirrors kazi-sasa-feed's web app.js exactly).
 *   4. THE FIT REASON - promoted to the card's visual centre, one line,
 *      the single strongest [FitReason] from the engine's own topReasons.
 *      This was being computed by FitEngineImpl and thrown away before
 *      this rewrite - the biggest concrete miss identified in the design
 *      review (spec §1.2).
 *   5. Chips, tiered (spec §5.3) - industry/seniority/work-mode/years,
 *      wrapped in a real FlowRow (not clipped) since spec §5.3's own
 *      verification (built and measured, not assumed) found chip rows
 *      genuinely wrap at narrow widths with realistic label lengths, and
 *      that's fine as long as nothing is silently cut off.
 *   6. Footer: source confidence as quiet micro text (not a filled pill -
 *      spec §5.2: "it's a footnote, which is exactly its epistemic
 *      status"), plus actions. Save is the one prominent, always-visible
 *      action per spec §5.1's diagram; Skip/Shortlist are real triage
 *      actions this app already depends on elsewhere (Shortlist screen's
 *      data flow), so they're kept - not silently dropped to match the
 *      diagram exactly - but de-emphasised (quiet colour, smaller type)
 *      so Save reads as primary. Shown only when a profile is active,
 *      same rule as before this rewrite (triage doesn't mean anything
 *      without a profile to triage against).
 *
 * The STRETCH-band compact variant (spec §5.4) is a real structural
 * difference, not just styling: smaller padding, no fit-reason line
 * (there isn't a positive one to show), max 2 chips, sunken background,
 * de-emphasised title. A stretch role earns *showing*, not the same
 * visual weight as a strong match (spec §2.1: "earn the scroll").
 */
@Composable
fun OpportunityCard(
    scored: ScoredOpportunity,
    onOpenDetail: () -> Unit,
    onKeep: () -> Unit,
    onSkip: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val o = scored.opportunity
    val fit = scored.fit
    val colors = KaziSasaTheme.colors
    val isStretch = fit?.band == FitBand.STRETCH

    val cardPadding = if (isStretch) KaziSasaTheme.spacing.cardPaddingCompact else KaziSasaTheme.spacing.cardPadding
    val cardBackground = if (isStretch) colors.surfaceSunken else colors.surfaceRaised
    val titleStyle = if (isStretch) KaziSasaTheme.type.titleSmall else KaziSasaTheme.type.title
    val titleColor = if (isStretch) colors.inkMuted else colors.ink

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(KaziSasaTheme.shapes.card)
            .background(cardBackground)
            .border(1.dp, colors.hairline, KaziSasaTheme.shapes.card)
            .clickable(onClick = onOpenDetail),
    ) {
        FitRail(band = fit?.band)

        Column(modifier = Modifier.padding(cardPadding)) {
            // --- 2. Title + verified mark ---
            Row(
                horizontalArrangement = Arrangement.spacedBy(KaziSasaTheme.spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = o.title,
                    style = titleStyle,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (o.organisation.verified) {
                    Text("\u2713", color = colors.verified, style = KaziSasaTheme.type.label)
                }
            }

            // --- 3. Meta line: org · location · relative time ---
            val locationLabel = o.location.raw ?: o.location.country
            val relativeTime = formatRelativeTime(o.postedAtMillis)
            Row(
                horizontalArrangement = Arrangement.spacedBy(KaziSasaTheme.spacing.xs),
                modifier = Modifier.padding(top = 3.dp),
            ) {
                Text(o.organisation.name, style = KaziSasaTheme.type.label, color = colors.inkMuted)
                if (locationLabel != null) {
                    Text("\u00B7", style = KaziSasaTheme.type.label, color = colors.inkFaint)
                    Text(locationLabel, style = KaziSasaTheme.type.label, color = colors.inkMuted)
                }
                if (relativeTime != null) {
                    Text("\u00B7", style = KaziSasaTheme.type.label, color = colors.inkFaint)
                    Text(relativeTime, style = KaziSasaTheme.type.meta, color = colors.inkFaint)
                }
            }

            // --- 4. The fit reason (promoted - see this file's own top doc comment) ---
            // Suppressed entirely for STRETCH (spec §5.4: "there isn't a positive
            // one to show - that's what STRETCH means"), regardless of whether
            // topReasons happens to have content alongside a blocking caution.
            if (fit != null && !isStretch) {
                fit.topReasons.firstOrNull()?.let { reason ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.padding(top = KaziSasaTheme.spacing.md),
                    ) {
                        Text("\u2794", color = colors.accent, style = KaziSasaTheme.type.bodyStrong)
                        Text(reason.explanation, style = KaziSasaTheme.type.bodyStrong, color = colors.ink)
                    }
                }
            }

            // --- 5b. Summary line - browse mode only (spec §5.5) ---
            if (fit == null) {
                o.summary?.let { summary ->
                    Text(
                        text = summary,
                        style = KaziSasaTheme.type.body,
                        color = colors.inkMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = KaziSasaTheme.spacing.md),
                    )
                }
            }

            // --- 5. Chips, tiered ---
            val chips = buildCardChips(scored, maxChips = if (isStretch) 2 else 3)
            if (chips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(KaziSasaTheme.spacing.xs + 2.dp),
                    verticalArrangement = Arrangement.spacedBy(KaziSasaTheme.spacing.xs + 2.dp),
                    modifier = Modifier.padding(top = KaziSasaTheme.spacing.md),
                ) {
                    chips.forEach { chip -> CardChip(chip) }
                }
            }

            // --- 6. Footer ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isStretch) KaziSasaTheme.spacing.sm + 2.dp else KaziSasaTheme.spacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceConfidenceLabel(o.source.confidence)

                Row(horizontalArrangement = Arrangement.spacedBy(KaziSasaTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    // Skip/Shortlist: real triage actions the Shortlist screen's data
                    // flow depends on - kept, not silently dropped to match the spec's
                    // ASCII diagram exactly, but de-emphasised so Save reads as primary.
                    // Only meaningful with a profile active (same rule as before this
                    // rewrite - triage doesn't mean anything without a profile to
                    // triage against).
                    if (fit != null) {
                        QuietTextAction(text = "Skip", onClick = onSkip)
                        QuietTextAction(text = "Shortlist", onClick = onKeep)
                    }
                    TextButton(onClick = onSave) {
                        Text("Save", style = KaziSasaTheme.type.label, color = colors.accent)
                    }
                }
            }
        }
    }
}

/**
 * spec §4 - the signature element. Solid colour per tier, never opacity-
 * blended (see Color.kt's accentMuted doc comment for why: a 40%-opacity
 * GOOD-tier rail measured at only 1.95:1 contrast against its own card
 * background during verification - nowhere near visible enough for a
 * signal this important). Absent entirely (no Box at all, not just
 * invisible) when [band] is null, i.e. browsing without a profile.
 */
@Composable
private fun RowScope.FitRail(band: FitBand?) {
    if (band == null) return
    val colors = KaziSasaTheme.colors
    val railColor = when (band) {
        FitBand.STRONG -> colors.accent
        FitBand.GOOD -> colors.accentMuted
        FitBand.STRETCH -> colors.hairline
    }
    val description = when (band) {
        FitBand.STRONG -> "Strong fit for your profile"
        FitBand.GOOD -> "Good fit for your profile"
        FitBand.STRETCH -> "Stretch fit for your profile"
    }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(KaziSasaTheme.shapes.railWidth)
            .background(railColor)
            .semantics { contentDescription = description },
    )
}

@Composable
private fun QuietTextAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = KaziSasaTheme.type.label,
        color = KaziSasaTheme.colors.inkFaint,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * spec §5.2: "it's a footnote, which is exactly its epistemic status" -
 * plain micro text, not a filled pill like the pre-v4 SourceConfidenceBadge.
 */
@Composable
private fun SourceConfidenceLabel(confidence: SourceConfidence) {
    val colors = KaziSasaTheme.colors
    val (label, color) = when (confidence) {
        SourceConfidence.OFFICIAL -> "Official source" to colors.verified
        SourceConfidence.AGGREGATED -> "Aggregated listing" to colors.caution
        SourceConfidence.COMMUNITY -> "Community-sourced" to colors.caution
        SourceConfidence.UNVERIFIED -> "Unverified" to colors.unverified
    }
    Text(text = label.uppercase(), style = KaziSasaTheme.type.micro, color = color)
}

/** One chip's tier - determines its visual treatment, see spec §5.3's table. */
private enum class ChipTier { PRIMARY, SECONDARY, DATA }
private data class CardChipData(val label: String, val tier: ChipTier)

/**
 * Priority order per spec §5.3: industry -> seniority -> work mode -> years.
 * Capped at [maxChips] (3 normally, 2 for the STRETCH compact variant) -
 * a card is a summary, not a data dump, and every additional chip has to
 * earn its place per spec §2.1.
 */
private fun buildCardChips(scored: ScoredOpportunity, maxChips: Int): List<CardChipData> {
    val o = scored.opportunity
    val candidates = buildList {
        o.industry?.let { add(CardChipData(humanizeToken(it), ChipTier.PRIMARY)) }
        o.seniority?.let { add(CardChipData(it.name.lowercase().replaceFirstChar(Char::uppercase), ChipTier.SECONDARY)) }
        o.workMode?.let { add(CardChipData(workModeChipLabel(it), ChipTier.SECONDARY)) }
        if (o.yearsExperienceMin != null) {
            val label = if (o.yearsExperienceMax != null) "${o.yearsExperienceMin}\u2013${o.yearsExperienceMax} yrs" else "${o.yearsExperienceMin}+ yrs"
            add(CardChipData(label, ChipTier.DATA))
        }
    }
    return candidates.take(maxChips)
}

private fun workModeChipLabel(mode: WorkMode): String = when (mode) {
    WorkMode.ONSITE -> "On-site"
    WorkMode.HYBRID -> "Hybrid"
    WorkMode.REMOTE_KENYA,
    WorkMode.REMOTE_REGIONAL,
    WorkMode.REMOTE_GLOBAL,
    -> "Remote"
}

private fun humanizeToken(token: String): String =
    token.replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

@Composable
private fun CardChip(chip: CardChipData) {
    val colors = KaziSasaTheme.colors
    when (chip.tier) {
        ChipTier.PRIMARY -> Text(
            text = chip.label,
            style = KaziSasaTheme.type.label,
            color = colors.accent,
            modifier = Modifier
                .clip(KaziSasaTheme.shapes.chip)
                .background(colors.accentWash)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        ChipTier.SECONDARY -> Text(
            text = chip.label,
            style = KaziSasaTheme.type.label,
            color = colors.inkMuted,
            modifier = Modifier
                .clip(KaziSasaTheme.shapes.chip)
                .border(1.dp, colors.hairline, KaziSasaTheme.shapes.chip)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        ChipTier.DATA -> Text(
            text = chip.label,
            style = KaziSasaTheme.type.meta,
            color = colors.inkFaint,
        )
    }
}
