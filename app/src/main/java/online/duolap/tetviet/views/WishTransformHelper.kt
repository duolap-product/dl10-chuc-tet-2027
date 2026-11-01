package online.duolap.tetviet.views

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.doOnLayout
import kotlin.math.hypot

/**
 * Helper quản lý kéo thả (drag to move) và thay đổi kích thước (resize/pinch)
 * cho [targetView] bên trong giới hạn của [parentContainer].
 * Cung cấp vùng cảm ứng góc dưới-phải mở rộng (70dp+) để thao tác dễ dàng nhất.
 */
class WishTransformHelper(
    private val targetView: TextView,
    private val parentContainer: ViewGroup,
    private val minWidthDp: Float = 100f,
    private val minHeightDp: Float = 50f,
    private val onResizeStateChanged: ((Boolean) -> Unit)? = null,
    private val onDragStarted: (() -> Unit)? = null,
    private val onClick: (() -> Unit)? = null
) {

    private enum class TouchMode {
        NONE,
        DRAG,
        RESIZE,
        PINCH
    }

    private val density = targetView.resources.displayMetrics.density
    private val minWidthPx = (minWidthDp * density).toInt()
    private val minHeightPx = (minHeightDp * density).toInt()

    // Mở rộng vùng chạm quanh góc dưới-phải lên 20dp để user dễ dàng chạm trúng bằng ngón tay cái
    private val handleTouchZonePx = (20f * density)

    private var currentMode = TouchMode.NONE

    private var startRawX = 0f
    private var startRawY = 0f
    private var initialViewX = 0f
    private var initialViewY = 0f
    private var initialWidth = 0
    private var initialHeight = 0

    // Dành cho cử chỉ zoom 2 ngón (pinch-to-resize)
    private var initialPinchDistance = 0f

    private var downTime = 0L
    private var hasMoved = false

    init {
        centerInParent(animate = true)
        setupTouchListener()
    }

    /**
     * Căn giữa tvWish ra giữa màn hình/thiệp, hỗ trợ chuyển động animation mượt mà từ góc 0 ra giữa
     */
    fun centerInParent(animate: Boolean = true) {
        parentContainer.doOnLayout { parent ->
            targetView.doOnLayout { view ->
                val parentW = parent.width
                val parentH = parent.height
                if (parentW <= 0 || parentH <= 0) return@doOnLayout

                val viewW = if (view.width > 0) view.width else (220f * density).toInt()
                val viewH = if (view.height > 0) view.height else (100f * density).toInt()

                val targetX = ((parentW - viewW) / 2f).coerceAtLeast(0f)
                val targetY = ((parentH - viewH) / 2f).coerceAtLeast(0f)

                val lp = (view.layoutParams as? FrameLayout.LayoutParams)
                    ?: FrameLayout.LayoutParams(viewW, viewH)

                lp.gravity = Gravity.TOP or Gravity.START
                lp.width = viewW
                lp.height = viewH
                view.layoutParams = lp

                if (animate) {
                    view.animate()
                        .x(targetX)
                        .y(targetY)
                        .setDuration(450)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                } else {
                    view.x = targetX
                    view.y = targetY
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        targetView.setOnTouchListener { view, event ->
            val parentW = parentContainer.width
            val parentH = parentContainer.height

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    targetView.animate().cancel()
                    downTime = System.currentTimeMillis()
                    hasMoved = false
                    startRawX = event.rawX
                    startRawY = event.rawY
                    initialViewX = targetView.x
                    initialViewY = targetView.y
                    initialWidth = targetView.width
                    initialHeight = targetView.height

                    val distToCorner = hypot(event.x - view.width, event.y - view.height)
                    val isNearCorner = distToCorner <= handleTouchZonePx
                    val isNearRightEdgeBottomHalf = (event.x >= view.width - handleTouchZonePx) && (event.y >= view.height * 0.45f)
                    val isNearBottomEdgeRightHalf = (event.y >= view.height - handleTouchZonePx) && (event.x >= view.width * 0.45f)

                    val isResizeTouch = isNearCorner || isNearRightEdgeBottomHalf || isNearBottomEdgeRightHalf

                    currentMode = if (isResizeTouch) {
                        onResizeStateChanged?.invoke(true)
                        TouchMode.RESIZE
                    } else {
                        onResizeStateChanged?.invoke(false)
                        TouchMode.DRAG
                    }

                    parentContainer.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        currentMode = TouchMode.PINCH
                        onResizeStateChanged?.invoke(true)
                        initialPinchDistance = getDistanceBetweenPointers(event)
                        initialWidth = targetView.width
                        initialHeight = targetView.height
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaRawX = event.rawX - startRawX
                    val deltaRawY = event.rawY - startRawY
                    if (hypot(deltaRawX, deltaRawY) > 8f) {
                        hasMoved = true
                        onDragStarted?.invoke()
                    }

                    when (currentMode) {
                        TouchMode.DRAG -> {
                            // Di chuyển view và giới hạn trong parentContainer
                            val targetX = initialViewX + deltaRawX
                            val targetY = initialViewY + deltaRawY

                            val maxX = (parentW - targetView.width).toFloat().coerceAtLeast(0f)
                            val maxY = (parentH - targetView.height).toFloat().coerceAtLeast(0f)

                            targetView.x = targetX.coerceIn(0f, maxX)
                            targetView.y = targetY.coerceIn(0f, maxY)
                        }

                        TouchMode.RESIZE -> {
                            // Kéo góc dưới-phải để thay đổi width và height
                            val targetW = (initialWidth + deltaRawX).toInt()
                            val targetH = (initialHeight + deltaRawY).toInt()

                            // Giới hạn max không vượt quá biên phải và biên dưới của parentContainer
                            val maxW = (parentW - targetView.x.toInt()).coerceAtLeast(minWidthPx)
                            val maxH = (parentH - targetView.y.toInt()).coerceAtLeast(minHeightPx)

                            val clampedW = targetW.coerceIn(minWidthPx, maxW)
                            val clampedH = targetH.coerceIn(minHeightPx, maxH)

                            val lp = targetView.layoutParams
                            lp.width = clampedW
                            lp.height = clampedH
                            targetView.layoutParams = lp
                            targetView.requestLayout()
                        }

                        TouchMode.PINCH -> {
                            if (event.pointerCount >= 2 && initialPinchDistance > 10f) {
                                val currentDistance = getDistanceBetweenPointers(event)
                                val scaleFactor = currentDistance / initialPinchDistance

                                val targetW = (initialWidth * scaleFactor).toInt()
                                val targetH = (initialHeight * scaleFactor).toInt()

                                val maxW = (parentW - targetView.x.toInt()).coerceAtLeast(minWidthPx)
                                val maxH = (parentH - targetView.y.toInt()).coerceAtLeast(minHeightPx)

                                val clampedW = targetW.coerceIn(minWidthPx, maxW)
                                val clampedH = targetH.coerceIn(minHeightPx, maxH)

                                val lp = targetView.layoutParams
                                lp.width = clampedW
                                lp.height = clampedH
                                targetView.layoutParams = lp
                                targetView.requestLayout()
                            }
                        }

                        TouchMode.NONE -> {}
                    }
                    true
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    if (currentMode == TouchMode.PINCH && event.pointerCount <= 2) {
                        currentMode = TouchMode.NONE
                        onResizeStateChanged?.invoke(false)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parentContainer.requestDisallowInterceptTouchEvent(false)
                    onResizeStateChanged?.invoke(false)

                    val clickDuration = System.currentTimeMillis() - downTime
                    if (!hasMoved && clickDuration < 300) {
                        // Người dùng chạm nhẹ (tap) vào TextView -> mở dialog sửa chữ
                        onClick?.invoke()
                    }
                    currentMode = TouchMode.NONE
                    true
                }

                else -> false
            }
        }
    }

    private fun getDistanceBetweenPointers(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return hypot(dx, dy)
    }
}
