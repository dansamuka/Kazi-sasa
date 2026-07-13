package com.kazisasa.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kazisasa.app.di.AppContainer
import com.kazisasa.app.ui.detail.OpportunityDetailScreen
import com.kazisasa.app.ui.feed.FeedScreen
import com.kazisasa.app.ui.profile.ProfileSwitcherScreen
import com.kazisasa.app.ui.saved.SavedScreen
import com.kazisasa.app.ui.shortlist.ShortlistScreen
import com.kazisasa.app.ui.shortlist.SkippedScreen

@Composable
fun KaziSasaNavHost(
    navController: NavHostController = rememberNavController(),
    startOpportunityId: String? = null,
    startProfileId: String? = null,
) {
    val context = LocalContext.current
    val container = AppContainer.getInstance(context)

    LaunchedEffect(startProfileId, startOpportunityId) {
        // Deep link from a fired reminder notification (spec §7.6): switch to
        // whichever profile set the reminder, then go straight to the opportunity,
        // so a notification never opens the wrong profile's context.
        if (startProfileId != null) {
            container.profileRepository.setActiveProfile(startProfileId)
        }
        if (startOpportunityId != null) {
            navController.navigate(Destination.Detail.createRoute(startOpportunityId))
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    val showBottomBar = bottomNavDestinations.any { dest -> currentRoute?.hierarchy?.any { it.route == dest.route } == true }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavDestinations.forEach { destination ->
                        val selected = currentRoute?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(destination.route) {
                                        // Pop to Feed specifically rather than via findStartDestination() -
                                        // simpler, and doesn't depend on an API I can't compile-check here.
                                        popUpTo(Destination.Feed.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { }, // no bottom-nav iconography yet - spec §11 leaves visual treatment to the designer
                            label = { Text(destination.tabLabel()) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Feed.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Feed.route) {
                FeedScreen(
                    onOpenDetail = { id -> navController.navigate(Destination.Detail.createRoute(id)) },
                    onCreateProfile = { navController.navigate(Destination.Profiles.route) },
                )
            }
            composable(Destination.Shortlist.route) {
                ShortlistScreen(
                    onOpenDetail = { id -> navController.navigate(Destination.Detail.createRoute(id)) },
                    onOpenSkipped = { navController.navigate(Destination.Skipped.route) },
                )
            }
            composable(Destination.Saved.route) {
                SavedScreen(onOpenDetail = { id -> navController.navigate(Destination.Detail.createRoute(id)) })
            }
            composable(Destination.Profiles.route) {
                ProfileSwitcherScreen()
            }
            composable(Destination.Skipped.route) {
                SkippedScreen(
                    onOpenDetail = { id -> navController.navigate(Destination.Detail.createRoute(id)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.Detail.route) { backStackEntryArg ->
                val id = backStackEntryArg.arguments?.getString(Destination.Detail.ARG_OPPORTUNITY_ID).orEmpty()
                OpportunityDetailScreen(opportunityId = id, onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun Destination.tabLabel(): String = when (this) {
    Destination.Feed -> Destination.Feed.LABEL
    Destination.Shortlist -> Destination.Shortlist.LABEL
    Destination.Saved -> Destination.Saved.LABEL
    Destination.Profiles -> Destination.Profiles.LABEL
    else -> route
}
