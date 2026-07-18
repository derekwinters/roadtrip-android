package com.roadtrip.app.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.roadtrip.app.MainActivity
import com.roadtrip.core.journal.NavTarget

/**
 * Encodes a core [NavTarget] into MainActivity launch extras and back, so tapping a
 * notification opens its deep-link target (ANDNOTIF-005). A unique data URI keeps
 * PendingIntents with different targets from collapsing into one another.
 */
object NavTargetExtras {
    private const val EXTRA_KIND = "com.roadtrip.app.nav_kind"
    private const val EXTRA_GAME_ID = "com.roadtrip.app.nav_game_id"
    private const val EXTRA_LAT = "com.roadtrip.app.nav_lat"
    private const val EXTRA_LON = "com.roadtrip.app.nav_lon"
    private const val EXTRA_STATE = "com.roadtrip.app.nav_state"
    private const val EXTRA_DEST = "com.roadtrip.app.nav_dest"

    private const val KIND_JOURNAL = "journal"
    private const val KIND_BOARD = "board"
    private const val KIND_REPLAY = "replay"
    private const val KIND_MAP = "map"
    private const val KIND_CHECKLIST = "checklist"
    private const val KIND_LEG = "leg"

    fun intentFor(context: Context, target: NavTarget): Intent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        when (target) {
            is NavTarget.Journal -> {
                intent.putExtra(EXTRA_KIND, KIND_JOURNAL)
                intent.data = Uri.parse("roadtrip://nav/journal")
            }
            is NavTarget.GameBoard -> {
                intent.putExtra(EXTRA_KIND, KIND_BOARD)
                intent.putExtra(EXTRA_GAME_ID, target.gameId)
                intent.data = Uri.parse("roadtrip://nav/board/${target.gameId}")
            }
            is NavTarget.GameReplay -> {
                intent.putExtra(EXTRA_KIND, KIND_REPLAY)
                intent.putExtra(EXTRA_GAME_ID, target.gameId)
                intent.data = Uri.parse("roadtrip://nav/replay/${target.gameId}")
            }
            is NavTarget.MapPin -> {
                intent.putExtra(EXTRA_KIND, KIND_MAP)
                intent.putExtra(EXTRA_LAT, target.lat)
                intent.putExtra(EXTRA_LON, target.lon)
                intent.data = Uri.parse("roadtrip://nav/map/${target.lat},${target.lon}")
            }
            is NavTarget.ChecklistScreen -> {
                intent.putExtra(EXTRA_KIND, KIND_CHECKLIST)
                target.stateCode?.let { intent.putExtra(EXTRA_STATE, it) }
                intent.data = Uri.parse("roadtrip://nav/checklist/${target.stateCode.orEmpty()}")
            }
            is NavTarget.LegSummaryScreen -> {
                intent.putExtra(EXTRA_KIND, KIND_LEG)
                intent.putExtra(EXTRA_DEST, target.destinationId)
                intent.data = Uri.parse("roadtrip://nav/leg/${target.destinationId}")
            }
        }
        return intent
    }

    fun fromIntent(intent: Intent?): NavTarget? {
        intent ?: return null
        return when (intent.getStringExtra(EXTRA_KIND)) {
            KIND_JOURNAL -> NavTarget.Journal
            KIND_BOARD -> intent.getStringExtra(EXTRA_GAME_ID)?.let { NavTarget.GameBoard(it) }
            KIND_REPLAY -> intent.getStringExtra(EXTRA_GAME_ID)?.let { NavTarget.GameReplay(it) }
            KIND_MAP -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
                val lon = intent.getDoubleExtra(EXTRA_LON, Double.NaN)
                if (lat.isNaN() || lon.isNaN()) null else NavTarget.MapPin(lat, lon)
            }
            KIND_CHECKLIST -> NavTarget.ChecklistScreen(intent.getStringExtra(EXTRA_STATE))
            KIND_LEG -> intent.getStringExtra(EXTRA_DEST)?.let { NavTarget.LegSummaryScreen(it) }
            else -> null
        }
    }
}
