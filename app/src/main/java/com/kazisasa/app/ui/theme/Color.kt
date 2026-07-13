package com.kazisasa.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * v4 design spec §3.1 — the "ink-and-paper" palette. Every pair here was
 * checked against real WCAG contrast math (relative-luminance formula), not
 * eyeballed - see the design spec's §3.1.1 for the two values that failed
 * AA-normal on first draft (inkFaint at 3.15:1 in both modes, light-mode
 * accent at 4.45:1) and were corrected here. Verified by rendering an actual
 * HTML/CSS mockup with a headless browser before any of this was written as
 * Kotlin - not assumed to look right from the hex values alone.
 */
object KaziSasaColors {

    // ---- Dark ----
    val surfaceDark = Color(0xFF14161C)
    val surfaceRaisedDark = Color(0xFF1B1E26)
    val surfaceSunkenDark = Color(0xFF0F1116)
    val inkDark = Color(0xFFF2F1ED)
    val inkMutedDark = Color(0xFF9AA0A8)
    val inkFaintDark = Color(0xFF82868E) // corrected from #666C75 - see spec §3.1.1
    val hairlineDark = Color(0xFF282C35)
    val accentDark = Color(0xFFD4813F)
    val accentMutedDark = Color(0xFFAA754D) // new token - solid GOOD-fit rail colour, see spec §3.1.1/§4
    val accentWashDark = Color(0xFF2A1D12)
    val verifiedDark = Color(0xFF5A9B78)
    val cautionDark = Color(0xFFC08050)
    val unverifiedDark = Color(0xFF666C75)

    // ---- Light ----
    val surfaceLight = Color(0xFFFBFAF7)
    val surfaceRaisedLight = Color(0xFFFFFFFF)
    val surfaceSunkenLight = Color(0xFFF1EFEA)
    val inkLight = Color(0xFF16181D)
    val inkMutedLight = Color(0xFF5C6068)
    val inkFaintLight = Color(0xFF72767C) // corrected from #8E939B - see spec §3.1.1
    val hairlineLight = Color(0xFFE4E1DA)
    val accentLight = Color(0xFFB2612A) // corrected from #B4622A (4.45:1 -> 4.53:1) - see spec §3.1.1
    val accentMutedLight = Color(0xFFAB7856) // new token - solid GOOD-fit rail colour
    val accentWashLight = Color(0xFFFBF0E6)
    val verifiedLight = Color(0xFF2F6B4F)
    val cautionLight = Color(0xFF9A5A2B)
    val unverifiedLight = Color(0xFF8E939B)
}

/**
 * The actual set of tokens a composable reads from, resolved once per
 * recomposition based on dark/light - avoids every call site having its own
 * `if (isDark) ... else ...` branch. See [androidx.compose.runtime.staticCompositionLocalOf]
 * usage in Theme.kt for how this gets threaded through [MaterialTheme] equivalents.
 */
data class KaziSasaColorTokens(
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val hairline: Color,
    val accent: Color,
    val accentMuted: Color,
    val accentWash: Color,
    val verified: Color,
    val caution: Color,
    val unverified: Color,
)

val LightKaziSasaColors = KaziSasaColorTokens(
    surface = KaziSasaColors.surfaceLight,
    surfaceRaised = KaziSasaColors.surfaceRaisedLight,
    surfaceSunken = KaziSasaColors.surfaceSunkenLight,
    ink = KaziSasaColors.inkLight,
    inkMuted = KaziSasaColors.inkMutedLight,
    inkFaint = KaziSasaColors.inkFaintLight,
    hairline = KaziSasaColors.hairlineLight,
    accent = KaziSasaColors.accentLight,
    accentMuted = KaziSasaColors.accentMutedLight,
    accentWash = KaziSasaColors.accentWashLight,
    verified = KaziSasaColors.verifiedLight,
    caution = KaziSasaColors.cautionLight,
    unverified = KaziSasaColors.unverifiedLight,
)

val DarkKaziSasaColors = KaziSasaColorTokens(
    surface = KaziSasaColors.surfaceDark,
    surfaceRaised = KaziSasaColors.surfaceRaisedDark,
    surfaceSunken = KaziSasaColors.surfaceSunkenDark,
    ink = KaziSasaColors.inkDark,
    inkMuted = KaziSasaColors.inkMutedDark,
    inkFaint = KaziSasaColors.inkFaintDark,
    hairline = KaziSasaColors.hairlineDark,
    accent = KaziSasaColors.accentDark,
    accentMuted = KaziSasaColors.accentMutedDark,
    accentWash = KaziSasaColors.accentWashDark,
    verified = KaziSasaColors.verifiedDark,
    caution = KaziSasaColors.cautionDark,
    unverified = KaziSasaColors.unverifiedDark,
)
