package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty
import io.github.ffeltrinelli.pool.ui.*
import org.junit.Assert.*
import org.junit.Test

class DifficultySliderTest {
    @Test fun `compact layout retains columns order and disjoint touch targets at portrait sizes`() {
        for ((width, height) in listOf(1080 to 2400, 420 to 933, 540 to 1200, 360 to 640)) {
            val layout = HudLayout(width, height)
            assertEquals(layout.top.x, layout.aidedAim.x, 0f)
            assertEquals(layout.top.width, layout.aidedAim.width, 0f)
            assertEquals(146f, layout.restart.width, 0f)
            assertEquals(146f, layout.exit.width, 0f)
            assertEquals(layout.solo.x, layout.restart.x, 0f)
            assertEquals(layout.computer.x, layout.exit.x, 0f)
            assertEquals(layout.restart.y, layout.exit.y, 0f)
            assertTrue(layout.aidedAim.y > layout.difficulty.y && layout.difficulty.y > layout.restart.y)
            val targets = listOf(layout.solo, layout.computer, layout.top, layout.player, layout.aidedAim,
                layout.restart, layout.exit, layout.close) + layout.difficultyStops
            for ((index, target) in targets.withIndex()) {
                assertTrue(target.width >= 48f && target.height >= 48f)
                assertTrue(layout.panel.contains(target))
                assertTrue(targets.drop(index + 1).none(target::overlaps))
            }
            for ((index, action) in listOf(HudAction.EASY, HudAction.MEDIUM, HudAction.HARD).withIndex()) {
                val stop = layout.difficultyStops[index]
                val x = ((stop.x + stop.width / 2) * width / 420).toInt()
                val y = ((layout.uiHeight - stop.y - stop.height / 2) * height / layout.uiHeight).toInt()
                assertEquals(action, layout.hit(x, y, true, false))
            }
        }
    }

    @Test fun `slider captures primary down snaps at midpoints clamps and releases on cancellation`() {
        val layout = HudLayout(420, 933)
        val input = DifficultySliderInput()
        val y = (layout.uiHeight - layout.difficulty.y - 24).toInt()
        input.begin(layout, 110, y, 1)
        assertNull(input.selection(layout, 110, 1))
        input.begin(layout, 110, 0, 0)
        assertNull(input.selection(layout, 110, 0))
        assertNull(input.selection(layout, 310, 0))
        input.begin(layout, 110, y, 0)
        assertEquals(OpponentDifficulty.EASY, input.selection(layout, 110, 0))
        assertNull(input.selection(layout, 310, 1))
        assertEquals(OpponentDifficulty.EASY, input.selection(layout, 159, 0))
        assertEquals(OpponentDifficulty.MEDIUM, input.selection(layout, 160, 0))
        assertEquals(OpponentDifficulty.MEDIUM, input.selection(layout, 259, 0))
        assertEquals(OpponentDifficulty.HARD, input.selection(layout, 260, 0))
        assertEquals(OpponentDifficulty.HARD, input.selection(layout, 999, 0))
        assertEquals(OpponentDifficulty.EASY, input.selection(layout, -999, 0))
        input.end(1)
        assertEquals(OpponentDifficulty.MEDIUM, input.selection(layout, 210, 0))
        input.end(0)
        assertNull(input.selection(layout, 310, 0))
        input.begin(layout, 310, y, 0)
        assertEquals(OpponentDifficulty.HARD, input.selection(layout, 310, 0))
        input.cancel()
        assertNull(input.selection(layout, 110, 0))
    }
}
