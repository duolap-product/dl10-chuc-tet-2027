package online.duolap.tetviet.game.dephi

import online.duolap.tetviet.game.dephi.engine.DePhiEngine
import online.duolap.tetviet.game.dephi.engine.DePhiEvent
import online.duolap.tetviet.game.dephi.engine.DePhiState
import online.duolap.tetviet.game.dephi.engine.Item
import online.duolap.tetviet.game.dephi.engine.ItemType
import online.duolap.tetviet.game.dephi.engine.Obstacle
import online.duolap.tetviet.game.dephi.engine.ObstacleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DePhiEngineTest {

    private fun run(state: DePhiState, ms: Long, onFrame: (DePhiState) -> Unit = {}): List<DePhiEvent> {
        val events = ArrayList<DePhiEvent>()
        var t = 0L
        while (t < ms && !state.isOver) {
            onFrame(state)
            events += DePhiEngine.step(state, 16)
            t += 16
        }
        return events
    }

    /** Cùng quy tắc người chơi: nhảy khi chướng ngại vật gần nhất còn cách khoảng 0,12 giây chạy. */
    private fun autopilot(s: DePhiState) {
        val goatRight = DePhiEngine.GOAT_X + DePhiEngine.GOAT_W - DePhiEngine.HITBOX_INSET
        val next = s.obstacles
            .filter { it.x + it.type.width - DePhiEngine.HITBOX_INSET > DePhiEngine.GOAT_X + DePhiEngine.HITBOX_INSET }
            .minByOrNull { it.x } ?: return
        val dist = next.x + DePhiEngine.HITBOX_INSET - goatRight
        if (dist <= s.speed * 0.12f) DePhiEngine.jump(s)
    }

    @Test
    fun jumpOnlyFromGround() {
        val s = DePhiEngine.newGame(seed = 1)
        assertTrue(DePhiEngine.jump(s))
        DePhiEngine.step(s, 50)
        assertFalse("chạm lúc đang bay không có tác dụng", DePhiEngine.jump(s))
        assertEquals(1, s.jumps)
    }

    @Test
    fun jumpReachesPlannedHeightAndLands() {
        val s = DePhiEngine.newGame(seed = 2, spawnX = 100_000f) // không có chướng ngại vật trong tầm
        DePhiEngine.jump(s)
        var peak = 0f
        var landedAfterMs = -1L
        var t = 0L
        while (t < 2000) {
            val ev = DePhiEngine.step(s, 16)
            t += 16
            peak = maxOf(peak, s.goatY)
            if (DePhiEvent.Landed in ev) { landedAfterMs = t; break }
        }
        assertEquals(DePhiEngine.JUMP_HEIGHT, peak, 6f)
        assertEquals(720f, landedAfterMs.toFloat(), 40f)
        assertTrue(s.onGround)
    }

    @Test
    fun noInputMeansKeepRunningUntilFirstObstacle() {
        val s = DePhiEngine.newGame(seed = 3)
        val events = run(s, 60_000)
        assertTrue(s.isOver)
        assertTrue(events.any { it is DePhiEvent.Crashed })
        assertTrue(s.meters > 0)
    }

    @Test
    fun crashEndsTheRunAndFreezesState() {
        val s = DePhiEngine.newGame(seed = 4, spawnX = 100_000f)
        s.obstacles += Obstacle(ObstacleType.PHAO, DePhiEngine.GOAT_X + 20f)
        val ev = DePhiEngine.step(s, 16)
        assertTrue(s.isOver)
        assertTrue(ev.single() is DePhiEvent.Crashed)
        val meters = s.meters
        assertTrue(DePhiEngine.step(s, 1000).isEmpty())
        assertEquals(meters, s.meters)
        assertFalse(DePhiEngine.jump(s))
    }

    @Test
    fun collectingItemsAddsScoreAndXu() {
        val s = DePhiEngine.newGame(seed = 5, spawnX = 100_000f)
        s.items += Item(ItemType.GOLD, DePhiEngine.GOAT_X + 10f, 6f)
        s.items += Item(ItemType.LIXI, DePhiEngine.GOAT_X + 12f, 8f)
        val ev = DePhiEngine.step(s, 16)
        assertEquals(2, ev.count { it is DePhiEvent.Collected })
        assertEquals(ItemType.GOLD.points + ItemType.LIXI.points, s.score)
        assertEquals(ItemType.GOLD.xu + ItemType.LIXI.xu, s.xu)
        assertEquals(1, s.collectedCounts[ItemType.LIXI])
    }

    @Test
    fun speedRisesAndIsCapped() {
        val s = DePhiEngine.newGame(seed = 6, spawnX = 100_000f)
        run(s, 10_000)
        assertTrue(s.speed > DePhiEngine.START_SPEED)
        run(s, 120_000)
        assertEquals(DePhiEngine.MAX_SPEED, s.speed, 0.01f)
    }

    @Test
    fun obstaclesAreAlwaysClearableWithOneTapPerObstacle() {
        // 30 màn ngẫu nhiên, mỗi màn chạy 3 phút (qua cả tốc độ tối đa): người chơi tự động không được đâm
        for (seed in 1L..30L) {
            val s = DePhiEngine.newGame(seed = seed, spawnX = 420f)
            run(s, 180_000) { autopilot(it) }
            assertFalse("seed $seed đâm ở ${s.meters} m", s.isOver)
            assertTrue(s.jumps > 50)
        }
    }

    @Test
    fun reviveKeepsProgressAndClearsTheWay() {
        val s = DePhiEngine.newGame(seed = 8)
        run(s, 60_000) // không nhảy: đâm vào chướng ngại vật đầu tiên
        assertTrue(s.isOver)
        val meters = s.meters
        val xu = s.xu
        assertFalse(DePhiEngine.revive(DePhiEngine.newGame(seed = 9))) // ván chưa kết thúc thì không hồi sinh

        assertTrue(DePhiEngine.revive(s))
        assertFalse(s.isOver)
        assertTrue(s.onGround)
        assertTrue(s.obstacles.isEmpty())
        assertEquals(meters, s.meters)
        assertEquals(xu, s.xu)
        assertEquals(1, s.revives)

        // Có khoảng trống an toàn: chạy tiếp 1 giây không nhảy vẫn chưa đâm
        run(s, 1_000)
        assertFalse(s.isOver)
        // Và chơi tiếp bình thường
        run(s, 30_000) { autopilot(it) }
        assertFalse(s.isOver)
        assertTrue(s.meters > meters)
    }

    @Test
    fun sameSeedGivesSameRun() {
        val a = DePhiEngine.newGame(seed = 42)
        val b = DePhiEngine.newGame(seed = 42)
        run(a, 20_000) { autopilot(it) }
        run(b, 20_000) { autopilot(it) }
        assertEquals(a.meters, b.meters)
        assertEquals(a.score, b.score)
        assertEquals(a.jumps, b.jumps)
    }
}
