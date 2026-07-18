package com.roadtrip.core.api

import com.roadtrip.core.common.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before

class HttpRoadtripApiTest {
    private lateinit var server: MockWebServer
    private var profileId: String? = "profile-123"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api() = HttpRoadtripApi(server.url("/").toString(), { profileId })

    private fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body),
        )
    }

    @Test
    fun `attaches X-Profile-Id from the stored profile to every request AND-002`() = runTest {
        enqueueJson("""{"status":"ok","version":"0.1.0"}""")
        enqueueJson("""{"entries":[],"next_before":null}""")
        enqueueJson("""{"breadcrumb":[]}""")
        enqueueJson("""{"results":[]}""")
        enqueueJson(
            """{"seq":9,"kind":"post","ts":"2026-07-18T12:00:00Z","text":"hi"}""",
            code = 201,
        )

        val api = api()
        api.health()
        api.getJournal()
        api.getMap()
        api.syncBatch(SyncBatchRequest(deviceId = "phone-1", events = emptyList()))
        api.postJournal("hi")

        repeat(5) {
            assertEquals("profile-123", server.takeRequest().getHeader("X-Profile-Id"))
        }
    }

    @Test
    fun `serializes bodies and query strings with the snake_case contract names AND-002`() = runTest {
        enqueueJson("""{"results":[{"event_id":"e-1","status":"accepted","seq":4}]}""")
        enqueueJson(
            """{"ping_interval_s":60,"stop_radius_m":100,"min_stop_duration_min":10,
               "arrival_radius_m":800,"city_radius_km":10}""",
        )
        enqueueJson("""{"events":[],"next_after":7}""")

        val api = api()
        val batchResult = api.syncBatch(
            SyncBatchRequest(
                deviceId = "phone-1",
                events = listOf(
                    ClientEvent(
                        "e-1",
                        "journal.post",
                        "2026-07-18T12:00:00Z",
                        buildJsonObject { put("text", "hi") },
                    ),
                ),
            ),
        )
        api.putConfig(ConfigPatch(pingIntervalS = 60))
        val events = api.getEvents(after = 5, limit = 100, types = listOf("journal.post"), waitSeconds = 20)

        val batchBody = server.takeRequest()
        assertEquals("POST", batchBody.method)
        assertEquals("/api/sync/batch", batchBody.path)
        val batchJson = batchBody.body.readUtf8()
        assertTrue(batchJson.contains(""""event_id":"e-1""""))
        assertTrue(batchJson.contains(""""client_ts""""))
        assertTrue(batchJson.contains(""""device_id":"phone-1""""))
        assertEquals(SyncStatus.ACCEPTED, batchResult.results.single().status)
        assertEquals(4L, batchResult.results.single().seq)

        val configBody = server.takeRequest()
        assertEquals("PUT", configBody.method)
        assertEquals("""{"ping_interval_s":60}""", configBody.body.readUtf8())

        val eventsReq = server.takeRequest()
        assertEquals("/api/events?after=5&limit=100&types=journal.post&wait=20", eventsReq.path)
        assertEquals(7L, events.nextAfter)
    }

    @Test
    fun `parses snake_case responses and ignores unknown fields AND-002`() = runTest {
        enqueueJson(
            """{"id":"g-1","game_type":"hangman","mode":"challenge","status":"open",
               "created_by":"p-1","invited_profile_id":"p-2","move_count":3,
               "some_future_field":{"x":1}}""",
        )

        val game = api().getGame("g-1")

        assertEquals(GameType.HANGMAN, game.gameType)
        assertEquals(GameMode.CHALLENGE, game.mode)
        assertEquals("p-2", game.invitedProfileId)
        assertEquals(3, game.moveCount)
    }

    @Test
    fun `trip lifecycle endpoints follow the backend contract ANDTRIP-001`() = runTest {
        enqueueJson(
            """[{"id":"t-1","name":"Summer Loop","status":"ended",
               "started_at":"2026-06-01T12:00:00Z","ended_at":"2026-06-20T12:00:00Z"}]""",
        )
        enqueueJson(
            """{"id":"t-2","name":"Fall Colors","status":"active","started_at":"2026-07-18T12:00:00Z"}""",
            code = 201,
        )
        enqueueJson(
            """{"id":"t-2","name":"Fall Colors","status":"ended",
               "started_at":"2026-07-18T12:00:00Z","ended_at":"2026-07-19T12:00:00Z"}""",
        )
        enqueueJson(
            """{"id":"t-2","name":"Renamed","status":"ended",
               "started_at":"2026-07-18T12:00:00Z","ended_at":"2026-07-19T12:00:00Z"}""",
        )

        val api = api()
        val trips = api.getTrips()
        assertEquals(TripStatus.ENDED, trips.single().status)
        assertEquals("2026-06-20T12:00:00Z", trips.single().endedAt)
        assertEquals(TripStatus.ACTIVE, api.createTrip("Fall Colors").status)
        assertEquals(TripStatus.ENDED, api.endTrip("t-2").status)
        assertEquals("Renamed", api.renameTrip("t-2", "Renamed").name)

        assertEquals("/api/trips", server.takeRequest().path)
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/api/trips", create.path)
        assertEquals("""{"name":"Fall Colors"}""", create.body.readUtf8())
        val end = server.takeRequest()
        assertEquals("POST", end.method)
        assertEquals("/api/trips/t-2/end", end.path)
        val rename = server.takeRequest()
        assertEquals("PATCH", rename.method)
        assertEquals("/api/trips/t-2", rename.path)
        assertEquals("""{"name":"Renamed"}""", rename.body.readUtf8())
    }

    @Test
    fun `scoped readers pass the trip query parameter and the per-trip summary path ANDTRIP-003`() = runTest {
        enqueueJson("""{"entries":[],"next_before":null}""")
        enqueueJson("""{"states":[],"cities":[],"stops":[]}""")
        enqueueJson("""[]""")
        enqueueJson("""{"breadcrumb":[]}""")
        enqueueJson(
            """{"miles":1204.5,"wall_minutes":100,"moving_minutes":80,
               "states_count":5,"stop_count":3,"games_played":2}""",
        )

        val api = api()
        api.getJournal(limit = 20, trip = "t-1")
        api.getChecklist(trip = "t-1")
        api.getLegs(trip = "t-1")
        api.getMap(maxPoints = 100, trip = "t-1")
        assertEquals(1204.5, api.getTripSummary("t-1").miles)

        assertEquals("/api/journal?limit=20&trip=t-1", server.takeRequest().path)
        assertEquals("/api/checklist?trip=t-1", server.takeRequest().path)
        assertEquals("/api/legs?trip=t-1", server.takeRequest().path)
        assertEquals("/api/map?max_points=100&trip=t-1", server.takeRequest().path)
        assertEquals("/api/trips/t-1/summary", server.takeRequest().path)
    }

    @Test
    fun `sync batches with an actor override send that X-Profile-Id ANDLOC-008`() = runTest {
        enqueueJson("""{"results":[]}""")
        enqueueJson("""{"results":[]}""")

        val api = api()
        api.syncBatch(
            SyncBatchRequest(deviceId = "tablet-1", events = emptyList()),
            actorProfileId = "p-parent",
        )
        api.syncBatch(SyncBatchRequest(deviceId = "tablet-1", events = emptyList()))

        // The enabling parent's id overrides the signed-in profile for that batch only.
        assertEquals("p-parent", server.takeRequest().getHeader("X-Profile-Id"))
        assertEquals("profile-123", server.takeRequest().getHeader("X-Profile-Id"))
    }

    @Test
    fun `bootstrap profile create sends no X-Profile-Id when nobody is signed in AND-007`() = runTest {
        profileId = null // first run: no selected profile yet
        enqueueJson("""{"id":"p-1","name":"Derek","avatar":"bear","role":"parent"}""", code = 201)

        val created = api().createProfile("Derek", "bear", Role.PARENT)

        assertEquals(Role.PARENT, created.role)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/profiles", request.path)
        // The backend's zero-profiles bootstrap accepts exactly this header-less parent create.
        assertNull(request.getHeader("X-Profile-Id"))
        assertTrue(request.body.readUtf8().contains(""""role":"parent""""))
    }

    @Test
    fun `geocode hits the proxy with the query and parses snake_case matches ANDMAP-008`() = runTest {
        enqueueJson(
            """{"results":[{"display_name":"Moab, Grand County, Utah","lat":38.5733,"lon":-109.5498}]}""",
        )

        val matches = api().geocode("Moab UT")

        assertEquals("/api/geocode?q=Moab%20UT", server.takeRequest().path)
        assertEquals("Moab, Grand County, Utah", matches.single().displayName)
        assertEquals(38.5733, matches.single().lat)
        assertEquals(-109.5498, matches.single().lon)
    }

    @Test
    fun `geocode 503 surfaces the geocode_unavailable error code ANDMAP-009`() = runTest {
        enqueueJson(
            """{"error":{"code":"geocode_unavailable","message":"upstream geocoder unreachable"}}""",
            code = 503,
        )

        val ex = assertFailsWith<ApiException> { api().geocode("Moab") }

        assertEquals(503, ex.status)
        assertEquals("geocode_unavailable", ex.code)
    }

    @Test
    fun `maps non-2xx responses to ApiException with the contract error shape`() = runTest {
        enqueueJson("""{"error":{"code":"out_of_bounds","message":"ping_interval_s must be >= 5"}}""", code = 400)

        val ex = assertFailsWith<ApiException> { api().putConfig(ConfigPatch(pingIntervalS = 1)) }

        assertEquals(400, ex.status)
        assertEquals("out_of_bounds", ex.code)
        assertEquals("ping_interval_s must be >= 5", ex.message)
    }
}
