package com.roadtrip.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `maps non-2xx responses to ApiException with the contract error shape`() = runTest {
        enqueueJson("""{"error":{"code":"out_of_bounds","message":"ping_interval_s must be >= 5"}}""", code = 400)

        val ex = assertFailsWith<ApiException> { api().putConfig(ConfigPatch(pingIntervalS = 1)) }

        assertEquals(400, ex.status)
        assertEquals("out_of_bounds", ex.code)
        assertEquals("ping_interval_s must be >= 5", ex.message)
    }
}
