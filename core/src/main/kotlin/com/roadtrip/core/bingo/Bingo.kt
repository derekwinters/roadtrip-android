package com.roadtrip.core.bingo

import com.roadtrip.core.api.BingoCard
import com.roadtrip.core.api.BingoCell
import com.roadtrip.core.api.BingoLogAction
import com.roadtrip.core.api.BingoLogEntry
import com.roadtrip.core.api.EventDto
import com.roadtrip.core.api.Profile
import com.roadtrip.core.common.Role
import com.roadtrip.core.common.Timestamps
import com.roadtrip.core.sync.OutboxEntry
import java.time.Instant
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class UsState(val code: String, val name: String)

/** The static 50-states-plus-DC grid source (docs/spec/10-bingo.md, ANDBNG-001). */
object UsStates {
    val ALL: List<UsState> = listOf(
        UsState("AL", "Alabama"),
        UsState("AK", "Alaska"),
        UsState("AZ", "Arizona"),
        UsState("AR", "Arkansas"),
        UsState("CA", "California"),
        UsState("CO", "Colorado"),
        UsState("CT", "Connecticut"),
        UsState("DE", "Delaware"),
        UsState("DC", "District of Columbia"),
        UsState("FL", "Florida"),
        UsState("GA", "Georgia"),
        UsState("HI", "Hawaii"),
        UsState("ID", "Idaho"),
        UsState("IL", "Illinois"),
        UsState("IN", "Indiana"),
        UsState("IA", "Iowa"),
        UsState("KS", "Kansas"),
        UsState("KY", "Kentucky"),
        UsState("LA", "Louisiana"),
        UsState("ME", "Maine"),
        UsState("MD", "Maryland"),
        UsState("MA", "Massachusetts"),
        UsState("MI", "Michigan"),
        UsState("MN", "Minnesota"),
        UsState("MS", "Mississippi"),
        UsState("MO", "Missouri"),
        UsState("MT", "Montana"),
        UsState("NE", "Nebraska"),
        UsState("NV", "Nevada"),
        UsState("NH", "New Hampshire"),
        UsState("NJ", "New Jersey"),
        UsState("NM", "New Mexico"),
        UsState("NY", "New York"),
        UsState("NC", "North Carolina"),
        UsState("ND", "North Dakota"),
        UsState("OH", "Ohio"),
        UsState("OK", "Oklahoma"),
        UsState("OR", "Oregon"),
        UsState("PA", "Pennsylvania"),
        UsState("RI", "Rhode Island"),
        UsState("SC", "South Carolina"),
        UsState("SD", "South Dakota"),
        UsState("TN", "Tennessee"),
        UsState("TX", "Texas"),
        UsState("UT", "Utah"),
        UsState("VT", "Vermont"),
        UsState("VA", "Virginia"),
        UsState("WA", "Washington"),
        UsState("WV", "West Virginia"),
        UsState("WI", "Wisconsin"),
        UsState("WY", "Wyoming"),
    )

    val byCode: Map<String, UsState> = ALL.associateBy { it.code }
}

/** One grid cell, ready to render (ANDBNG-001/002). */
data class BingoCellView(
    val stateCode: String,
    val stateName: String,
    val spottedById: String?,
    val spottedByName: String?,
    val spottedAt: Instant?,
    /** A local spot/removal is still waiting in the outbox (ANDBNG-001/002). */
    val pending: Boolean,
    /** Empty cell, tappable by any profile (never on read-only cards). */
    val canSpot: Boolean,
    /** Remove offered to the original spotter or any parent only (ANDBNG-002). */
    val canRemove: Boolean,
) {
    val spotted: Boolean get() = spottedById != null
}

/** One chronological history line (ANDBNG-003). */
data class BingoLogRow(
    val stateCode: String,
    val stateName: String,
    val action: BingoLogAction,
    val profileId: String,
    val profileName: String,
    val ts: Instant,
)

/** Leaderboard row: standing spot count per profile (ANDBNG-004). */
data class BingoStanding(
    val profileId: String,
    val profileName: String,
    val count: Int,
)

data class BingoScreenState(
    val cells: List<BingoCellView>,
    val log: List<BingoLogRow>,
    val standings: List<BingoStanding>,
    val readOnly: Boolean,
)

/**
 * Folds the server card, the local pending outbox spots/removals, and live `plate.*`
 * feed events into the bingo screen state (docs/spec/10-bingo.md). The removal
 * permission rule mirrors the backend: only the original spotter or a parent.
 */
object BingoReducer {
    /** Mirrors the backend rule for the detail sheet's Remove action (ANDBNG-002). */
    fun canRemove(cell: BingoCell, self: Profile?): Boolean =
        self != null && (cell.spottedBy == self.id || self.role == Role.PARENT)

