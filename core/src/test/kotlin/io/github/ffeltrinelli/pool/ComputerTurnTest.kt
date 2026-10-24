package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import org.junit.Assert.*
import org.junit.Test

class ComputerTurnTest {
    private val shot = ShotCommand(Point(1f, 0f), 0.3f)
    private class Service : ShotPlanningService {
        var starts = 0; var cancellations = 0; var closed = false
        var result: PlanningResult? = null
        override fun start(table: TableSnapshot, seed: Long) { starts++ }
        override fun poll() = result.also { result = null }
        override fun cancel() { cancellations++; result = null }
        override fun close() { closed = true }
    }
    private fun computerSession() = GameSession().apply {
        beginShot(); accept(PhysicsEvent.Settled)
    }
    private val snapshot = TableSnapshot(listOf(BallSnapshot(0, Point(0f, 0.5f)), BallSnapshot(1, Point(0f, 0f))))

    @Test fun `stroke stages and contact are independent of frame partition and impulse`() {
        for (impulse in listOf(0.1f, 1.15f)) {
            val s = ComputerStroke(shot.copy(impulse = impulse))
            assertEquals(0.05f, s.pose.tipGap, 0.00001f)
            assertNull(s.advance(0.35f))
            assertEquals(0.05f, s.pose.tipGap, 0.00001f)
            assertNull(s.advance(0.25f))
            assertEquals(0.12f, s.pose.tipGap, 0.00001f)
            assertNull(s.advance(0.1f))
            assertEquals(0.06f, s.pose.tipGap, 0.00001f)
            assertEquals(impulse, s.advance(0.1f)!!.impulse, 0f)
            assertEquals(0f, s.pose.tipGap, 0.00001f)
            assertNull(s.advance(10f))
        }
        val fine = ComputerStroke(shot)
        val deliveries = (1..100).mapNotNull { fine.advance(0.01f) }
        assertEquals(listOf(shot), deliveries)
    }
    @Test fun `thinking lasts one active second then exactly one contact shot is delivered`() {
        val service = Service(); val turn = ComputerTurn(service); val session = computerSession()
        assertNull(turn.advance(session, { snapshot }, 0.1f))
        assertEquals(1, service.starts)
        assertNull(turn.pose)
        service.result = PlanningResult(shot, 6, 20, false)
        assertNull(turn.advance(session, { snapshot }, 0.8f))
        assertNull(turn.pose)
        assertNull(turn.advance(session, { snapshot }, 0.1f))
        assertEquals(ExecutionAccuracy.apply(shot, OpponentDifficulty.MEDIUM, 1).direction, turn.pose!!.direction)
        assertEquals(ExecutionAccuracy.apply(shot, OpponentDifficulty.MEDIUM, 1), turn.advance(session, { snapshot }, 0.8f))
        session.beginShot()
        repeat(10) { assertNull(turn.advance(session, { snapshot }, 0.1f)) }
        assertEquals(1, service.starts)
    }
    @Test fun `pause before contact resets the stroke but retains the selected shot`() {
        val service = Service(); val turn = ComputerTurn(service); val session = computerSession()
        service.result = PlanningResult(shot, 6, 10, false)
        turn.advance(session, { snapshot }, 1f)
        turn.advance(session, { snapshot }, 0.7f)
        turn.suspend()
        assertNull(turn.pose)
        turn.advance(session, { snapshot }, 1f)
        assertEquals(0.05f, turn.pose!!.tipGap, 0.00001f)
        assertEquals(1, service.starts)
        assertEquals(ExecutionAccuracy.apply(shot, OpponentDifficulty.MEDIUM, 1), turn.advance(session, { snapshot }, 0.8f))
    }
    @Test fun `difficulty changes reuse raw selection without compounding and cue matches contact`() {
        val service = Service(); val turn = ComputerTurn(service); val session = computerSession()
        service.result = PlanningResult(shot, 6, 10, false)
        turn.advance(session, { snapshot }, 1f, OpponentDifficulty.EASY)
        assertEquals(ExecutionAccuracy.apply(shot, OpponentDifficulty.EASY, 1).direction, turn.pose!!.direction)
        turn.suspend()
        turn.advance(session, { snapshot }, 1f, OpponentDifficulty.HARD)
        assertEquals(shot.direction, turn.pose!!.direction)
        turn.suspend()
        turn.advance(session, { snapshot }, 1f, OpponentDifficulty.MEDIUM)
        val expected = ExecutionAccuracy.apply(shot, OpponentDifficulty.MEDIUM, 1)
        assertEquals(expected.direction, turn.pose!!.direction)
        repeat(3) {
            turn.suspend()
            turn.advance(session, { snapshot }, 1f, OpponentDifficulty.MEDIUM)
            assertEquals(expected.direction, turn.pose!!.direction)
        }
        assertEquals(expected, turn.advance(session, { snapshot }, 0.8f, OpponentDifficulty.MEDIUM))
        session.beginShot()
        assertNull(turn.advance(session, { snapshot }, 1f, OpponentDifficulty.HARD))
        assertEquals(1, service.starts)
    }

    @Test fun `pending work is cancelled and new result uses current difficulty including fallback`() {
        val service = Service(); val turn = ComputerTurn(service); val session = computerSession()
        turn.advance(session, { snapshot }, 0.3f, OpponentDifficulty.EASY)
        turn.suspend()
        assertEquals(1, service.cancellations)
        turn.advance(session, { snapshot }, 0.2f, OpponentDifficulty.HARD)
        assertEquals(2, service.starts)
        service.result = PlanningResult(shot, 0, 0, true)
        turn.advance(session, { snapshot }, 0.8f, OpponentDifficulty.MEDIUM)
        val expected = ExecutionAccuracy.apply(shot, OpponentDifficulty.MEDIUM, 2)
        assertEquals(expected.direction, turn.pose!!.direction)
        assertEquals(expected, turn.advance(session, { snapshot }, 0.8f, OpponentDifficulty.MEDIUM))
    }

    @Test fun `foul delay blocks either player and reset discards selected work`() {
        val service = Service(); val turn = ComputerTurn(service)
        for (player in Player.entries) {
            turn.foul(player)
            assertTrue(turn.blocksShot)
            assertEquals(player, turn.foulPlayer)
            assertNull(turn.advance(computerSession(), { snapshot }, 2.9f))
            assertEquals(0, service.starts)
            assertNull(turn.advance(computerSession(), { snapshot }, 0.2f))
            assertFalse(turn.blocksShot)
            assertNull(turn.foulPlayer)
        }
        turn.advance(computerSession(), { snapshot }, 0.1f)
        turn.reset()
        assertNull(turn.pose)
        assertFalse(turn.thinking)
        turn.close()
        assertTrue(service.closed)
    }
    @Test fun `human and moving turns never request computer work`() {
        val service = Service(); val turn = ComputerTurn(service)
        val human = GameSession()
        turn.advance(human, { error("No snapshot needed") }, 2f)
        human.beginShot()
        turn.advance(human, { error("No snapshot needed") }, 2f)
        turn.advance(GameSession(), { error("No snapshot needed") }, 2f)
        assertEquals(0, service.starts)
    }
}
