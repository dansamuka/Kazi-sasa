package com.kazisasa.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * v4 design spec §3.2 - fixed type scale, no arbitrary sizes. Every value in
 * [KaziSasaType] below traces directly to the spec's own table; this is not
 * a "reasonable-looking" type scale invented while coding, it's the one
 * that was actually specified and reviewed.
 *
 * Kept as a separate [KaziSasaType] object (used directly by v4 components
 * like OpportunityCard) rather than folded entirely into Material 3's
 * [Typography] slots, because M3's built-in slot names (headlineSmall,
 * titleMedium, etc.) don't map cleanly onto the spec's own named roles
 * (display/title/titleSmall/body/bodyStrong/label/meta/micro) - forcing a
 * fit would mean guessing which M3 slot is "close enough" to e.g. `meta`,
 * which doesn't exist in M3 at all. [KaziSasaTypography] below still
 * populates the M3 Typography object for the handful of stock M3 components
 * still in use elsewhere in the app (buttons, menus) that read from
 * MaterialTheme.typography directly.
 */
object KaziSasaType {
    val display = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp)
    val title = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp)
    val titleSmall = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyStrong = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp)
    val label = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val meta = TextStyle(fontFamily = PlexMonoFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
    val micro = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp,
        letterSpacing = 0.44.sp, // +0.04em at 11sp ≈ 0.44sp - spec §3.2's tracking value for micro/badge text
    )
}

/**
 * Material 3's [Typography] slots, populated from [KaziSasaType] where a
 * reasonable mapping exists, for the stock M3 components (buttons, menus,
 * dialogs) that read MaterialTheme.typography.* directly rather than the
 * v4-specific KaziSasaType object. New v4 components (OpportunityCard and
 * anything built for this redesign) should reference [KaziSasaType]
 * directly instead, not these M3 slot names.
 */
val KaziSasaTypography = Typography(
    headlineSmall = KaziSasaType.display,
    titleLarge = KaziSasaType.title,
    titleMedium = KaziSasaType.titleSmall,
    bodyLarge = KaziSasaType.body,
    bodyMedium = KaziSasaType.body,
    labelLarge = KaziSasaType.label,
    labelMedium = KaziSasaType.meta,
    labelSmall = KaziSasaType.micro,
)
