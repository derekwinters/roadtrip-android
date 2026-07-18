package com.roadtrip.core.games

import com.roadtrip.core.api.EventDto
import com.roadtrip.core.api.EventsPage
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GameStreamFollowerTest {
    private val api = FakeRoadtripApi()

    private fun moveEvent(seq: Long, moveNo: Int) = TestData.gameMoveEvent(
        seq = seq, gameId = "g-1", moveNo = moveNo,
        move = buildJsonObject { put("cell", moveNo) }, actorId = TestData.parent.id,
    )

    @Test
    fun `applies pulled game events in order and advances the cursor ANDGAME-005`() = runTest {
        api.feed += moveEvent(10, 1)
        api.feed += moveEvent(11, 2)
        val seen = mutableListOf<Long>()
        val follower = GameStreamFollower(api, "g-1", { seen += it.seq })

        assertEquals(2, follower.pollOnce())
        assertEquals(listOf(10L, 11L), seen)
        assertEquals(11L, follower.cursor)

        // Nothing new: the cursor holds.
        assertEquals(0, follower.pollOnce())
        assertEquals(11L, follower.cursor)
    }

    @Test
    fun `the long-poll loop surfaces the opponent move without manual refresh ANDGAME-005`() = runTest {
        // A long-poll fake: the request parks until an event is published.
        val pipe = Channel<EventDto>(Channel.UNLIMITED)
        api.gameEventsHandler = { _, after, _ ->
            val event = pipe.receive() // holds the request open, like wait=25 on the server
            EventsPage(listOf(event), event.seq)
        }
        val seen = mutableListOf<Int>()
        val follower = GameStreamFollower(
            api, "g-1",
            { seen += it.payload["move_no"]!!.jsonPrimitive.int },
        )

        val loop = launch { follower.run() }
        runCurrent()
        assertEquals(emptyList(), seen) // request parked, no polling storm

        // The opponent moves — the open long-poll returns and the board updates by itself.
        pipe.send(moveEvent(20, 7))
        runCurrent()
        assertEquals(listOf(7), seen)
        assertEquals(20L, follower.cursor)

        pipe.send(moveEvent(21, 8))
        runCurrent()
        assertEquals(listOf(7, 8), seen)

        loop.cancel() // screen closed
    }
}
