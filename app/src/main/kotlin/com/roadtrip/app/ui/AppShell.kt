package com.roadtrip.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.ui.checklist.ChecklistScreen
import com.roadtrip.app.ui.common.OnlineBadge
import com.roadtrip.app.ui.games.BoardScreen
import com.roadtrip.app.ui.games.GamesScreen
import com.roadtrip.app.ui.games.ReplayScreen
import com.roadtrip.app.ui.journal.JournalScreen
import com.roadtrip.app.ui.map.MapScreen
import com.roadtrip.app.ui.settings.SettingsScreen
import com.roadtrip.app.ui.trip.TripScreen
import com.roadtrip.app.ui.trips.StartTripDialog
import com.roadtrip.app.ui.trips.TripsScreen
import com.roadtrip.core.api.Profile
import com.roadtrip.core.journal.NavTarget
import com.roadtrip.core.notifications.Screen
import com.roadtrip.core.notifications.VisibleContext
import com.roadtrip.core.trips.TripHomeState
import com.roadtrip.core.trips.TripStateReducer
import kotlinx.coroutines.flow.MutableStateFlow

private data class TopDestination(
    val navigateRoute: String,
    val routePattern: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * App shell: adaptive navigation over the five destinations (AND-004 — the
 * NavigationSuiteScaffold renders a bottom bar on compact width and a navigation rail on
 * medium/expanded), a top bar with the online indicator (AND-006), and the NavHost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    container: AppContainer,
    profile: Profile,
    pendingNavTarget: MutableStateFlow<NavTarget?>,
) {
    val navController = rememberNavController()
    val online by container.onlineMonitor.online.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Trip lifecycle state: active-trip name, between-trips banner, parent start/end
    // affordances (docs/spec/09-trips.md, ANDTRIP-001/002/004).
    val tick by container.refreshTick.collectAsState()
    val tripHome: TripHomeState = remember(tick, online, profile) {
        TripStateReducer.reduce(container.tripsCache.read()?.value.orEmpty(), profile.role, online)
    }
    var showStartDialog by remember { mutableStateOf(false) }

    // Consume a notification tap's deep-link target (ANDNOTIF-005).
    val pending by pendingNavTarget.collectAsState()
    LaunchedEffect(pending) {
        val target = pending ?: return@LaunchedEffect
        navController.navigate(Routes.forTarget(target)) { launchSingleTop = true }
        pendingNavTarget.value = null
    }

    // Track the visible screen so notifications for it are suppressed (ANDNOTIF-004).
    LaunchedEffect(currentRoute, backStackEntry) {
        container.visibleContext.value = when {
            currentRoute == null -> null
            currentRoute == Routes.JOURNAL -> VisibleContext(Screen.JOURNAL)
            currentRoute.startsWith("map") -> VisibleContext(Screen.MAP)
            currentRoute == Routes.GAMES -> VisibleContext(Screen.GAMES)
            currentRoute.startsWith("board/") -> VisibleContext(
                Screen.GAME_BOARD,
                backStackEntry?.arguments?.getString("gameId"),
            )
            currentRoute.startsWith("replay/") -> VisibleContext(Screen.GAMES)
            currentRoute.startsWith("checklist") -> VisibleContext(Screen.CHECKLIST)
            // "trips" must be tested before the "trip" prefix it shares.
            currentRoute.startsWith("trips") -> VisibleContext(Screen.TRIP)
            currentRoute.startsWith("trip") -> VisibleContext(Screen.TRIP)
            currentRoute == Routes.SETTINGS -> VisibleContext(Screen.SETTINGS)
            else -> null
        }
    }

    val destinations = listOf(
        TopDestination(Routes.JOURNAL, Routes.JOURNAL, "Journal", Icons.AutoMirrored.Filled.Article),
        TopDestination("map", Routes.MAP, "Map", Icons.Filled.Map),
        TopDestination(Routes.GAMES, Routes.GAMES, "Games", Icons.Filled.SportsEsports),
        TopDestination("checklist", Routes.CHECKLIST, "Checklist", Icons.Filled.Checklist),
        TopDestination("trip", Routes.TRIP, "Trip", Icons.Filled.Flag),
    )

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            destinations.forEach { destination ->
                item(
                    selected = currentRoute == destination.routePattern,
                    onClick = {
                        navController.navigate(destination.navigateRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(titleFor(currentRoute)) },
                    actions = {
                        OnlineBadge(online)
                        IconButton(onClick = {
                            navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                        }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                // Active-trip indicator / persistent "No active road trip" banner with the
                // parent-only start action (ANDTRIP-001/002/004).
                TripStrip(
                    state = tripHome,
                    onStart = { showStartDialog = true },
                    onOpenHistory = {
                        navController.navigate(Routes.trips()) { launchSingleTop = true }
                    },
                )
                NavHost(
                    navController = navController,
                    startDestination = Routes.JOURNAL,
                    modifier = Modifier.weight(1f),
                ) {
                composable(Routes.JOURNAL) {
                    JournalScreen(container, profile) { target ->
                        navController.navigate(Routes.forTarget(target)) { launchSingleTop = true }
                    }
                }
                composable(
                    Routes.MAP,
                    arguments = listOf(
                        navArgument("lat") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("lon") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    MapScreen(
                        container = container,
                        profile = profile,
                        centerLat = entry.arguments?.getString("lat")?.toDoubleOrNull(),
                        centerLon = entry.arguments?.getString("lon")?.toDoubleOrNull(),
                    )
                }
                composable(Routes.GAMES) {
                    GamesScreen(
                        container = container,
                        profile = profile,
                        onOpenBoard = { id -> navController.navigate(Routes.board(id)) },
                        onOpenReplay = { id -> navController.navigate(Routes.replay(id)) },
                    )
                }
                composable(
                    Routes.BOARD,
                    arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
                ) { entry ->
                    BoardScreen(container, profile, entry.arguments?.getString("gameId").orEmpty())
                }
                composable(
                    Routes.REPLAY,
                    arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
                ) { entry ->
                    ReplayScreen(container, entry.arguments?.getString("gameId").orEmpty())
                }
                composable(
                    Routes.CHECKLIST,
                    arguments = listOf(
                        navArgument("state") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    ChecklistScreen(container, entry.arguments?.getString("state"))
                }
                composable(
                    Routes.TRIP,
                    arguments = listOf(
                        navArgument("dest") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    TripScreen(
                        container = container,
                        highlightDestinationId = entry.arguments?.getString("dest"),
                        onOpenHistory = {
                            navController.navigate(Routes.trips()) { launchSingleTop = true }
                        },
                    )
                }
                composable(
                    Routes.TRIPS,
                    arguments = listOf(
                        navArgument("trip") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    TripsScreen(container, entry.arguments?.getString("trip"))
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(container, profile)
                }
                }
            }
        }
    }

    if (showStartDialog) {
        StartTripDialog(
            onConfirm = { name ->
                showStartDialog = false
                container.startTrip(name)
            },
            onDismiss = { showStartDialog = false },
        )
    }
}

/**
 * Slim strip under the top bar: shows the active trip's name while one runs, or the
 * persistent "No active road trip" banner (with the parent-only, online-only start
 * action) between trips and on first launch (ANDTRIP-001/002/004).
 */
@Composable
private fun TripStrip(
    state: TripHomeState,
    onStart: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val banner = state.bannerText
    if (banner == null) {
        val name = state.viewedTrip?.name ?: return
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "On the road: $name",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(banner, style = MaterialTheme.typography.labelLarge)
                    val viewed = state.viewedTrip
                    Text(
                        when {
                            viewed != null -> "Browsing \"${viewed.name}\" (read-only)"
                            else -> "Welcome! Start your first road trip when you hit the road."
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (state.viewedTrip != null) {
                    TextButton(onClick = onOpenHistory) { Text("History") }
                }
                if (state.startAction.visible) {
                    TextButton(onClick = onStart, enabled = state.startAction.enabled) {
                        Text("Road trip starts now")
                    }
                }
            }
            if (state.startAction.visible && !state.startAction.enabled) {
                Text(
                    state.startAction.disabledReason.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun titleFor(route: String?): String = when {
    route == null -> "Road Trip"
    route == Routes.JOURNAL -> "Journal"
    route.startsWith("map") -> "Map"
    route == Routes.GAMES -> "Games"
    route.startsWith("board/") -> "Game"
    route.startsWith("replay/") -> "Replay"
    route.startsWith("checklist") -> "Checklist"
    route.startsWith("trips") -> "Trip history"
    route.startsWith("trip") -> "Trip"
    route == Routes.SETTINGS -> "Settings"
    else -> "Road Trip"
}
