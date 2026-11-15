package online.duolap.tetviet.game.dapchuot.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lớp hiệu ứng hạt vẽ đè lên màn chơi (không nhận chạm).
 * Chỉ chạy vòng vẽ khi còn hạt hoặc đang bật hoa đào bay, nên không tốn pin lúc đứng yên.
 */
class DapChuotEffectsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Kind { STAR, DUST, CONFETTI, COIN, PETAL, SPARK }

    private class Particle(
        val kind: Kind,
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val size: Float,
        val color: Int,
        val lifeMs: Float,
        var rotation: Float,
        val spin: Float,
        val gravity: Float,
        var ageMs: Float = 0f
    )

    private val particles = ArrayList<Particle>(256)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPath = Path()
    private val density = resources.displayMetrics.density
    private var lastFrameMs = 0L
    private var ambientPetals = false
    private var petalAccumulatorMs = 0f

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Sao vàng toé ra khi đập trúng chuột. */
    fun burstStars(x: Float, y: Float, golden: Boolean) {
        val colors = if (golden) GOLD_COLORS else STAR_COLORS
        repeat(if (golden) 16 else 10) {
            val angle = Random.nextFloat() * TWO_PI
            val speed = dp(Random.nextFloat() * 260f + 160f)
            add(Kind.STAR, x, y, cos(angle) * speed, sin(angle) * speed - dp(120f),
                dp(Random.nextFloat() * 6f + 6f), colors.random(), 650f, gravity = dp(900f))
        }
        if (golden) burstCoins(x, y, 6)
    }

    /** Bụi đất khi đập vào hang trống. */
    fun puffDust(x: Float, y: Float) {
        repeat(7) {
            val angle = (Random.nextFloat() * 0.9f + 0.05f) * -PI_F
            val speed = dp(Random.nextFloat() * 120f + 60f)
            add(Kind.DUST, x + dp(Random.nextFloat() * 24f - 12f), y + dp(14f), cos(angle) * speed, sin(angle) * speed,
                dp(Random.nextFloat() * 5f + 5f), DUST_COLORS.random(), 520f, gravity = dp(260f))
        }
    }

    /** Tia lửa đỏ cam khi đập trúng pháo. */
    fun burstSparks(x: Float, y: Float) {
        repeat(22) {
            val angle = Random.nextFloat() * TWO_PI
            val speed = dp(Random.nextFloat() * 420f + 200f)
            add(Kind.SPARK, x, y, cos(angle) * speed, sin(angle) * speed,
                dp(Random.nextFloat() * 3f + 3f), SPARK_COLORS.random(), 520f, gravity = dp(500f))
        }
    }

    fun burstCoins(x: Float, y: Float, count: Int) {
        repeat(count) {
            val angle = -PI_F / 2f + (Random.nextFloat() - 0.5f) * 1.6f
            val speed = dp(Random.nextFloat() * 220f + 260f)
            add(Kind.COIN, x, y, cos(angle) * speed, sin(angle) * speed,
                dp(Random.nextFloat() * 3f + 7f), Color.parseColor("#FFC107"), 1100f, gravity = dp(1100f))
        }
    }

    /** Mưa pháo giấy từ trên xuống, dùng khi phá kỷ lục. */
    fun rainConfetti(count: Int = 90) {
        if (width == 0) return
        repeat(count) {
            add(Kind.CONFETTI, Random.nextFloat() * width, -dp(Random.nextFloat() * 160f + 10f),
                dp(Random.nextFloat() * 80f - 40f), dp(Random.nextFloat() * 120f + 140f),
                dp(Random.nextFloat() * 4f + 5f), CONFETTI_COLORS.random(), 3200f, gravity = dp(60f))
        }
    }

    /** Cánh hoa đào bay lững lờ làm nền. */
    fun setAmbientPetals(enabled: Boolean) {
        ambientPetals = enabled
        if (enabled) kick()
    }

    fun clear() {
        particles.clear()
        invalidate()
    }

    private fun add(
        kind: Kind, x: Float, y: Float, vx: Float, vy: Float, size: Float, color: Int,
        lifeMs: Float, gravity: Float
    ) {
        if (particles.size >= MAX_PARTICLES) particles.removeAt(0)
        particles += Particle(kind, x, y, vx, vy, size, color, lifeMs,
            Random.nextFloat() * 360f, Random.nextFloat() * 540f - 270f, gravity)
        kick()
    }

    private fun kick() {
        if (lastFrameMs == 0L) {
            lastFrameMs = SystemClock.uptimeMillis()
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val dtMs = if (lastFrameMs == 0L) 16f else (now - lastFrameMs).coerceIn(0L, 48L).toFloat()
        val dt = dtMs / 1000f

        if (ambientPetals && width > 0) {
            petalAccumulatorMs += dtMs
            while (petalAccumulatorMs > PETAL_INTERVAL_MS) {
                petalAccumulatorMs -= PETAL_INTERVAL_MS
                add(Kind.PETAL, Random.nextFloat() * width, -dp(12f), dp(Random.nextFloat() * 40f + 10f),
                    dp(Random.nextFloat() * 30f + 40f), dp(Random.nextFloat() * 3f + 5f),
                    PETAL_COLORS.random(), height / dp(50f) * 1000f + 2000f, gravity = 0f)
            }
        }

        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.ageMs += dtMs
            if (p.ageMs >= p.lifeMs || p.y > height + dp(40f)) {
                it.remove()
                continue
            }
            p.vy += p.gravity * dt
            if (p.kind == Kind.PETAL || p.kind == Kind.CONFETTI) {
                p.vx += sin((p.ageMs + p.size * 37f) / 380f) * dp(30f) * dt
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rotation += p.spin * dt
            draw(canvas, p)
        }

        if (particles.isNotEmpty() || ambientPetals) {
            lastFrameMs = now
            postInvalidateOnAnimation()
        } else {
            lastFrameMs = 0L
        }
    }

    private fun draw(canvas: Canvas, p: Particle) {
        val life = p.ageMs / p.lifeMs
        val fade = when (p.kind) {
            Kind.CONFETTI, Kind.PETAL -> if (life > 0.85f) (1f - life) / 0.15f else 1f
            else -> 1f - life * life
        }
        paint.color = p.color
        paint.alpha = (Color.alpha(p.color) * fade).toInt().coerceIn(0, 255)
        paint.style = Paint.Style.FILL
        canvas.save()
        canvas.translate(p.x, p.y)
        canvas.rotate(p.rotation)
        when (p.kind) {
            Kind.STAR -> {
                buildStar(p.size)
                canvas.drawPath(starPath, paint)
            }
            Kind.DUST, Kind.SPARK -> canvas.drawCircle(0f, 0f, p.size * (if (p.kind == Kind.DUST) 0.6f + life else 1f - life * 0.5f), paint)
            Kind.CONFETTI -> canvas.drawRect(-p.size, -p.size * 0.45f, p.size, p.size * 0.45f, paint)
            Kind.PETAL -> canvas.drawOval(-p.size, -p.size * 0.55f, p.size, p.size * 0.55f, paint)
            Kind.COIN -> {
                // Đồng xu lật: co bề ngang theo góc quay
                val squeeze = kotlin.math.abs(cos(Math.toRadians(p.rotation.toDouble() * 2)).toFloat()).coerceAtLeast(0.2f)
                canvas.rotate(-p.rotation)
                canvas.drawOval(-p.size * squeeze, -p.size, p.size * squeeze, p.size, paint)
                paint.color = Color.parseColor("#FFF59D")
                paint.alpha = (255 * fade).toInt()
                canvas.drawOval(-p.size * squeeze * 0.5f, -p.size * 0.5f, p.size * squeeze * 0.5f, p.size * 0.5f, paint)
            }
        }
        canvas.restore()
    }

    private fun buildStar(r: Float) {
        starPath.reset()
        val inner = r * 0.45f
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else inner
            val a = -PI_F / 2f + i * PI_F / 5f
            val x = cos(a) * radius
            val y = sin(a) * radius
            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
    }

    private fun dp(v: Float) = v * density

    override fun onDetachedFromWindow() {
        particles.clear()
        ambientPetals = false
        lastFrameMs = 0L
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MAX_PARTICLES = 320
        private const val PETAL_INTERVAL_MS = 420f
        private const val PI_F = Math.PI.toFloat()
        private const val TWO_PI = PI_F * 2f

        private val STAR_COLORS = intArrayOf(0xFFFFEB3B.toInt(), 0xFFFFF59D.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFC107.toInt())
        private val GOLD_COLORS = intArrayOf(0xFFFFD54F.toInt(), 0xFFFFF176.toInt(), 0xFFFFA000.toInt(), 0xFFFFFFFF.toInt())
        private val DUST_COLORS = intArrayOf(0xCC8D6E63.toInt(), 0xCCA1887F.toInt(), 0xCC795548.toInt())
        private val SPARK_COLORS = intArrayOf(0xFFFF3D00.toInt(), 0xFFFF9100.toInt(), 0xFFFFEA00.toInt(), 0xFFFFFFFF.toInt())
        private val CONFETTI_COLORS = intArrayOf(
            0xFFE53935.toInt(), 0xFFFFC107.toInt(), 0xFF43A047.toInt(), 0xFF1E88E5.toInt(),
            0xFFFF4081.toInt(), 0xFFFFEB3B.toInt()
        )
        private val PETAL_COLORS = intArrayOf(0xDDF8BBD0.toInt(), 0xDDF48FB1.toInt(), 0xDDFFFFFF.toInt())
    }
}
