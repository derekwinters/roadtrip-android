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

/** A spottable plate region; [section] is the grid section header it renders under. */
data class PlateRegion(val code: String, val name: String, val section: String)

/**
 * The static plate-region grid source (docs/spec/10-bingo.md, ANDBNG-001): the United
 * States section (50 states + DC) then the Canada section (10 provinces + 3 territories),
 * each alphabetical by name. Invariant: section order and the code list live only here —
 * nothing branches on country; a region's section is plain data.
 */
object PlateRegions {
    const val UNITED_STATES = "United States"
    const val CANADA = "Canada"

    private fun us(code: String, name: String) = PlateRegion(code, name, UNITED_STATES)
    private fun ca(code: String, name: String) = PlateRegion(code, name, CANADA)

    val ALL: List<PlateRegion> = listOf(
        us("AL", "Alabama"),
        us("AK", "Alaska"),
        us("AZ", "Arizona"),
        us("AR", "Arkansas"),
        us("CA", "California"),
        us("CO", "Colorado"),
        us("CT", "Connecticut"),
        us("DE", "Delaware"),
        us("DC", "District of Columbia"),
        us("FL", "Florida"),
        us("GA", "Georgia"),
        us("HI", "Hawaii"),
        us("ID", "Idaho"),
        us("IL", "Illinois"),
        us("IN", "Indiana"),
        us("IA", "Iowa"),
        us("KS", "Kansas"),
        us("KY", "Kentucky"),
        us("LA", "Louisiana"),
        us("ME", "Maine"),
        us("MD", "Maryland"),
        us("MA", "Massachusetts"),
        us("MI", "Michigan"),
        us("MN", "Minnesota"),
        us("MS", "Mississippi"),
        us("MO", "Missouri"),
        us("MT", "Montana"),
        us("NE", "Nebraska"),
        us("NV", "Nevada"),
        us("NH", "New Hampshire"),
        us("NJ", "New Jersey"),
        us("NM", "New Mexico"),
        us("NY", "New York"),
        us("NC", "North Carolina"),
        us("ND", "North Dakota"),
        us("OH", "Ohio"),
        us("OK", "Oklahoma"),
        us("OR", "Oregon"),
        us("PA", "Pennsylvania"),
        us("RI", "Rhode Island"),
        us("SC", "South Carolina"),
        us("SD", "South Dakota"),
        us("TN", "Tennessee"),
        us("TX", "Texas"),
        us("UT", "Utah"),
        us("VT", "Vermont"),
        us("VA", "Virginia"),
        us("WA", "Washington"),
        us("WV", "West Virginia"),
        us("WI", "Wisconsin"),
        us("WY", "Wyoming"),
        ca("AB", "Alberta"),
        ca("BC", "British Columbia"),
        ca("MB", "Manitoba"),
        ca("NB", "New Brunswick"),
        ca("NL", "Newfoundland and Labrador"),
        ca("NT", "Northwest Territories"),
        ca("NS", "Nova Scotia"),
        ca("NU", "Nunavut"),
        ca("ON", "Ontario"),
        ca("PE", "Prince Edward Island"),
        ca("QC", "Quebec"),
        ca("SK", "Saskatchewan"),
        ca("YT", "Yukon"),
    )

    val byCode: Map<String, PlateRegion> = ALL.associateBy { it.code }
}

/** One grid cell, ready to render (ANDBNG-001/002). */
data class BingoCellView(
    /** The region's code — named after the `state_code` wire field it mirrors. */
    val stateCode: String,
    val regionName: String,
    /** The grid section this cell renders under (ANDBNG-001). */
    val section: String,
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
    val regionName: String,
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

/** One titled grid section, in [PlateRegions.ALL] order (ANDBNG-001). */
data class BingoSection(val title: String, val cells: List<BingoCellView>)

data class BingoScreenState(
    /** Every cell in [PlateRegions.ALL] order — the sections concatenated. */
    val cells: List<BingoCellView>,
    val log: List<BingoLogRow>,
    val standings: List<BingoStanding>,
    val readOnly: Boolean,
) {
    /** The cells grouped under their section headers, in list order (ANDBNG-001). */
    val sections: List<BingoSection>
        get() = cells.groupBy { it.section }.map { (title, sectionCells) -> BingoSection(title, sectionCells) }
}

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

        val views = PlateRegions.ALL.map { region ->
            val cell = cells[region.code]
            BingoCellView(
                stateCode = region.code,
                regionName = region.name,
                section = region.section,
                spottedById = cell?.spottedBy,
                spottedByName = cell?.spottedBy?.let(::nameOf),
                spottedAt = cell?.spottedAt?.let(Timestamps::parse),
                pending = region.code in pendingCodes,
                canSpot = !readOnly && cell == null,
                canRemove = !readOnly && cell != null && canRemove(cell, selfProfile),
            )
        }

        val log = card.log
            .map { entry ->
                BingoLogRow(
                    stateCode = entry.stateCode,
                    regionName = PlateRegions.byCode[entry.stateCode]?.name ?: entry.stateCode,
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
