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
 * Hiệu ứng ánh sáng cho đèn lồng: quầng sáng "thở" chậm và ánh nến lung linh, điều khiển bằng
 * MỘT TimeAnimator cho mọi đèn. Mỗi khung hình chỉ gán alpha/scale (thuộc tính RenderNode), không vẽ lại view.
 *
 * Độ sáng là tổng ba sóng sin chu kỳ lệch nhau (~1.9 s, 0.73 s, 0.31 s) nên nhấp nháy tự nhiên như
 * ngọn nến, không lặp đều đặn. [burst] làm đèn bừng sáng rồi dịu dần khi người dùng chạm.
 */
class LanternGlowAnimator {

    private class Lantern(val glow: View, val light: View, val phase: Double) {
        var burstStartMs = -1L
    }

    private val lanterns = ArrayList<Lantern>()
    private val epochMs = SystemClock.uptimeMillis()
    private val enabled: Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    private val ticker = TimeAnimator().apply {
        setTimeListener { _, _, _ -> onFrame(SystemClock.uptimeMillis()) }
    }

    fun add(glow: View, light: View, seed: Int) {
        if (lanterns.any { it.glow === glow }) return
        lanterns += Lantern(glow, light, phase = (seed * 1.618) % 1.0 * TWO_PI)
        if (!enabled) {
            glow.alpha = 0.85f
            light.alpha = 0.8f
        }
    }

    fun burst(glow: View) {
        lanterns.firstOrNull { it.glow === glow }?.burstStartMs = SystemClock.uptimeMillis()
    }

    fun start() {
        if (enabled && lanterns.isNotEmpty() && !ticker.isStarted) ticker.start()
    }

    fun stop() {
        ticker.cancel()
    }

    fun clear() {
        ticker.cancel()
        lanterns.clear()
    }

    private fun onFrame(now: Long) {
        val t = (now - epochMs).toDouble()
        for (l in lanterns) {
            val p = l.phase
            val flicker = 0.5 * sin(t * TWO_PI / 1900.0 + p) +
                0.3 * sin(t * TWO_PI / 730.0 + p * 2.3) +
                0.2 * sin(t * TWO_PI / 310.0 + p * 0.7) // [-1, 1]

            var burst = 0.0
            if (l.burstStartMs >= 0) {
                val dt = (now - l.burstStartMs).toDouble()
                if (dt < BURST_MS) {
                    // Bừng lên nhanh (~80 ms) rồi tắt dần
                    burst = (dt / 80.0).coerceAtMost(1.0) * exp(-dt / 380.0)
                } else {
                    l.burstStartMs = -1
                }
            }

            val glowScale = (1.0 + 0.05 * flicker + 0.35 * burst).toFloat()
            l.glow.scaleX = glowScale
            l.glow.scaleY = glowScale
            l.glow.alpha = (0.78 + 0.16 * flicker + 0.3 * burst).coerceIn(0.0, 1.0).toFloat()
            l.light.alpha = (0.72 + 0.25 * flicker + 0.5 * burst).coerceIn(0.0, 1.0).toFloat()
        }
    }

    private companion object {
        const val TWO_PI = 2 * PI
        const val BURST_MS = 1500.0
    }
}
