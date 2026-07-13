package com.kazisasa.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * v4 design spec, replacing the placeholder theme that shipped with the
 * original build. Every colour, size, and shape value in this file traces
 * to the spec (kazi-sasa-v4-design-spec.md) - none of it is a fresh
 * "reasonable-looking" choice made while writing code. See Color.kt's own
 * doc comment for how the palette was verified (real WCAG contrast math,
 * not eyeballed) before being written here.
 *
 * [LocalKaziSasaColors] is how v4 components (OpportunityCard and anything
 * built for this redesign) read the ink-and-paper palette - via
 * `KaziSasaTheme.colors.accent` etc, not `MaterialTheme.colorScheme.primary`.
 * The M3 [MaterialTheme] colour scheme below is still populated (from the
 * same token set) for the stock M3 components elsewhere in the app that
 * read MaterialTheme.colorScheme.* directly - so nothing already built
 * breaks, while new v4 work has a cleaner, spec-named API to reach for.
 */
private val LocalKaziSasaColors: ProvidableCompositionLocal<KaziSasaColorTokens> =
    staticCompositionLocalOf { LightKaziSasaColors } // default only used if read outside KaziSasaTheme - shouldn't happen in practice

/** Namespaced accessors so call sites read as `KaziSasaTheme.colors.accent`, `KaziSasaTheme.spacing.cardPadding`, etc. */
object KaziSasaTheme {
    val colors: KaziSasaColorTokens
        @Composable get() = LocalKaziSasaColors.current

    val spacing: KaziSasaSpacing get() = KaziSasaSpacing
    val shapes: KaziSasaShapes get() = KaziSasaShapes
    val type: KaziSasaType get() = KaziSasaType
}

@Composable
fun KaziSasaAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkKaziSasaColors else LightKaziSasaColors

    // M3 colour scheme populated from the SAME token set, for stock M3
    // components (buttons, menus, dialogs) that read MaterialTheme.colorScheme
    // directly rather than KaziSasaTheme.colors - keeps both APIs consistent
    // with one source of truth instead of two palettes that could drift.
    val m3Scheme = if (darkTheme) {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.surfaceRaised,
            secondary = tokens.accentMuted,
            background = tokens.surface,
            surface = tokens.surfaceRaised,
            onBackground = tokens.ink,
            onSurface = tokens.ink,
            error = tokens.caution,
        )
    } else {
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.surfaceRaised,
            secondary = tokens.accentMuted,
            background = tokens.surface,
            surface = tokens.surfaceRaised,
            onBackground = tokens.ink,
            onSurface = tokens.ink,
            error = tokens.caution,
        )
    }

    CompositionLocalProvider(LocalKaziSasaColors provides tokens) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = KaziSasaTypography,
            content = content,
        )
    }
}
