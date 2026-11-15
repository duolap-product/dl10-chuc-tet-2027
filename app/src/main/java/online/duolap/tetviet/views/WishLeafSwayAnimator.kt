package online.duolap.tetviet.views

import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.os.Build
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Điều khiển hiệu ứng đung đưa của tất cả lá điều ước bằng MỘT TimeAnimator.
 *
 * Mỗi khung hình chỉ gán `rotation` (thuộc tính RenderNode, không vẽ lại view) theo hàm sin của
 * thời gian thực, nên:
 * - Không có cú giật khi bắt đầu (không dùng startDelay, góc tính liên tục từ đồng hồ hệ thống).
 * - Danh sách lá thay đổi hay dừng/chạy lại khi rời màn hình vẫn giữ nguyên pha, không nhảy góc.
 * - Lá mới thêm vào hoặc vừa bay lên cây được "hoà" dần từ góc hiện tại vào nhịp đung đưa.
 */
class WishLeafSwayAnimator {

    private class Leaf(
        val view: View,
        val phase: Float,
        val periodMs: Float,
        val amplitude: Float
    ) {
        var suspended = false
        var blendStartMs = 0L
        var blendFrom = 0f
        var nudgeStartMs = -1L
    }

    private val leaves = LinkedHashMap<View, Leaf>()
    private val epochMs = SystemClock.uptimeMillis()
    private val enabled: Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    private val ticker = TimeAnimator().apply {
        setTimeListener { _, _, _ -> onFrame(SystemClock.uptimeMillis()) }
    }

    /** [seed] khác nhau cho mỗi lá để nhịp đung đưa không đồng loạt. */
    fun add(view: View, seed: Int) {
        if (leaves.containsKey(view)) return
        val s = (seed * 2654435761L).toUInt().toLong()
        val leaf = Leaf(
            view = view,
            phase = ((s % 1000) / 1000f) * TWO_PI_F,
            periodMs = 3000f + (s / 1000 % 900),
            amplitude = 2.6f + ((s / 7 % 10) / 10f) * 1.0f
        )
        leaf.blendFrom = view.rotation
        leaf.blendStartMs = SystemClock.uptimeMillis()
        leaves[view] = leaf
    }

    fun remove(view: View) {
        leaves.remove(view)
    }

    fun clear() {
        ticker.cancel()
        leaves.clear()
    }

    /** Tạm ngừng điều khiển một lá (ví dụ khi đang bay lên cây); khi bật lại sẽ hoà dần vào nhịp. */
    fun setSuspended(view: View, suspended: Boolean) {
        val leaf = leaves[view] ?: return
        if (leaf.suspended == suspended) return
        leaf.suspended = suspended
        if (!suspended) {
            leaf.blendFrom = view.rotation
            leaf.blendStartMs = SystemClock.uptimeMillis()
        }
    }

    /** Lắc mạnh rồi tắt dần, dùng khi người dùng chạm vào lá. */
    fun nudge(view: View) {
        leaves[view]?.nudgeStartMs = SystemClock.uptimeMillis()
    }

    fun start() {
        if (enabled && !ticker.isStarted) ticker.start()
    }

    fun stop() {
        ticker.cancel()
    }

    private fun onFrame(now: Long) {
        // Tính bằng Double từ một mốc riêng: uptime dạng Float mất độ chính xác (máy bật lâu
        // ngày bước thời gian bị làm tròn hàng chục ms, gây giật).
        val t = (now - epochMs).toDouble()
        // Cơn gió chung chậm (~9 s) làm biên độ của cả cây lúc mạnh lúc nhẹ
        val gust = 0.72 + 0.28 * sin(t * TWO_PI / GUST_PERIOD_MS)
        for (leaf in leaves.values) {
            if (leaf.suspended) continue
            val p = leaf.phase.toDouble()
            var angle = (leaf.amplitude * gust * sin(t * TWO_PI / leaf.periodMs + p) +
                leaf.amplitude * 0.2 * sin(t * TWO_PI / (leaf.periodMs * 0.43) + p * 1.7)).toFloat()

            if (leaf.nudgeStartMs >= 0) {
                val dt = (now - leaf.nudgeStartMs).toFloat()
                if (dt < NUDGE_DURATION_MS) {
                    angle += NUDGE_AMPLITUDE * exp(-dt / 420f) * sin(dt * TWO_PI_F / 560f)
                } else {
                    leaf.nudgeStartMs = -1
                }
            }

            val blendDt = (now - leaf.blendStartMs).toFloat()
            if (blendDt < BLEND_MS) {
                val x = blendDt / BLEND_MS
                val w = x * x * (3f - 2f * x) // smoothstep
                angle = leaf.blendFrom + (angle - leaf.blendFrom) * w
            }
            leaf.view.rotation = angle
        }
    }

    private companion object {
        const val TWO_PI = 2 * PI
        const val TWO_PI_F = TWO_PI.toFloat()
        const val GUST_PERIOD_MS = 9000.0
        const val BLEND_MS = 900f
        const val NUDGE_DURATION_MS = 1800f
        const val NUDGE_AMPLITUDE = 9f
    }
}
