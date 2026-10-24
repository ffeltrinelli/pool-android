package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.rules.*
import io.github.ffeltrinelli.pool.physics.*
import org.junit.Assert.*
import org.junit.Test

class ComputerSessionTest {
    private fun facts(first: Set<Int> = setOf(1), rails: Set<Int> = emptySet(), pots: Set<Int> = emptySet()) = ShotFacts().apply {
        accept(PhysicsEvent.Contacts(first, rails))
        pots.forEach { accept(PhysicsEvent.Pocketed(it)) }
    }
    @Test fun `first contact and cushion requirements are separate fouls`() {
        val context = RulesContext(group = BallGroup.SOLIDS)
        assertEquals(FoulReason.NO_CONTACT, EightBallRules.resolve(context, facts(emptySet())).foul)
        assertEquals(FoulReason.WRONG_FIRST, EightBallRules.resolve(context, facts(setOf(9))).foul)
        assertEquals(FoulReason.NO_RAIL, EightBallRules.resolve(context, facts()).foul)
        assertNull(EightBallRules.resolve(context, facts(rails = setOf(0))).foul)
        assertNull(EightBallRules.resolve(context, facts(pots = setOf(9))).foul)
        assertNull(EightBallRules.resolve(context, facts(setOf(1, 9), setOf(0))).foul)
    }
    @Test fun `cushion before contact does not satisfy rule`() {
        val f = ShotFacts()
        f.accept(PhysicsEvent.Contacts(emptySet(), setOf(0)))
        f.accept(PhysicsEvent.Contacts(setOf(1), emptySet()))
        assertEquals(FoulReason.NO_RAIL, EightBallRules.resolve(RulesContext(), f).foul)
    }
    @Test fun `head string must cross before restricted contact behind line`() {
        val context = RulesContext(restricted = true)
        val f = ShotFacts().apply { accept(PhysicsEvent.Contacts(setOf(1), setOf(1), targetsBehindHeadString = true)) }
        assertEquals(FoulReason.HEAD_STRING, EightBallRules.resolve(context, f).foul)
        val crossed = ShotFacts().apply {
            accept(PhysicsEvent.Contacts(emptySet(), emptySet(), crossedHeadString = true))
            accept(PhysicsEvent.Contacts(setOf(1), setOf(1), targetsBehindHeadString = true))
        }
        assertNull(EightBallRules.resolve(context, crossed).foul)
    }
    @Test fun `off table eight loses while other off table balls foul`() {
        for (id in listOf(0, 1, 8)) {
            val f = facts(rails = setOf(1)).apply { accept(PhysicsEvent.OffTable(id)) }
            val result = EightBallRules.resolve(RulesContext(), f)
            assertEquals(FoulReason.OFF_TABLE, result.foul)
            assertEquals(id == 8, result.loss != null)
        }
    }
    @Test fun `break needs four distinct object balls not cue ball`() {
        assertTrue(EightBallRules.resolve(RulesContext(breaking = true), facts(rails = setOf(0, 1, 2, 3))).rerack)
        assertFalse(EightBallRules.resolve(RulesContext(breaking = true), facts(rails = setOf(1, 2, 3, 4))).rerack)
    }
    @Test fun `placement rejects overlaps rails pockets and restricted line`() {
        assertNull(BallPlacement.invalidReason(TableGeometry.CUE_START, emptyList(), PlacementRegion.BEHIND_HEAD_STRING))
        assertNotNull(BallPlacement.invalidReason(Point(0f, TableGeometry.HEAD_STRING), emptyList(), PlacementRegion.BEHIND_HEAD_STRING))
        assertNotNull(BallPlacement.invalidReason(Point(0f, 0f), listOf(Point(0.03f, 0f)), PlacementRegion.ANYWHERE))
        assertNotNull(BallPlacement.invalidReason(Point(0.55f, 0f), emptyList(), PlacementRegion.ANYWHERE))
        assertNotNull(BallPlacement.invalidReason(PocketGeometry.pockets.first().center, emptyList(), PlacementRegion.ANYWHERE))
        assertNotNull(BallPlacement.invalidReason(Point(Float.NaN, 0f), emptyList(), PlacementRegion.ANYWHERE))
        val positions = BallPlacement.candidates(listOf(TableGeometry.CUE_START), PlacementRegion.BEHIND_HEAD_STRING)
        assertTrue(positions.isNotEmpty())
        assertEquals(positions, BallPlacement.candidates(listOf(TableGeometry.CUE_START), PlacementRegion.BEHIND_HEAD_STRING))
        assertTrue(positions.all { it.z > TableGeometry.HEAD_STRING })
    }
    @Test fun `restricted placement spots nearest eligible ball with deterministic ties only when all are behind`() {
        val table = TableSnapshot(listOf(BallSnapshot(1, Point(0.1f, 0.7f)), BallSnapshot(2, Point(-0.1f, 0.7f)),
            BallSnapshot(9, Point(0f, 0f))))
        assertEquals(1, BallPlacement.restrictedTargetToSpot(table, setOf(1, 2)))
        assertNull(BallPlacement.restrictedTargetToSpot(table, setOf(1, 9)))
        assertNull(BallPlacement.restrictedTargetToSpot(table.copy(balls = listOf(BallSnapshot(1, Point(0f, TableGeometry.HEAD_STRING)))), setOf(1)))
    }
    @Test fun `rack constraints hold across seeds and positions match official spots`() {
        for (seed in 0L..50L) {
            val rack = TableGeometry.rack(seed)
            assertEquals((0..15).toSet(), rack.map { it.first }.toSet())
            assertEquals(TableGeometry.FOOT_SPOT, rack[1].second.z, 0f)
            assertEquals(8, rack[5].first)
            assertTrue((rack[11].first in 1..7) != (rack[15].first in 1..7))
            assertEquals(rack, TableGeometry.rack(seed))
        }
        assertNotEquals(TableGeometry.rack(1), TableGeometry.rack(2))
    }
}
