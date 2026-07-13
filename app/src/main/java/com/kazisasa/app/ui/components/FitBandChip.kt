package com.kazisasa.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kazisasa.app.domain.model.FitBand

/**
 * Deliberately pairs a distinct leading glyph with the band label, not just a
 * colour - spec §13 "no colour-only meaning." A colour-blind user or a screen in
 * grayscale mode should still be able to tell STRONG from STRETCH from the glyph
 * and text alone.
 */
@Composable
fun FitBandChip(band: FitBand, modifier: Modifier = Modifier) {
    val (glyph, label, tint) = when (band) {
        FitBand.STRONG -> Triple("\u25CF", "Strong fit", Color(0xFF2E7D5B))   // ●
        FitBand.GOOD -> Triple("\u25D0", "Good fit", Color(0xFF9A7B1E))       // ◐
        FitBand.STRETCH -> Triple("\u25CB", "Stretch", Color(0xFF8A5A2B))     // ○
    }
    Text(
        text = "$glyph $label",
        style = MaterialTheme.typography.labelLarge,
        color = tint,
        modifier = modifier
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Small helper so callers don't need to import BorderStroke separately if they want an outlined variant later. */
internal fun outline(color: Color) = BorderStroke(1.dp, color.copy(alpha = 0.4f))
