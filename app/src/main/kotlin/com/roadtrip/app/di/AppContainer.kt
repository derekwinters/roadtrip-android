package com.roadtrip.app.di

import android.content.Context
import androidx.room.Room
import com.roadtrip.app.data.AppSettings
import com.roadtrip.app.data.BingoCacheApplier
import com.roadtrip.app.data.ChecklistCacheApplier
import com.roadtrip.app.data.GamesCacheApplier
import com.roadtrip.app.data.MapCacheApplier
import com.roadtrip.app.data.room.RoadtripDatabase
import com.roadtrip.app.data.room.RoomCacheStore
import com.roadtrip.app.data.room.RoomCursorStore
import com.roadtrip.app.data.room.RoomOutboxStore
import com.roadtrip.app.notifications.AndroidNotificationPoster
import com.roadtrip.core.api.ApiException
import com.roadtrip.core.api.BingoCard
import com.roadtrip.core.api.Checklist
import com.roadtrip.core.api.Config
import com.roadtrip.core.api.Destination
import com.roadtrip.core.api.DestinationCreate
import com.roadtrip.core.api.DestinationPatch
import com.roadtrip.core.api.Game
import com.roadtrip.core.api.HttpRoadtripApi
import com.roadtrip.core.api.JournalEntry
import com.roadtrip.core.api.JournalPage
import com.roadtrip.core.api.Leg
import com.roadtrip.core.api.MapState
import com.roadtrip.core.api.OnlineMonitor
import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.RoadtripApi
import com.roadtrip.core.api.Trip
import com.roadtrip.core.api.TripStatus
import com.roadtrip.core.api.TripSummary
import com.roadtrip.core.common.SystemClock
import com.roadtrip.core.common.Timestamps
import com.roadtrip.core.games.LobbyRefresher
import com.roadtrip.core.journal.JournalComposer
import com.roadtrip.core.notifications.NotificationPipeline
import com.roadtrip.core.notifications.VisibleContext
import com.roadtrip.core.storage.CacheStore
import com.roadtrip.core.storage.CursorStore
import com.roadtrip.core.sync.InboxPuller
import com.roadtrip.core.sync.JournalCacheApplier
import com.roadtrip.core.sync.OutboxQueue
import com.roadtrip.core.sync.SyncEngine
import com.roadtrip.core.sync.SyncScheduler
import com.roadtrip.core.sync.SyncTrigger
import com.roadtrip.core.trips.TripScopedCacheStore
import com.roadtrip.core.trips.TripStateReducer
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

/**
 * Manual dependency container (no DI framework). Built once by [com.roadtrip.app.RoadtripApplication];
 * wires the pure-JVM core components to Room/DataStore/OkHttp and owns the app-wide sync pass.
 */
