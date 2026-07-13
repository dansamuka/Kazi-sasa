package com.kazisasa.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.kazisasa.app.R

/**
 * v4 design spec §3.2 - three type roles, each a deliberate choice, not the
 * same families you'd reach for on any other project:
 *   - Fraunces (display): a variable serif with an optical-size axis, used
 *     ONLY for card titles and screen headings. Its character carries the
 *     "well-briefed colleague" warmth the spec calls for.
 *   - Inter (body): neutral, excellent at small sizes, carries all UI prose.
 *   - IBM Plex Mono (data): used ONLY for things that are literally measured
 *     values (dates, counts, years-of-experience) - monospace here is
 *     semantic, not decorative. See spec §3.2's table for the full rationale.
 *
 * Fetched via the Downloadable Fonts API (Google Play Services), not
 * bundled as APK binary resources - see font_certs.xml for the provider
 * certificate values and where they came from.
 */
@OptIn(ExperimentalTextApi::class)
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

@OptIn(ExperimentalTextApi::class)
private val frauncesGoogleFont = GoogleFont("Fraunces")

@OptIn(ExperimentalTextApi::class)
private val interGoogleFont = GoogleFont("Inter")

@OptIn(ExperimentalTextApi::class)
private val plexMonoGoogleFont = GoogleFont("IBM Plex Mono")

@OptIn(ExperimentalTextApi::class)
val FrauncesFamily = FontFamily(
    Font(googleFont = frauncesGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = frauncesGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = frauncesGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
val InterFamily = FontFamily(
    Font(googleFont = interGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = interGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = interGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = interGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
val PlexMonoFamily = FontFamily(
    Font(googleFont = plexMonoGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = plexMonoGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
)

/**
 * Fallback behaviour if the Downloadable Fonts API can't reach Play Services
 * (older devices, no Play Services, or the fetch simply fails): Compose's
 * own font resolution falls back to the platform default (a system serif/
 * sans-serif/monospace) automatically when a GoogleFont entry fails to
 * load - nothing extra needs to be built here for that. What this means in
 * practice: on a device without Play Services, headings render in the
 * system's default serif rather than Fraunces, body text in the system
 * sans rather than Inter, and so on. The type *scale* (size, weight,
 * line-height) is unaffected either way, only the specific typeface glyphs.
 * This is a real, if imperfect, degradation - not a crash and not blank text.
 */
