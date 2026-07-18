package com.roadtrip.core.testing

import com.roadtrip.core.api.EventDto
import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameMode
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.GameType
import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.Trip
import com.roadtrip.core.api.TripStatus
import com.roadtrip.core.common.Clock
import com.roadtrip.core.common.IdGenerator
import com.roadtrip.core.common.Role
import com.roadtrip.core.common.Timestamps
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class FakeClock(var current: Instant = T0) : Clock {
    override fun now(): Instant = current

    fun advanceSeconds(seconds: Long) {
        current = current.plusSeconds(seconds)
    }

    companion object {
        val T0: Instant = Instant.parse("2026-07-18T12:00:00Z")
    }
}

class SequentialIds(private val prefix: String = "evt") : IdGenerator {
    private var next = 1

    override fun newId(): String = "$prefix-${next++}"
}

object TestData {
    val parent = Profile("p-parent", "Derek", "bear", Role.PARENT)
    val kid = Profile("p-kid", "Maya", "fox", Role.KID)
    val otherKid = Profile("p-kid2", "Theo", "owl", Role.KID)

    fun t(secondsFromNoon: Long): Instant = FakeClock.T0.plusSeconds(secondsFromNoon)

    fun ts(secondsFromNoon: Long): String = Timestamps.format(t(secondsFromNoon))

    fun event(
        seq: Long,
        type: String,
        payload: JsonObject,
        actorId: String? = null,
        clientTs: String = ts(seq),
        serverTs: String = ts(seq + 1),
    ): EventDto = EventDto(seq, "server-$seq", type, actorId, payload, clientTs, serverTs)

    fun journalPostEvent(seq: Long, actorId: String, text: String, clientTs: String = ts(seq)): EventDto =
        event(seq, "journal.post", buildJsonObject { put("text", text) }, actorId, clientTs)

    fun pingEvent(seq: Long, lat: Double, lon: Double, clientTs: String = ts(seq)): EventDto =
        event(
            seq,
            "location.ping",
            buildJsonObject {
                put("lat", lat)
                put("lon", lon)
            },
            actorId = parent.id,
            clientTs = clientTs,
        )

    fun stateCrossingEvent(seq: Long, state: String, stateCode: String, clientTs: String = ts(seq)): EventDto =
        event(
            seq,
            "location.crossing.state",
            buildJsonObject {
                put("state", state)
                put("state_code", stateCode)
            },
            clientTs = clientTs,
        )

    fun gameMoveEvent(seq: Long, gameId: String, moveNo: Int, move: JsonObject, actorId: String): EventDto =
        event(
            seq,
            "game.move",
            buildJsonObject {
                put("game_id", gameId)
                put("move_no", moveNo)
                put("move", move)
            },
            actorId = actorId,
        )

    fun trip(
        id: String = "trip-1",
        name: String = "Summer Loop",
        status: TripStatus = TripStatus.ACTIVE,
        startedAt: String? = ts(0),
        endedAt: String? = null,
        plannedStartAt: String? = null,
    ): Trip = Trip(
        id = id,
        name = name,
        status = status,
        startedAt = startedAt,
        endedAt = endedAt,
        plannedStartAt = plannedStartAt,
    )

    fun plannedTrip(
        id: String = "trip-plan",
        name: String = "Desert Loop",
        plannedStartAt: String? = "~ early August",
    ): Trip = trip(id, name, TripStatus.PLANNED, startedAt = null, plannedStartAt = plannedStartAt)

    fun plateSpottedEvent(seq: Long, stateCode: String, actorId: String, clientTs: String = ts(seq)): EventDto =
        event(seq, "plate.spotted", buildJsonObject { put("state_code", stateCode) }, actorId, clientTs)

    fun plateUnspottedEvent(seq: Long, stateCode: String, actorId: String, clientTs: String = ts(seq)): EventDto =
        event(seq, "plate.unspotted", buildJsonObject { put("state_code", stateCode) }, actorId, clientTs)

    fun game(
        id: String = "g-1",
        type: GameType = GameType.TICTACTOE,
        mode: GameMode = GameMode.OPEN,
        status: GameStatus = GameStatus.OPEN,
        createdBy: String = parent.id,
        invitedProfileId: String? = null,
        opponentId: String? = null,
        moveCount: Int = 0,
        turn: String? = null,
    ): Game = Game(
        id = id,
        gameType = type,
        mode = mode,
        status = status,
        createdBy = createdBy,
        invitedProfileId = invitedProfileId,
        opponentId = opponentId,
        moveCount = moveCount,
        turn = turn,
    )
}
