package online.duolap.tetviet.game.dephi.engine

import kotlin.random.Random

/**
 * Luật chơi "Cùng Dê Phi Đón Tết": dê tự chạy, chạm màn hình để nhảy qua chướng ngại vật và nhặt lộc.
 *
 * Kotlin thuần, không phụ thuộc Android (có unit test). Toạ độ theo dp: trục x từ mép trái màn hình,
 * trục y tính từ mặt đất lên. Dê đứng yên ở [GOAT_X], thế giới trôi sang trái với tốc độ [DePhiState.speed].
 * [step] trả về danh sách sự kiện để giao diện phát hiệu ứng.
 *
 * Quy tắc MVP: chỉ nhảy khi đang chạm đất (chạm lúc đang bay không có tác dụng, không có nhảy đôi);
 * chạm chướng ngại vật là kết thúc lượt. Khoảng cách giữa hai chướng ngại vật luôn đủ để nhảy qua
 * và kịp chạm đất trước cái kế tiếp ([minObstacleGap]).
 */
object DePhiEngine {

    // Dê
    const val GOAT_X = 64f
    const val GOAT_W = 54f
    const val GOAT_H = 50f

    // Nhảy: cao [JUMP_HEIGHT] dp, ở trên không [AIRTIME_SEC] giây
    const val JUMP_HEIGHT = 120f
    const val AIRTIME_SEC = 0.72f
    const val JUMP_VELOCITY = 4 * JUMP_HEIGHT / AIRTIME_SEC   // v0 = 2H / (T/2)
    const val GRAVITY = JUMP_VELOCITY / (AIRTIME_SEC / 2)     // g = v0 / (T/2)

    // Tốc độ chạy (dp/s)
    const val START_SPEED = 260f
    const val MAX_SPEED = 600f
    const val SPEED_GAIN_PER_SEC = 6f

    /** 1 mét quãng đường = bấy nhiêu dp. */
    const val DP_PER_METER = 40f

    /** Thu nhỏ khung va chạm để chạm sượt không tính là đâm (cảm giác công bằng hơn). */
    const val HITBOX_INSET = 7f

    private const val MAX_SUBSTEP_MS = 16L
    private const val MAX_FRAME_MS = 50L

    fun newGame(seed: Long = System.nanoTime(), spawnX: Float = 420f): DePhiState =
        DePhiState(seed = seed, spawnX = spawnX).also { it.nextSpawnIn = 260f }

    /** Chạm màn hình. Trả về true nếu dê nhảy (đang ở mặt đất và ván chưa kết thúc). */
    fun jump(state: DePhiState): Boolean {
        if (state.isOver || !state.onGround) return false
        state.goatVy = JUMP_VELOCITY
        state.onGround = false
        state.jumps++
        return true
    }

    /**
     * Hồi sinh sau va chạm (thưởng quảng cáo): giữ nguyên quãng đường, điểm và xu, dọn mọi chướng ngại vật
     * đang trên màn hình và chừa một khoảng trống an toàn trước cái kế tiếp. Trả về false nếu ván chưa kết thúc.
     */
    fun revive(state: DePhiState): Boolean {
        if (!state.isOver) return false
        state.isOver = false
        state.goatY = 0f
        state.goatVy = 0f
        state.onGround = true
        state.obstacles.clear()
        state.nextSpawnIn = minObstacleGap(state.speed) + state.spawnX * 0.5f
        state.revives++
        return true
    }

    /** Khoảng trống tối thiểu (dp) giữa hai chướng ngại vật ở tốc độ [speed]: một cú nhảy + thời gian kịp phản xạ. */
    fun minObstacleGap(speed: Float): Float = speed * AIRTIME_SEC * 1.15f + speed * 0.32f + 60f

    /** Tiến thời gian [dtMs] mili giây (chia nhỏ để va chạm chính xác ở tốc độ cao). */
    fun step(state: DePhiState, dtMs: Long): List<DePhiEvent> {
        if (state.isOver || dtMs <= 0) return emptyList()
        val events = ArrayList<DePhiEvent>(2)
        var remaining = dtMs.coerceAtMost(MAX_FRAME_MS)
        while (remaining > 0 && !state.isOver) {
            val sub = remaining.coerceAtMost(MAX_SUBSTEP_MS)
            subStep(state, sub / 1000f, events)
            remaining -= sub
        }
        return events
    }

