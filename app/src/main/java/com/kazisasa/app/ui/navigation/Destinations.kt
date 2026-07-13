package com.kazisasa.app.ui.navigation

/**
 * Route constants kept as a sealed type so a typo in a route string is a compile
 * error, not a runtime one. [label] and [icon] are used by the bottom nav bar in
 * KaziSasaNavHost - only the four top-level destinations need them.
 */
sealed class Destination(val route: String) {
    data object Feed : Destination("feed") {
        const val LABEL = "For You"
    }
    data object Shortlist : Destination("shortlist") {
        const val LABEL = "Shortlist"
    }
    data object Saved : Destination("saved") {
        const val LABEL = "Saved"
    }
    data object Profiles : Destination("profiles") {
        const val LABEL = "Profile"
    }
    data object Skipped : Destination("skipped")
    data object Detail : Destination("detail/{opportunityId}") {
        fun createRoute(opportunityId: String) = "detail/$opportunityId"
        const val ARG_OPPORTUNITY_ID = "opportunityId"
    }
}

/** The four tabs the bottom nav bar shows, in order - recommendations doc §10's suggested nav. */
val bottomNavDestinations = listOf(Destination.Feed, Destination.Shortlist, Destination.Saved, Destination.Profiles)
