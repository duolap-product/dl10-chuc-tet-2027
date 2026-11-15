package online.duolap.tetviet.game.dapchuot.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import online.duolap.tetviet.R
import online.duolap.tetviet.game.dapchuot.engine.MoleType
import kotlin.math.min

/**
 * Một hang chuột: hang phía sau, chuột (bị cắt ở miệng hang) và mép đất phía trước.
 * Chuột trồi lên từ trong lòng hang nên mép đất luôn che phần thân dưới.
 *
 * Tỉ lệ khớp với các vector `ic_dc_hole_back` / `ic_dc_hole_front` (khung 200x110):
 * tâm hang ở y = 58, chuột bị cắt ở y = [CLIP_Y_IN_HOLE].
 */
class MoleHoleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val holeBack = ImageView(context).apply {
        setImageResource(R.drawable.ic_dc_hole_back)
        scaleType = ImageView.ScaleType.FIT_XY
    }
    // clipChildren của View cha mới cắt được con theo khung của nó, nên MoleHoleView phải bật clipChildren
    // để khung moleClip cắt chuột ở miệng hang (kể cả lúc đang tụt xuống bằng translationY)
    private val moleClip = FrameLayout(context)
    private val mole = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        visibility = View.INVISIBLE
    }
    private val holeFront = ImageView(context).apply {
        setImageResource(R.drawable.ic_dc_hole_front)
        scaleType = ImageView.ScaleType.FIT_XY
    }

    /** Loại đang trồi lên, null khi hang trống. */
    var currentType: MoleType? = null
        private set

    private var moleAnimator: Animator? = null
    private var idleAnimator: ValueAnimator? = null

    init {
        clipChildren = true
        moleClip.addView(mole)
        addView(holeBack)
        addView(moleClip)
        addView(holeFront)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val g = geometry(w, h)
        holeBack.measure(exactly(g.holeW), exactly(g.holeH))
        holeFront.measure(exactly(g.holeW), exactly(g.holeH))
        moleClip.measure(exactly(g.holeW), exactly(g.clipBottom - g.top))
        mole.measure(exactly(g.moleSize), exactly(g.moleSize))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val g = geometry(right - left, bottom - top)
        holeBack.layout(g.left, g.holeTop, g.left + g.holeW, g.holeTop + g.holeH)
        holeFront.layout(g.left, g.holeTop, g.left + g.holeW, g.holeTop + g.holeH)
        moleClip.layout(g.left, g.top, g.left + g.holeW, g.clipBottom)
        // Đáy chuột thấp hơn đường cắt một chút để không lộ mép thẳng khi trồi lên hết
        val moleLeft = (g.holeW - g.moleSize) / 2
        val moleBottom = g.clipBottom - g.top + (g.moleSize * 0.06f).toInt()
        mole.layout(moleLeft, moleBottom - g.moleSize, moleLeft + g.moleSize, moleBottom)
        mole.pivotX = g.moleSize / 2f
        mole.pivotY = g.moleSize.toFloat()
        if (mole.visibility != View.VISIBLE) mole.translationY = hiddenOffset()
    }

    private data class Geometry(
        val left: Int, val top: Int, val holeW: Int, val holeH: Int,
        val holeTop: Int, val clipBottom: Int, val moleSize: Int
    )

    private fun geometry(w: Int, h: Int): Geometry {
        // Ô cần cao khoảng 0.95 lần chiều rộng hang; ô thấp hơn thì thu nhỏ hang lại
        val holeW = min(w, (h / CELL_ASPECT).toInt()).coerceAtLeast(1)
        val holeH = (holeW * HOLE_ASPECT).toInt()
        val left = (w - holeW) / 2
        val holeTop = h - holeH
        val clipBottom = holeTop + (holeH * CLIP_Y_IN_HOLE).toInt()
        val moleSize = (holeW * MOLE_WIDTH_RATIO).toInt()
        return Geometry(left, (clipBottom - moleSize * 1.15f).toInt().coerceAtLeast(0), holeW, holeH, holeTop, clipBottom, moleSize)
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size.coerceAtLeast(0), MeasureSpec.EXACTLY)

    private fun hiddenOffset() = mole.height * 1.08f

    /** Tâm hang trong toạ độ của [ancestor], dùng để đặt búa, điểm bay và hạt hiệu ứng. */
    fun moleCenterIn(ancestor: View, out: FloatArray) {
        var x = left + moleClip.left + mole.left + mole.width / 2f
        var y = top + moleClip.top + mole.top + mole.height * 0.45f
        var p = parent as? View
        while (p != null && p !== ancestor) {
            x += p.left - p.scrollX
            y += p.top - p.scrollY
            p = p.parent as? View
        }
        out[0] = x
        out[1] = y
    }

    fun popUp(type: MoleType) {
        cancelAnimations()
        currentType = type
        mole.setImageResource(imageFor(type, hit = false))
        mole.visibility = View.VISIBLE
        mole.alpha = 1f
        mole.rotation = 0f
        mole.scaleX = 0.82f
        mole.scaleY = 1.18f
        mole.translationY = hiddenOffset()

        val rise = ObjectAnimator.ofFloat(mole, View.TRANSLATION_Y, hiddenOffset(), 0f).apply {
            duration = 220L
            interpolator = OvershootInterpolator(1.6f)
        }
        // Co giãn kiểu hoạt hình: dài ra khi vọt lên, bẹt xuống khi dừng
        val settleX = ObjectAnimator.ofFloat(mole, View.SCALE_X, 0.82f, 1.08f, 1f).apply { duration = 320L }
        val settleY = ObjectAnimator.ofFloat(mole, View.SCALE_Y, 1.18f, 0.92f, 1f).apply { duration = 320L }
        moleAnimator = AnimatorSet().apply {
            playTogether(rise, settleX, settleY)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (currentType == type) startIdle(type)
                }
            })
            start()
        }
    }

    /** Trốn xuống hang khi hết thời gian mà chưa bị đập. */
    fun hide() {
        if (mole.visibility != View.VISIBLE) return
        cancelAnimations()
        currentType = null
        mole.animate()
            .translationY(hiddenOffset())
            .scaleY(1.1f)
            .setDuration(170L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { resetMole() }
            .start()
    }

    /** Bị đập trúng: đổi mặt choáng, bẹt xuống rồi tụt hẳn vào hang. */
    fun hit() {
        val type = currentType ?: return
        cancelAnimations()
        currentType = null
        if (type == MoleType.BOMB) {
            // Vụ nổ to hơn hang nên Activity vẽ nó ở lớp hiệu ứng; ở đây pháo biến mất ngay
            resetMole()
            return
        }
        mole.setImageResource(imageFor(type, hit = true))
        val squashX = ObjectAnimator.ofFloat(mole, View.SCALE_X, 1f, 1.3f, 1.15f)
        val squashY = ObjectAnimator.ofFloat(mole, View.SCALE_Y, 1f, 0.6f, 0.75f)
        val squash = AnimatorSet().apply {
            playTogether(squashX, squashY)
            duration = 120L
        }
        val sink = ObjectAnimator.ofFloat(mole, View.TRANSLATION_Y, 0f, hiddenOffset()).apply {
            startDelay = 260L
            duration = 200L
            interpolator = AccelerateInterpolator()
        }
        moleAnimator = AnimatorSet().apply {
            playTogether(squash, sink)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = resetMole()
            })
            start()
        }
    }

    /** Hoạt cảnh lặp ở màn hình bắt đầu: chuột ló lên, nghiêng đầu rồi lặn xuống. */
    fun peekLoop(): Animator {
        cancelAnimations()
        currentType = MoleType.NORMAL
        mole.setImageResource(R.drawable.ic_dc_mouse)
        mole.visibility = View.VISIBLE
        val up = ObjectAnimator.ofFloat(mole, View.TRANSLATION_Y, hiddenOffset(), 0f).apply {
            duration = 380L
            interpolator = OvershootInterpolator(1.4f)
        }
        val tilt = ObjectAnimator.ofFloat(mole, View.ROTATION, 0f, -9f, 8f, -4f, 0f).apply {
            duration = 1100L
        }
        val down = ObjectAnimator.ofFloat(mole, View.TRANSLATION_Y, 0f, hiddenOffset()).apply {
            startDelay = 500L
            duration = 260L
            interpolator = AccelerateInterpolator()
        }
        val set = AnimatorSet()
        set.playSequentially(up, tilt, down)
        set.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }
            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled && isAttachedToWindow) {
                    animation.startDelay = 650L
                    animation.start()
                }
            }
        })
        moleAnimator = set
        set.start()
        return set
    }

    fun reset() {
        cancelAnimations()
        currentType = null
        resetMole()
    }

    private fun startIdle(type: MoleType) {
        // Nhún nhẹ và lắc đầu trong lúc chờ; pháo thì rung mạnh hơn như sắp nổ
        val amplitude = if (type == MoleType.BOMB) 5f else 3f
        idleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (type == MoleType.BOMB) 260L else 620L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                val v = it.animatedValue as Float
                mole.rotation = (v - 0.5f) * 2f * amplitude
                mole.scaleY = 1f + v * 0.04f
            }
            start()
        }
    }

    private fun cancelAnimations() {
        moleAnimator?.cancel()
        moleAnimator = null
        idleAnimator?.cancel()
        idleAnimator = null
        mole.animate().cancel()
    }

    private fun resetMole() {
        mole.visibility = View.INVISIBLE
        mole.translationY = hiddenOffset()
        mole.scaleX = 1f
        mole.scaleY = 1f
        mole.rotation = 0f
        mole.alpha = 1f
    }

    override fun onDetachedFromWindow() {
        cancelAnimations()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val HOLE_ASPECT = 110f / 200f
        private const val CLIP_Y_IN_HOLE = 84f / 110f
        private const val MOLE_WIDTH_RATIO = 0.68f
        private const val CELL_ASPECT = 0.95f

        fun imageFor(type: MoleType, hit: Boolean): Int = when (type) {
            MoleType.NORMAL -> if (hit) R.drawable.ic_dc_mouse_hit else R.drawable.ic_dc_mouse
            MoleType.GOLDEN -> if (hit) R.drawable.ic_dc_mouse_golden_hit else R.drawable.ic_dc_mouse_golden
            MoleType.BOMB -> R.drawable.ic_dc_firecracker
        }
    }
}
