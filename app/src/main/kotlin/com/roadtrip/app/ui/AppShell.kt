package com.roadtrip.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.roadtrip.app.ui.games.BingoScreen
import com.roadtrip.app.ui.games.BoardScreen
import com.roadtrip.app.ui.games.GamesScreen
import com.roadtrip.app.ui.games.ReplayScreen
import com.roadtrip.app.ui.journal.JournalScreen
import com.roadtrip.app.ui.map.MapScreen
import com.roadtrip.app.ui.settings.SettingsScreen
import com.roadtrip.app.ui.trip.TripScreen
import com.roadtrip.app.ui.trips.ActivatePlannedTripDialog
import com.roadtrip.app.ui.trips.DeletePlannedTripDialog
import com.roadtrip.app.ui.trips.PlanTripDialog
import com.roadtrip.app.ui.trips.PlannedTripCardView
import com.roadtrip.app.ui.trips.StartTripDialog
import com.roadtrip.app.ui.trips.TripsScreen
import com.roadtrip.core.api.Profile
import com.roadtrip.core.journal.NavTarget
import com.roadtrip.core.navigation.NavMotion
import com.roadtrip.core.navigation.NavMotionClassifier
import com.roadtrip.core.notifications.Screen
import com.roadtrip.core.notifications.VisibleContext
import com.roadtrip.core.trips.PlannerState
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
    // affordances (docs/spec/09-trips.md, ANDTRIP-001/002/004). Produced off the composition
    // thread on Dispatchers.IO so navigation never blocks a frame on Room I/O (AND-012); the
    // shell only observes already-loaded state.
    val tripHome: TripHomeState by remember(profile) { container.tripHomeFlow(profile) }.collectAsState()
    var showStartDialog by remember { mutableStateOf(false) }

    // The planned "next trip" card state for the no-active-trip banner area
    // (ANDTRIP-006/007); the staged itinerary previews from its own cache, also read off the
    // composition thread (AND-012).
    val plannerState: PlannerState by remember(profile) { container.plannerFlow(profile) }.collectAsState()
    val tripError by container.tripActionError.collectAsState()
    var showPlanDialog by remember { mutableStateOf(false) }
    var showActivateDialog by remember { mutableStateOf(false) }
    var showDeletePlanDialog by remember { mutableStateOf(false) }

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
            // Bingo lives in the Games section (docs/spec/10-bingo.md).
            currentRoute.startsWith("bingo") -> VisibleContext(Screen.GAMES)
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
                    title = {
                        // The active trip's name rides as persistent context above the
                        // per-screen title (ANDTRIP-009); it shows only while a trip runs and
                        // ellipsizes when long so the app bar never overflows.
                        val tripBarLabel = TripStateReducer.activeTripBarLabel(tripHome)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (tripBarLabel != null) {
                                Text(
                                    text = "On the road: $tripBarLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = titleFor(currentRoute),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
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
                    plannerState = plannerState,
                    onStart = { showStartDialog = true },
                    onPlan = { showPlanDialog = true },
                    onOpenHistory = {
                        navController.navigate(Routes.trips()) { launchSingleTop = true }
                    },
                )
                // The planned-trip card rides the no-active-trip banner area: between
                // trips and on the first-launch welcome (ANDTRIP-006/007).
                plannerState.card?.let { card ->
                    PlannedTripCardView(
                        card = card,
                        tripError = tripError,
                        onStart = { showActivateDialog = true },
                        onEdit = { showPlanDialog = true },
                        onDelete = { showDeletePlanDialog = true },
                    )
                }
                NavHost(
                    navController = navController,
                    startDestination = Routes.JOURNAL,
                    modifier = Modifier.weight(1f),
                    // Material motion instead of the navigation-compose default 700 ms
                    // crossfade (AND-011): fade-through between the five top-level tabs,
                    // shared-axis X when drilling into or popping back out of a detail route.
                    enterTransition = {
                        when (
                            NavMotionClassifier.motionFor(
                                initialState.destination.route,
                                targetState.destination.route,
                            )
                        ) {
                            NavMotion.FADE_THROUGH ->
                                fadeIn(tween(NAV_MOTION_MS)) +
                                    scaleIn(tween(NAV_MOTION_MS), initialScale = FADE_THROUGH_SCALE)
                            NavMotion.SHARED_AXIS_X ->
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Start,
                                    tween(NAV_MOTION_MS),
                                ) + fadeIn(tween(NAV_MOTION_MS))
                        }
                    },
                    exitTransition = {
                        when (
                            NavMotionClassifier.motionFor(
                                initialState.destination.route,
                                targetState.destination.route,
                            )
                        ) {
                            NavMotion.FADE_THROUGH -> fadeOut(tween(NAV_MOTION_MS))
                            NavMotion.SHARED_AXIS_X ->
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Start,
                                    tween(NAV_MOTION_MS),
                                ) + fadeOut(tween(NAV_MOTION_MS))
                        }
                    },
                    popEnterTransition = {
                        when (
                            NavMotionClassifier.motionFor(
                                initialState.destination.route,
                                targetState.destination.route,
                            )
                        ) {
                            NavMotion.FADE_THROUGH ->
                                fadeIn(tween(NAV_MOTION_MS)) +
                                    scaleIn(tween(NAV_MOTION_MS), initialScale = FADE_THROUGH_SCALE)
                            NavMotion.SHARED_AXIS_X ->
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.End,
                                    tween(NAV_MOTION_MS),
                                ) + fadeIn(tween(NAV_MOTION_MS))
                        }
                    },
                    popExitTransition = {
                        when (
                            NavMotionClassifier.motionFor(
                                initialState.destination.route,
                                targetState.destination.route,
                            )
                        ) {
                            NavMotion.FADE_THROUGH -> fadeOut(tween(NAV_MOTION_MS))
                            NavMotion.SHARED_AXIS_X ->
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.End,
                                    tween(NAV_MOTION_MS),
                                ) + fadeOut(tween(NAV_MOTION_MS))
                        }
                    },
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
                        onOpenBingo = {
                            navController.navigate(Routes.bingo()) { launchSingleTop = true }
                        },
                    )
                }
                composable(
                    Routes.BINGO,
                    arguments = listOf(
                        navArgument("trip") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    BingoScreen(
                        container = container,
                        profile = profile,
                        historyTripId = entry.arguments?.getString("trip"),
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
                    TripsScreen(
                        container = container,
                        profile = profile,
                        initialTripId = entry.arguments?.getString("trip"),
                        onOpenBingo = { tripId ->
                            navController.navigate(Routes.bingo(tripId)) { launchSingleTop = true }
                        },
                    )
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

    // ---- itinerary planner dialogs (ANDTRIP-006/008) ---------------------------------------
    if (showPlanDialog) {
        val editingTrip = plannerState.card?.trip
        PlanTripDialog(
            initialName = editingTrip?.name.orEmpty(),
            initialStart = editingTrip?.plannedStartAt.orEmpty(),
            editing = editingTrip != null,
            onConfirm = { name, plannedStartAt ->
                showPlanDialog = false
                if (editingTrip == null) {
                    container.createPlannedTrip(name, plannedStartAt)
                } else {
                    container.updatePlannedTrip(editingTrip.id, name, plannedStartAt)
                }
            },
            onDismiss = { showPlanDialog = false },
        )
    }
    plannerState.card?.let { card ->
        if (showActivateDialog) {
            ActivatePlannedTripDialog(
                tripName = card.trip.name,
                stagedCount = card.itinerary.size,
                onConfirm = {
                    showActivateDialog = false
                    container.activatePlannedTrip(card.trip.id)
                },
                onDismiss = { showActivateDialog = false },
            )
        }
        if (showDeletePlanDialog) {
            DeletePlannedTripDialog(
                tripName = card.trip.name,
                onConfirm = {
                    showDeletePlanDialog = false
                    container.deletePlannedTrip(card.trip.id)
                },
                onDismiss = { showDeletePlanDialog = false },
            )
        }
    }
}

/**
 * Slim strip under the top bar for the between-trips state only: the persistent "No active
 * road trip" banner (with the parent-only, online-only start action) between trips and on
 * first launch, plus the read-only "Browsing …" context (ANDTRIP-001/002/004). While a trip
 * is active its name is persistent context in the top app bar instead (ANDTRIP-009), so the
 * strip renders nothing. When a planned trip exists its card (rendered below the strip)
 * carries the start action instead, and parents without a plan get the "Plan the next trip"
 * entry point (ANDTRIP-006).
 */
@Composable
private fun TripStrip(
    state: TripHomeState,
    plannerState: PlannerState,
    onStart: () -> Unit,
    onPlan: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    // While a trip is active the app bar carries its name as persistent context
    // (ANDTRIP-009), so the strip renders nothing here — only the between-trips banner
    // (and its parent actions) below.
    val banner = state.bannerText ?: return

    // The planned card (below the strip) owns "Road trip starts now" while a plan exists.
    val showGenericStart = state.startAction.visible && state.plannedTrip == null

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
                            state.plannedTrip != null -> "Welcome! The next road trip is planned below."
                            else -> "Welcome! Start your first road trip when you hit the road."
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (state.viewedTrip != null) {
                    TextButton(onClick = onOpenHistory) { Text("History") }
                }
                if (plannerState.planAction.visible) {
                    TextButton(onClick = onPlan, enabled = plannerState.planAction.enabled) {
                        Text("Plan the next trip")
                    }
                }
                if (showGenericStart) {
                    TextButton(onClick = onStart, enabled = state.startAction.enabled) {
                        Text("Road trip starts now")
                    }
                }
            }
            if (showGenericStart && !state.startAction.enabled) {
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
    route.startsWith("bingo") -> "License plate bingo"
    route.startsWith("checklist") -> "Checklist"
    route.startsWith("trips") -> "Trip history"
    route.startsWith("trip") -> "Trip"
    route == Routes.SETTINGS -> "Settings"
    else -> "Road Trip"
}

/** Navigation motion duration — Material-brisk, well under the 700 ms default (AND-011). */
private const val NAV_MOTION_MS = 280

/** Fade-through incoming scale: a slight grow-in that reads as a fade, not a spatial slide. */
private const val FADE_THROUGH_SCALE = 0.92f