class AppContainer(private val context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = AppSettings(context, appScope)

    // ---- HTTP ---------------------------------------------------------------------------

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var httpApi: RoadtripApi = buildApi(settings.serverUrl.value)

    /** Stable facade; swaps the underlying client when the server URL setting changes. */
    val api: RoadtripApi = DelegatingRoadtripApi { httpApi }

    private fun buildApi(baseUrl: String): RoadtripApi = try {
        HttpRoadtripApi(baseUrl, { settings.get()?.id }, okHttp)
    } catch (e: IllegalArgumentException) {
        // Malformed URL typed into settings: fall back to the default so the app keeps working.
        HttpRoadtripApi(AppSettings.DEFAULT_SERVER_URL, { settings.get()?.id }, okHttp)
    }

    // ---- storage ------------------------------------------------------------------------

    private val database: RoadtripDatabase = Room.databaseBuilder(
        context.applicationContext,
        RoadtripDatabase::class.java,
        "roadtrip.db",
    )
        // Tables are tiny (family-trip scale); synchronous core ports may be touched from
        // the main thread on user actions like posting a journal entry.
        .allowMainThreadQueries()
        .fallbackToDestructiveMigration()
        .build()

    val outboxStore = RoomOutboxStore(database.outboxDao())
    val cursorStore = RoomCursorStore(database.cursorDao())

    private fun <T> cache(key: String, serializer: KSerializer<T>): CacheStore<T> =
        RoomCacheStore(database.cacheDao(), key, serializer)

    // ---- trips (docs/spec/09-trips.md) ----------------------------------------------------

    val tripsCache = cache("trips", ListSerializer(Trip.serializer()))

    /**
     * Trip id whose data the live caches hold: the active trip, else the most recently
     * ended one — mirroring the server's default read scope. Null until the first trips
     * fetch (pre-trips backend), which keeps the legacy unscoped cache keys (ANDTRIP-002).
     */
    val viewedTripId = MutableStateFlow(
        tripsCache.read()?.value?.let { TripStateReducer.viewedTrip(it)?.id },
    )

    /** Null = trips unknown yet (never fetched); the tracker stays permissive then. */
    val hasActiveTrip = MutableStateFlow(
        tripsCache.read()?.value?.any { it.status == TripStatus.ACTIVE },
    )

    /** The tracker never runs between trips (09-trips.md interface decision). */
    fun trackerMayRun(): Boolean = hasActiveTrip.value != false

    /** Point the scoped caches + tracker gate at the fresh trips list (ANDTRIP-002). */
    private fun applyTripState(trips: List<Trip>) {
        viewedTripId.value = TripStateReducer.viewedTrip(trips)?.id
        hasActiveTrip.value = trips.any { it.status == TripStatus.ACTIVE }
    }

    /** Room cache keyed per trip (`journal_<tripId>`, ...) so histories never mix (ANDTRIP-002). */
    private fun <T> tripScopedCache(base: String, serializer: KSerializer<T>): CacheStore<T> =
        TripScopedCacheStore(base, { viewedTripId.value }) { key -> cache(key, serializer) }

    val journalCache = tripScopedCache("journal", ListSerializer(JournalEntry.serializer()))
    val mapCache = tripScopedCache("map", MapState.serializer())
    val destinationsCache = cache("destinations", ListSerializer(Destination.serializer()))
    /** The shared license-plate bingo card, per trip like the journal (ANDBNG-004). */
    val bingoCache = tripScopedCache("bingo", BingoCard.serializer())
    /** The planned trip's staged itinerary, keyed by the planned trip id (ANDTRIP-007). */
    fun stagedDestinationsCache(tripId: String): CacheStore<List<Destination>> =
        cache("destinations_$tripId", ListSerializer(Destination.serializer()))
    val checklistCache = tripScopedCache("checklist", Checklist.serializer())
    val configCache = cache("config", Config.serializer())
    val gamesCache = cache("games", ListSerializer(Game.serializer()))
    val legsCache = tripScopedCache("legs", ListSerializer(Leg.serializer()))
    val tripSummaryCache = tripScopedCache("trip_summary", TripSummary.serializer())
    val profilesCache = cache("profiles", ListSerializer(Profile.serializer()))

    /** Per-game recorded move stream, kept so finished-game replays work offline (ANDGAME-008). */
    fun gameMovesCache(gameId: String): CacheStore<List<JsonObject>> =
        cache("game_moves_$gameId", ListSerializer(JsonObject.serializer()))

    fun gameCache(gameId: String): CacheStore<Game> = cache("game_$gameId", Game.serializer())

    // ---- core wiring ----------------------------------------------------------------------

    private val clock = SystemClock

    val outboxQueue = OutboxQueue(outboxStore, clock)
    val journalComposer = JournalComposer(outboxQueue)
    val onlineMonitor = OnlineMonitor(api)

    private val syncEngine = SyncEngine(api, outboxStore, deviceId = settings.deviceId)

    private val profileLookup: (String) -> Profile? = { id ->
        profilesCache.read()?.value?.firstOrNull { it.id == id }
    }

    private val inboxPuller = InboxPuller(
        api = api,
        cursors = cursorStore,
        appliers = listOf(
            JournalCacheApplier(journalCache, clock, profileLookup),
            MapCacheApplier(mapCache, cursorStore, clock),
            ChecklistCacheApplier(checklistCache, cursorStore, clock),
            GamesCacheApplier(gamesCache, cursorStore, clock),
            // Live plate.* events fill/clear bingo cells between refreshes (ANDBNG-004).
            BingoCacheApplier(bingoCache, cursorStore, clock, profileLookup),
        ),
    )

    // ---- notifications --------------------------------------------------------------------

    /** Set by the app shell from the current navigation route (ANDNOTIF-004). */
    val visibleContext = MutableStateFlow<VisibleContext?>(null)

    /** True while MainActivity is started; suppression only applies to a visible screen. */
    val activityVisible = MutableStateFlow(false)

    private val notificationPoster = AndroidNotificationPoster(context.applicationContext)

    private val notificationPipeline = NotificationPipeline(
        cursors = cursorStore,
        poster = notificationPoster,
        visibleContext = { if (activityVisible.value) visibleContext.value else null },
    )

    // ---- tracker state shared with the settings screen (ANDLOC-007) ------------------------

    val trackerGpsWarning = MutableStateFlow(false)

    // ---- sync orchestration -----------------------------------------------------------------

    /** Bumped after every sync pass so screens re-read the Room caches. */
    val refreshTick = MutableStateFlow(0L)

    val syncScheduler = SyncScheduler(::syncPass)

    /** Fire-and-forget sync from UI/service call sites; failures stay local (offline is normal). */
    fun requestSync(trigger: SyncTrigger) {
        appScope.launch {
            try {
                when (trigger) {
                    SyncTrigger.FOREGROUND -> syncScheduler.onForeground()
                    SyncTrigger.CONNECTIVITY_REGAINED -> syncScheduler.onConnectivityRegained()
                    SyncTrigger.PERIODIC -> syncScheduler.onPeriodic()
                    SyncTrigger.POST_WRITE -> syncScheduler.onLocalWrite()
                }
            } catch (e: Exception) {
                // Never crash on background sync problems; the next trigger retries.
            }
        }
    }

    /**
     * Shared reload behind the games lobby's pull-to-refresh gesture and its header refresh
     * button (ANDGAME-010): request a foreground sync and refetch the games cache. Offline is
     * short-circuited in [LobbyRefresher] so the indicator never spins indefinitely.
     */
    private val lobbyRefresher = LobbyRefresher(
        isOnline = { onlineMonitor.online.value },
        requestForegroundSync = { requestSync(SyncTrigger.FOREGROUND) },
        fetchGames = { withContext(Dispatchers.IO) { api.getGames() } },
        storeGames = { games ->
            gamesCache.write(games, clock.now())
            bumpTick()
        },
    )

    /** Reloads the games lobby; returns false (without spinning) when offline. */
    suspend fun reloadLobby(): Boolean = lobbyRefresher.reload()

    private suspend fun syncPass(trigger: SyncTrigger) = withContext(Dispatchers.IO) {
        try {
            val online = onlineMonitor.check()
            if (!online) return@withContext

            val flush = syncEngine.flush()
            if (flush.networkFailure) {
                onlineMonitor.noteSyncResult(false)
                return@withContext
            }

            var rounds = 0
            while (rounds < MAX_PULL_ROUNDS && inboxPuller.pullOnce() > 0) {
                rounds++
            }

            refreshReadModels()
            processNotifications()
        } catch (e: IOException) {
            onlineMonitor.noteSyncResult(false)
        } catch (e: Exception) {
            // Server-side errors (4xx/5xx) abort the pass but leave the app usable offline.
        } finally {
            bumpTick()
        }
    }

    /** Best-effort refresh of every cached read model; each endpoint fails independently. */
    private suspend fun refreshReadModels() {
        val now = clock.now()
        // Trips first: the trip-scoped caches below must land under the fresh trip's key
        // so an end/start switchover never mixes histories (ANDTRIP-002).
        runCatching {
            val trips = api.getTrips()
            tripsCache.write(trips, now)
            applyTripState(trips)
        }
        runCatching { profilesCache.write(api.getProfiles(), now) }
        runCatching { configCache.write(api.getConfig(), now) }
        runCatching { destinationsCache.write(api.getDestinations(), now) }
        runCatching { mapCache.write(api.getMap(), now) }
        runCatching { checklistCache.write(api.getChecklist(), now) }
        runCatching { gamesCache.write(api.getGames(), now) }
        runCatching { legsCache.write(api.getLegs(), now) }
        runCatching { tripSummaryCache.write(api.getTripSummary(), now) }
        runCatching { bingoCache.write(api.getBingo(), now) }
        // The planned trip's staged itinerary previews on its card offline (ANDTRIP-007).
        runCatching {
            val planned = tripsCache.read()?.value.orEmpty().firstOrNull { it.status == TripStatus.PLANNED }
            if (planned != null) {
                stagedDestinationsCache(planned.id).write(api.getDestinations(trip = planned.id), now)
            }
        }
        runCatching { mergeJournalPage(api.getJournal(limit = JOURNAL_PAGE)) }
    }

    private suspend fun processNotifications() {
        val self = settings.get() ?: return
        runCatching {
            val after = cursorStore.get(CursorStore.NOTIFICATIONS)
            val page = api.getNotifications(after = after)
            notificationPipeline.process(page, self.id)
        }
    }

    // ---- helpers used by screens ---------------------------------------------------------------

    fun bumpTick() {
        refreshTick.value += 1
    }

    /** Merges a server journal page into the cache, de-duplicated by seq (ANDJRNL-005). */
    fun mergeJournalPage(page: JournalPage) {
        val current = journalCache.read()?.value.orEmpty()
        val known = current.mapTo(HashSet()) { it.seq }
        val incoming = page.entries.filter { it.seq !in known }
        if (incoming.isEmpty() && journalCache.read() != null) return
        val merged = (current + incoming).sortedByDescending { Timestamps.parse(it.ts) }
        journalCache.write(merged, clock.now())
    }

    /** Queues a post locally (renders immediately as "syncing", ANDJRNL-002) and syncs. */
    fun postJournal(text: String) {
        journalComposer.post(text)
        bumpTick()
        requestSync(SyncTrigger.POST_WRITE)
    }

    /**
     * Parent destination edits (ANDMAP-006) via the parent-only admin endpoints
     * (POST/PATCH/DELETE /api/destinations — see backend 03-api.md). These are online-only
     * administrative actions (the server keeps the one-active-destination invariant), so
     * they call the API directly instead of the offline outbox; failures surface through
     * [destinationError] and the screen re-syncs on success.
     */
    val destinationError = MutableStateFlow<String?>(null)

    // A non-null [trip] stages the write against the planned trip (?trip=<plannedId>,
    // ANDTRIP-007) — same parent-only online-only rules, different itinerary.
    fun addDestination(name: String, lat: Double, lon: Double, trip: String? = null) = destinationMutation(trip) {
        api.createDestination(DestinationCreate(name = name, lat = lat, lon = lon), trip)
    }

    fun reorderDestination(id: String, orderIndex: Int, trip: String? = null) = destinationMutation(trip) {
        api.updateDestination(id, DestinationPatch(orderIndex = orderIndex), trip)
    }

    fun removeDestination(id: String, trip: String? = null) = destinationMutation(trip) {
        api.deleteDestination(id, trip)
    }

    private fun destinationMutation(trip: String? = null, block: suspend () -> Unit) {
        appScope.launch {
            try {
                block()
                destinationError.value = null
                // Staged writes refresh the planned trip's cached itinerary right away.
                if (trip != null) {
                    runCatching {
                        stagedDestinationsCache(trip).write(api.getDestinations(trip = trip), clock.now())
                    }
                }
                requestSync(SyncTrigger.POST_WRITE)
            } catch (e: ApiException) {
                destinationError.value = e.message ?: "Destination change rejected"
            } catch (e: IOException) {
                destinationError.value = "Offline — destination changes need the server"
            }
            bumpTick()
        }
    }

    /**
     * Parent-only, online-only, confirm-dialog trip lifecycle actions (ANDTRIP-001/004).
     * Like destination edits these call the API directly — the server arbitrates the
     * single active trip and answers a concurrent second start with 409 `conflict`.
     */
    val tripActionError = MutableStateFlow<String?>(null)

    fun startTrip(name: String?) = tripMutation {
        api.createTrip(name?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun endTrip(tripId: String) = tripMutation {
        api.endTrip(tripId)
    }

    // ---- itinerary planner (ANDTRIP-006/008): parent-only, online-only, server-arbitrated.

    fun createPlannedTrip(name: String?, plannedStartAt: String?) = tripMutation {
        api.createPlannedTrip(
            name?.trim()?.takeIf { it.isNotEmpty() },
            plannedStartAt?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun updatePlannedTrip(tripId: String, name: String?, plannedStartAt: String?) = tripMutation {
        api.patchTrip(
            tripId,
            name?.trim()?.takeIf { it.isNotEmpty() },
            plannedStartAt?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun deletePlannedTrip(tripId: String) = tripMutation {
        api.deleteTrip(tripId)
    }

    /**
     * "Road trip starts now" on the planned card: /start adopts the staged itinerary
     * server-side; the re-pull in [tripMutation] switches the scoped caches to the new
     * active trip and the follow-up sync fills them (ANDTRIP-008).
     */
    fun activatePlannedTrip(tripId: String) = tripMutation {
        api.startTrip(tripId)
    }

    private fun tripMutation(block: suspend () -> Unit) {
        appScope.launch {
            try {
                block()
                tripActionError.value = null
                // Re-pull trips immediately so the banner and the scoped caches switch over.
                runCatching {
                    val trips = api.getTrips()
                    tripsCache.write(trips, clock.now())
                    applyTripState(trips)
                }
                requestSync(SyncTrigger.POST_WRITE)
            } catch (e: ApiException) {
                tripActionError.value = e.message ?: "Trip change rejected"
            } catch (e: IOException) {
                tripActionError.value = "Offline — starting or ending a road trip needs the server"
            }
            bumpTick()
        }
    }

    /**
     * License-plate bingo spots/removals queue offline like journal posts — any profile,
     * attributed via the normal sync path, real tap timestamps (ANDBNG-001/002).
     */
    fun spotPlate(stateCode: String) {
        outboxQueue.enqueuePlateSpotted(stateCode)
        bumpTick()
        requestSync(SyncTrigger.POST_WRITE)
    }

    fun unspotPlate(stateCode: String) {
        outboxQueue.enqueuePlateUnspotted(stateCode)
        bumpTick()
        requestSync(SyncTrigger.POST_WRITE)
    }

    fun selectProfile(profile: Profile?) {
        settings.set(profile)
        if (profile != null) requestSync(SyncTrigger.FOREGROUND)
    }

    init {
        appScope.launch {
            settings.serverUrl.collect { url ->
                httpApi = buildApi(url)
                onlineMonitor.check()
            }
        }
    }

    companion object {
        private const val MAX_PULL_ROUNDS = 30
        private const val JOURNAL_PAGE = 50
    }
}
