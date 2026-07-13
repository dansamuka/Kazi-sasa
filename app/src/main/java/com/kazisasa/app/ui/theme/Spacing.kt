package com.kazisasa.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * v4 design spec §3.3 - base unit 4dp, every spacing value a multiple.
 * No 5dp, no 13dp, no 18dp anywhere in v4 components. Named for what they're
 * for, not just their numeric value, so a call site reads as intent
 * ("cardPadding") rather than an arbitrary number ("20.dp").
 */
object KaziSasaSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp

    /** Card internal padding (§3.3: "Card padding: 20dp"). */
    val cardPadding = xl

    /** Compact-variant (STRETCH band) card padding - tighter, see §5.4. */
    val cardPaddingCompact = md

    /** Card-to-card gap (§3.3: "8dp — tighter cards read as a list"). */
    val cardGap = sm
}

/**
 * v4 design spec §3.3 - "one step of hierarchy, not five." Three radii only.
 */
object KaziSasaShapes {
    val card = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(10.dp)
    val sheet = RoundedCornerShape(20.dp)

    /** The fit rail's own width - spec §4: "a 3dp vertical rail." */
    val railWidth = 3.dp
}