    private fun subStep(s: DePhiState, dt: Float, events: MutableList<DePhiEvent>) {
        s.elapsedSec += dt
        s.speed = (START_SPEED + SPEED_GAIN_PER_SEC * s.elapsedSec).coerceAtMost(MAX_SPEED)
        val dx = s.speed * dt

        // Quãng đường và mốc mỗi 100 m
        val oldMeters = s.meters
        s.distanceDp += dx
        if (s.meters / 100 > oldMeters / 100) events += DePhiEvent.Milestone(s.meters / 100 * 100)

        // Dê nhảy / rơi
        if (!s.onGround) {
            s.goatVy -= GRAVITY * dt
            s.goatY += s.goatVy * dt
            if (s.goatY <= 0f) {
                s.goatY = 0f
                s.goatVy = 0f
                s.onGround = true
                events += DePhiEvent.Landed
            }
        }

        // Thế giới trôi sang trái
        s.obstacles.forEach { it.x -= dx }
        s.items.forEach { it.x -= dx }
        s.obstacles.removeAll { it.x + it.type.width < -20f }
        s.items.removeAll { it.x + it.type.size < -20f || it.collected }

        // Sinh chướng ngại vật và vật phẩm mới
        s.nextSpawnIn -= dx
        if (s.nextSpawnIn <= 0f) spawn(s)

        // Va chạm (khung đã thu nhỏ)
        val gl = GOAT_X + HITBOX_INSET
        val gr = GOAT_X + GOAT_W - HITBOX_INSET
        val gb = s.goatY + HITBOX_INSET * 0.5f
        val gt = s.goatY + GOAT_H - HITBOX_INSET

        for (o in s.obstacles) {
            val ol = o.x + HITBOX_INSET
            val or = o.x + o.type.width - HITBOX_INSET
            val ot = o.type.height - HITBOX_INSET * 0.5f
            if (gr > ol && gl < or && gb < ot) {
                s.isOver = true
                events += DePhiEvent.Crashed(o.type)
                return
            }
        }
        for (item in s.items) {
            if (item.collected) continue
            val r = item.type.size / 2
            val cx = item.x + r
            val cy = item.y + r
            if (cx + r > gl && cx - r < gr && cy + r > gb && cy - r < gt + HITBOX_INSET) {
                item.collected = true
                s.score += item.type.points
                s.xu += item.type.xu
                s.collectedCounts[item.type] = (s.collectedCounts[item.type] ?: 0) + 1
                events += DePhiEvent.Collected(item.type, cx, cy)
            }
        }
    }

    /**
     * Đặt một chướng ngại vật ở mép phải màn hình, kèm vật phẩm:
     * hàng vàng dưới đất trước nó, vòng cung vàng/lì xì phía trên (thưởng cho cú nhảy).
     */
    private fun spawn(s: DePhiState) {
        val rng = s.rng
        val x = s.spawnX + 20f
        val type = ObstacleType.entries[rng.nextInt(ObstacleType.entries.size)]
        s.obstacles += Obstacle(type, x)
        s.spawnCount++

        val minGap = minObstacleGap(s.speed)
        val gap = minGap * (1f + rng.nextFloat() * 0.85f)

        // Vật phẩm bay vòng cung trên chướng ngại vật: nhảy đúng lúc thì nhặt được
        when {
            s.spawnCount % 7 == 0 -> s.items += Item(ItemType.LIXI, x + type.width / 2 - ItemType.LIXI.size / 2, JUMP_HEIGHT * 0.78f)
            s.spawnCount % 5 == 0 -> s.items += Item(ItemType.BANH_CHUNG, x + type.width / 2 - ItemType.BANH_CHUNG.size / 2, JUMP_HEIGHT * 0.74f)
            rng.nextFloat() < 0.6f -> arcOfGold(s, x + type.width / 2)
        }
        // Hàng vàng dưới đất trong khoảng trống phía sau
        if (gap > minGap * 1.25f && rng.nextFloat() < 0.65f) {
            val count = 3 + rng.nextInt(3)
            val startX = x + type.width + minGap * 0.55f
            repeat(count) { i -> s.items += Item(ItemType.GOLD, startX + i * 34f, 6f) }
        }
        s.nextSpawnIn = gap + type.width
    }

    private fun arcOfGold(s: DePhiState, centerX: Float) {
        val r = ItemType.GOLD.size / 2
        for (i in -2..2) {
            val h = JUMP_HEIGHT * (0.72f - 0.07f * i * i)
            s.items += Item(ItemType.GOLD, centerX + i * 30f - r, h)
        }
    }
}

enum class ObstacleType(val width: Float, val height: Float) {
    /** Dây pháo đỏ dựng đứng. */
    PHAO(30f, 62f),
    /** Chậu hoa mai. */
    CHAU_MAI(50f, 52f),
    /** Hộp quà Tết. */
    HOP_QUA(44f, 40f)
}

enum class ItemType(val size: Float, val points: Int, val xu: Int) {
    /** Thỏi vàng. */
    GOLD(26f, 10, 1),
    /** Bánh chưng (vật phẩm Tết). */
    BANH_CHUNG(30f, 30, 2),
    /** Bao lì xì. */
    LIXI(30f, 50, 5)
}

class Obstacle(val type: ObstacleType, var x: Float)

/** [y] là đáy vật phẩm tính từ mặt đất. */
class Item(val type: ItemType, var x: Float, val y: Float) {
    var collected = false
}

class DePhiState(seed: Long, var spawnX: Float) {
    internal val rng = Random(seed)

    var goatY = 0f
    var goatVy = 0f
    var onGround = true
    var speed = DePhiEngine.START_SPEED
    var distanceDp = 0f
    var elapsedSec = 0f
    var isOver = false
    var jumps = 0
    /** Số lần đã hồi sinh trong ván. */
    var revives = 0

    /** Điểm từ vật phẩm (chưa tính quãng đường). */
    var score = 0
    /** Xu nhặt được trong ván, cộng vào ví khi kết thúc. */
    var xu = 0
    val collectedCounts = LinkedHashMap<ItemType, Int>()

    val obstacles = ArrayList<Obstacle>()
    val items = ArrayList<Item>()

    internal var nextSpawnIn = 0f
    internal var spawnCount = 0

    val meters: Int get() = (distanceDp / DePhiEngine.DP_PER_METER).toInt()

    /** Tổng điểm: mỗi mét 1 điểm + điểm vật phẩm. */
    val totalScore: Int get() = meters + score
}

sealed class DePhiEvent {
    data object Landed : DePhiEvent()
    data class Collected(val type: ItemType, val x: Float, val y: Float) : DePhiEvent()
    data class Crashed(val obstacle: ObstacleType) : DePhiEvent()
    data class Milestone(val meters: Int) : DePhiEvent()
}