    fun reduce(
        card: BingoCard,
        pendingOutbox: List<OutboxEntry>,
        selfProfile: Profile?,
        profilesById: Map<String, Profile> = emptyMap(),
        readOnly: Boolean = false,
    ): BingoScreenState {
        fun nameOf(profileId: String): String = profilesById[profileId]?.name ?: profileId

        // Fold local pending ops over the server cells, in tap order, with the real tap
        // timestamps — the offline pattern journal posts use (ANDBNG-001/002).
        val cells = LinkedHashMap<String, BingoCell>()
        card.cells.forEach { cells[it.stateCode] = it }
        val pendingCodes = mutableSetOf<String>()
        val counts = card.counts.toMutableMap()

        val plateOps = pendingOutbox
            .filter { it.type == OutboxEntry.TYPE_PLATE_SPOTTED || it.type == OutboxEntry.TYPE_PLATE_UNSPOTTED }
            .sortedBy { it.clientTs }
        for (op in plateOps) {
            val code = op.payload["state_code"]?.jsonPrimitive?.contentOrNull ?: continue
            when (op.type) {
                OutboxEntry.TYPE_PLATE_SPOTTED -> {
                    if (selfProfile == null || cells.containsKey(code)) continue
                    cells[code] = BingoCell(code, selfProfile.id, Timestamps.format(op.clientTs))
                    counts[selfProfile.id] = (counts[selfProfile.id] ?: 0) + 1
                    pendingCodes += code
                }
                OutboxEntry.TYPE_PLATE_UNSPOTTED -> {
                    val existing = cells[code] ?: continue
                    if (!canRemove(existing, selfProfile)) continue
                    cells.remove(code)
                    counts[existing.spottedBy] = maxOf(0, (counts[existing.spottedBy] ?: 0) - 1)
                    pendingCodes += code
                }
            }
        }

        val views = UsStates.ALL.map { state ->
            val cell = cells[state.code]
            BingoCellView(
                stateCode = state.code,
                stateName = state.name,
                spottedById = cell?.spottedBy,
                spottedByName = cell?.spottedBy?.let(::nameOf),
                spottedAt = cell?.spottedAt?.let(Timestamps::parse),
                pending = state.code in pendingCodes,
                canSpot = !readOnly && cell == null,
                canRemove = !readOnly && cell != null && canRemove(cell, selfProfile),
            )
        }

        val log = card.log
            .map { entry ->
                BingoLogRow(
                    stateCode = entry.stateCode,
                    stateName = UsStates.byCode[entry.stateCode]?.name ?: entry.stateCode,
                    action = entry.action,
                    profileId = entry.profileId,
                    profileName = nameOf(entry.profileId),
                    ts = Timestamps.parse(entry.ts),
                )
            }
            .sortedBy { it.ts }

        val standings = counts
            .filterValues { it > 0 }
            .map { (profileId, count) -> BingoStanding(profileId, nameOf(profileId), count) }
            .sortedWith(compareByDescending<BingoStanding> { it.count }.thenBy { it.profileName })

        return BingoScreenState(cells = views, log = log, standings = standings, readOnly = readOnly)
    }

    /**
     * Folds one live `plate.*` feed event into a cached card without a reload
     * (ANDBNG-004) — the bingo analogue of pings extending the map breadcrumb. Spots on
     * an already-filled cell are no-ops (first spotter wins); removals are honored only
     * from the spotter or a parent, mirroring the backend rule.
     */
    fun applyEvent(
        card: BingoCard,
        event: EventDto,
        profilesById: Map<String, Profile> = emptyMap(),
    ): BingoCard {
        val code = event.payload["state_code"]?.jsonPrimitive?.contentOrNull ?: return card
        val actor = event.actorId ?: return card
        return when (event.type) {
            OutboxEntry.TYPE_PLATE_SPOTTED -> {
                if (card.cells.any { it.stateCode == code }) return card
                card.copy(
                    cells = card.cells + BingoCell(code, actor, event.clientTs),
                    log = card.log + BingoLogEntry(code, BingoLogAction.SPOTTED, actor, event.clientTs),
                    counts = card.counts + (actor to (card.counts[actor] ?: 0) + 1),
                )
            }
            OutboxEntry.TYPE_PLATE_UNSPOTTED -> {
                val cell = card.cells.firstOrNull { it.stateCode == code } ?: return card
                val actorIsParent = profilesById[actor]?.role == Role.PARENT
                if (actor != cell.spottedBy && !actorIsParent) return card
                card.copy(
                    cells = card.cells - cell,
                    log = card.log + BingoLogEntry(code, BingoLogAction.REMOVED, actor, event.clientTs),
                    counts = card.counts + (cell.spottedBy to maxOf(0, (card.counts[cell.spottedBy] ?: 0) - 1)),
                )
            }
            else -> card
        }
    }
}
