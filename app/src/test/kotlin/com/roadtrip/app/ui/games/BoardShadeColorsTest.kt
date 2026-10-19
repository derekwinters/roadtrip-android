package com.roadtrip.app.ui.games

import com.roadtrip.app.ui.theme.AppDarkColorScheme
import com.roadtrip.app.ui.theme.AppLightColorScheme
import com.roadtrip.app.ui.theme.DarkBoardColors
import com.roadtrip.app.ui.theme.LightBoardColors
import com.roadtrip.core.games.LastMoveShade
import com.roadtrip.core.games.StagedMoveShade
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

/**
 * covers: ANDGAME-026 — the staged-move fill is its own colour, distinct from every ANDGAME-024
 * last-move shade, in both the light and the dark theme, and it follows the effective theme.
 */
class BoardShadeColorsTest {

    @Test
    fun stagedShadeDiffersFromEveryLastMoveShadeInLightAndDark() {
        listOf(
            AppLightColorScheme to LightBoardColors,
            AppDarkColorScheme to DarkBoardColors,
        ).forEach { (scheme, board) ->
            val staged = assertNotNull(boardShadeColor(StagedMoveShade.STAGED_MOVE, scheme, board))
            LastMoveShade.entries.forEach { last ->
                assertNotEquals(boardShadeColor(last, scheme, board), staged, "staged vs $last")
            }
        }
    }

    @Test
    fun stagedShadeFollowsTheTheme() {
        assertNotEquals(
            boardShadeColor(StagedMoveShade.STAGED_MOVE, AppLightColorScheme, LightBoardColors),
            boardShadeColor(StagedMoveShade.STAGED_MOVE, AppDarkColorScheme, DarkBoardColors),
        )
    }

    @Test
    fun noShadeGivesNoColour() {
        assertNull(boardShadeColor(null, AppLightColorScheme, LightBoardColors))
    }
}
