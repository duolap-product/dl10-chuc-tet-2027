package online.duolap.tetviet.views

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import online.duolap.tetviet.R
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Hiệu ứng cộng xu minh bạch: các đồng xu bung ra từ nơi phát thưởng rồi bay theo đường cong
 * (Bézier bậc 2) vào ô số dư; số dư đếm tăng dần theo từng đồng bay vào, cuối cùng hiện "+X xu".
 *
 * Xu được vẽ trên một lớp tạm phủ cả cửa sổ (không nhận chạm) nên bay được qua mọi lớp giao diện.
 * Mỗi khung hình chỉ đổi translation/scale/rotation (thuộc tính RenderNode), không vẽ lại view.
 *
 * Màn hình nào hiển thị số dư bằng Flow thì gọi [hold] ngay lúc cộng xu và bỏ qua cập nhật khi
 * [isHeld] để số không nhảy thẳng tới kết quả trước khi xu bay tới; [fly] tự bỏ giữ khi xong.
 */
object CoinFlyAnimator {

    private val held: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())

    fun hold(counter: View) {
        held += counter
    }

    fun isHeld(counter: View): Boolean = counter in held

    /** Bỏ giữ khi không chạy hiệu ứng nữa (ví dụ màn kết quả đã đóng); màn hình tự cập nhật số dư. */
    fun release(counter: View) {
        held -= counter
    }

    /**
     * @param fromScreenX/Y điểm phát xu theo toạ độ màn hình (getLocationOnScreen), có thể nằm trong dialog.
     * @param target ô số dư mà xu bay vào (nảy lên mỗi khi có xu chạm).
     * @param counter TextView số dư, đếm từ [startValue] lên [endValue].
     */
    fun fly(
        activity: Activity,
        fromScreenX: Float,
        fromScreenY: Float,
        target: View,
        counter: TextView?,
        startValue: Int,
        endValue: Int,
        format: (Int) -> String = { it.toString() },
        onEnd: () -> Unit = {}
    ) {
        val amount = endValue - startValue
        val decor = activity.window?.decorView as? ViewGroup
        val animatorsOn = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
        if (decor == null || activity.isFinishing || !target.isAttachedToWindow || target.width == 0 ||
            amount <= 0 || !animatorsOn
        ) {
            counter?.let { it.text = format(endValue); held -= it }
            onEnd()
            return
        }

        val density = activity.resources.displayMetrics.density
        val layer = FrameLayout(activity).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            translationZ = 100 * density
        }
        decor.addView(layer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        counter?.let { held += it; it.text = format(startValue) }

        val layerLoc = IntArray(2).also { decor.getLocationOnScreen(it) }
        val targetLoc = IntArray(2).also { target.getLocationOnScreen(it) }
        val coinSize = (28 * density).toInt()
        val half = coinSize / 2f
        val sx = fromScreenX - layerLoc[0] - half
        val sy = fromScreenY - layerLoc[1] - half
        val tx = targetLoc[0] - layerLoc[0] + target.width / 2f - half
        val ty = targetLoc[1] - layerLoc[1] + target.height / 2f - half

        val count = (6 + amount / 60).coerceIn(6, 14)
        var arrived = 0

        repeat(count) { i ->
            val coin = ImageView(activity).apply {
                setImageResource(R.drawable.ic_coin_gold)
                translationX = sx
                translationY = sy
                scaleX = 0f
                scaleY = 0f
            }
            layer.addView(coin, FrameLayout.LayoutParams(coinSize, coinSize))

            // Bung ra quanh điểm phát
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val radius = (36 + Random.nextInt(40)) * density
            val bx = sx + (radius * kotlin.math.cos(angle)).toFloat()
            val by = sy + (radius * kotlin.math.sin(angle)).toFloat()
            coin.animate()
                .translationX(bx).translationY(by)
                .scaleX(1f).scaleY(1f)
                .setDuration(260)
                .setStartDelay(i * 18L)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .withEndAction {
                    flyCurve(coin, bx, by, tx, ty, density, delay = 120L + i * 55L) {
                        coin.visibility = View.GONE
                        arrived++
                        counter?.text = format(startValue + amount * arrived / count)
                        bump(target)
                        target.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (arrived == count) finish(activity, layer, decor, target, counter, endValue, amount, format, onEnd)
                    }
                }
                .start()
        }
    }

    /** Tiện ích: điểm giữa của một view theo toạ độ màn hình. */
    fun centerOnScreen(view: View): Pair<Float, Float> {
        val loc = IntArray(2).also { view.getLocationOnScreen(it) }
        return loc[0] + view.width / 2f to loc[1] + view.height / 2f
    }

    private fun flyCurve(
        coin: View, x0: Float, y0: Float, x2: Float, y2: Float, density: Float, delay: Long, onArrive: () -> Unit
    ) {
        // Điểm điều khiển: lệch vuông góc với đường thẳng, ngẫu nhiên trái/phải, cong lên trên
        val mx = (x0 + x2) / 2
        val my = (y0 + y2) / 2
        val dist = hypot(x2 - x0, y2 - y0).coerceAtLeast(1f)
        val nx = -(y2 - y0) / dist
        val ny = (x2 - x0) / dist
        val bend = dist * (0.25f + Random.nextFloat() * 0.25f) * (if (Random.nextBoolean()) 1 else -1)
        val cx = mx + nx * bend
        val cy = my + ny * bend - 40 * density
        val spin = if (Random.nextBoolean()) 360f else -360f

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 620L
            startDelay = delay
            interpolator = AccelerateInterpolator(1.3f)
            addUpdateListener {
                val t = it.animatedValue as Float
                val u = 1 - t
                coin.translationX = u * u * x0 + 2 * u * t * cx + t * t * x2
                coin.translationY = u * u * y0 + 2 * u * t * cy + t * t * y2
                coin.rotation = spin * t
                val s = 1f - 0.4f * t
                coin.scaleX = s
                coin.scaleY = s
            }
            doOnEndCompat(onArrive)
            start()
        }
    }

    private fun bump(target: View) {
        target.animate().cancel()
        target.scaleX = 1.18f
        target.scaleY = 1.18f
        target.animate().scaleX(1f).scaleY(1f).setDuration(160).setStartDelay(0).start()
    }

    private fun finish(
        activity: Activity, layer: FrameLayout, decor: ViewGroup, target: View, counter: TextView?,
        endValue: Int, amount: Int, format: (Int) -> String, onEnd: () -> Unit
    ) {
        counter?.let { it.text = format(endValue); held -= it }

        // "+X xu" nổi lên ngay dưới ô số dư rồi mờ dần
        val density = activity.resources.displayMetrics.density
        val layerLoc = IntArray(2).also { decor.getLocationOnScreen(it) }
        val targetLoc = IntArray(2).also { target.getLocationOnScreen(it) }
        val label = TextView(activity).apply {
            text = activity.getString(R.string.coin_fly_gain, amount)
            setTextColor(Color.parseColor("#FFE08A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = ResourcesCompat.getFont(activity, R.font.arimo_bold)
            setShadowLayer(4f, 0f, 2f, Color.parseColor("#B3000000"))
            gravity = Gravity.CENTER
        }
        layer.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        label.post {
            label.translationX = targetLoc[0] - layerLoc[0] + target.width / 2f - label.width / 2f
            label.translationY = targetLoc[1] - layerLoc[1] + target.height.toFloat() + 2 * density
            label.alpha = 0f
            label.animate().alpha(1f).translationYBy(-10 * density).setDuration(220).withEndAction {
                label.animate().alpha(0f).translationYBy(-14 * density).setStartDelay(650).setDuration(380)
                    .withEndAction { decor.removeView(layer) }.start()
            }.start()
        }
        onEnd()
    }

    private fun ValueAnimator.doOnEndCompat(action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = action()
        })
    }
}
