package online.duolap.tetviet.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import online.duolap.tetviet.data.model.CardStickerItem
import online.duolap.tetviet.databinding.ViewStickerItemBinding
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * View sticker trên thiệp Tết hỗ trợ:
 * - Hiển thị đồ họa sticker sắc nét
 * - Kéo thả thay đổi vị trí (drag to move)
 * - Đổi kích thước (resize bằng kéo núm góc dưới-phải hoặc cử chỉ pinch 2 ngón)
 * - Xoay chiều sticker (rotate bằng kéo núm xoay góc dưới-trái, chạm để xoay 45° hoặc cử chỉ vặn 2 ngón)
 * - Nút xoá sticker (nút 'X' màu đỏ ở góc trên-phải)
 * - Ẩn hiện viền và các núm điều khiển khi chọn / bỏ chọn và khi xuất ảnh thiệp
 */
class StickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private enum class TouchMode {
        NONE,
        DRAG,
        RESIZE,
        ROTATE,
        PINCH_ROTATE
    }

    private val binding = ViewStickerItemBinding.inflate(LayoutInflater.from(context), this)

    private val density = resources.displayMetrics.density
    val paddingDp = 14f
    val paddingPx = (paddingDp * density).toInt()

    private val minImageSizePx = (44f * density).toInt()
    private val maxImageSizePx = (240f * density).toInt()
    private var currentImageSizePx = (74f * density).toInt()

    var isSelectedSticker: Boolean = false
        set(value) {
            field = value
            updateControlsVisibility()
        }

    var isExportMode: Boolean = false
        set(value) {
            field = value
            updateControlsVisibility()
        }

    var onDeleteClickListener: ((StickerView) -> Unit)? = null
    var onSelectedListener: ((StickerView) -> Unit)? = null

    private var currentMode = TouchMode.NONE
    private var startRawX = 0f
    private var startRawY = 0f
    private var initialViewX = 0f
    private var initialViewY = 0f
    private var startImageSize = 0

    // Rotation & Center Tracking
    private var centerScreenX = 0f
    private var centerScreenY = 0f
    private var startTouchAngle = 0f
    private var startDistance = 0f
    private var initialRotation = 0f

    // 2-Finger Gestures
    private var initialPinchDistance = 0f
    private var initialFingerAngle = 0f

    private var downTime = 0L
    private var hasMoved = false

    init {
        clipChildren = false
        clipToPadding = false

        val initialTotalSize = currentImageSizePx + 2 * paddingPx
        layoutParams = LayoutParams(initialTotalSize, initialTotalSize)
        pivotX = initialTotalSize / 2f
        pivotY = initialTotalSize / 2f

        binding.btnDeleteSticker.setOnClickListener {
            onDeleteClickListener?.invoke(this)
        }

        updateControlsVisibility()
    }

    fun setSticker(@DrawableRes resId: Int) {
        binding.ivStickerImage.setImageResource(resId)
    }

    fun setSticker(item: CardStickerItem) {
        if (item.url?.startsWith("http://") == true || item.url?.startsWith("https://") == true) {
            Glide.with(this).load(item.url).into(binding.ivStickerImage)
        } else if (item.resId != 0) {
            binding.ivStickerImage.setImageResource(item.resId)
        } else if (!item.url.isNullOrEmpty()) {
            val resId = context.resources.getIdentifier(item.url, "drawable", context.packageName)
            if (resId != 0) {
                binding.ivStickerImage.setImageResource(resId)
            } else {
                Glide.with(this).load(item.url).into(binding.ivStickerImage)
            }
        }
    }

    fun getTotalSize(): Int {
        return currentImageSizePx + 2 * paddingPx
    }

    private fun updateControlsVisibility() {
        val showControls = isSelectedSticker && !isExportMode
        binding.viewStickerBorder.isVisible = showControls
        binding.btnDeleteSticker.isVisible = showControls
        binding.btnResizeSticker.isVisible = showControls
        binding.btnRotateSticker.isVisible = showControls
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isExportMode) return false

        if (isSelectedSticker && binding.btnDeleteSticker.isVisible) {
            val deleteRect = Rect()
            binding.btnDeleteSticker.getHitRect(deleteRect)
            val touchExpansion = (8f * density).toInt()
            val expandedRect = Rect(
                deleteRect.left - touchExpansion,
                deleteRect.top - touchExpansion,
                deleteRect.right + touchExpansion,
                deleteRect.bottom + touchExpansion
            )
            // Nếu bấm trúng nút xoá thì để nút xoá xử lý click
            if (expandedRect.contains(event.x.toInt(), event.y.toInt())) {
                return false
            }
        }

        // Mọi thao tác chạm khác trên sticker sẽ được StickerView xử lý kéo/đổi kích thước/xoay/chọn
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isExportMode) return false

        val parentView = parent as? View
        val parentW = parentView?.width ?: 0
        val parentH = parentView?.height ?: 0

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = System.currentTimeMillis()
                hasMoved = false
                startRawX = event.rawX
                startRawY = event.rawY
                initialViewX = x
                initialViewY = y
                startImageSize = currentImageSizePx
                initialRotation = rotation

                // Tính tọa độ tâm của sticker trên màn hình
                val location = IntArray(2)
                getLocationOnScreen(location)
                centerScreenX = location[0] + width / 2f
                centerScreenY = location[1] + height / 2f

                startTouchAngle = Math.toDegrees(
                    atan2(
                        (event.rawY - centerScreenY).toDouble(),
                        (event.rawX - centerScreenX).toDouble()
                    )
                ).toFloat()

                startDistance = hypot(
                    (event.rawX - centerScreenX).toDouble(),
                    (event.rawY - centerScreenY).toDouble()
                ).toFloat().coerceAtLeast(10f)

                if (!isSelectedSticker) {
                    currentMode = TouchMode.DRAG
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                val handleTouchZone = 36f * density

                // Nút xoay tại góc dưới - trái (local x = 0, y = height)
                val distToRotateHandle = hypot(event.x - 0f, event.y - height.toFloat())
                val isNearRotateCorner = distToRotateHandle <= handleTouchZone

                // Nút đổi cỡ tại góc dưới - phải (local x = width, y = height)
                val distToResizeHandle = hypot(event.x - width.toFloat(), event.y - height.toFloat())
                val isNearResizeCorner = distToResizeHandle <= handleTouchZone

                currentMode = when {
                    isNearRotateCorner -> TouchMode.ROTATE
                    isNearResizeCorner -> TouchMode.RESIZE
                    else -> TouchMode.DRAG
                }

                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    currentMode = TouchMode.PINCH_ROTATE
                    initialPinchDistance = getDistanceBetweenPointers(event)
                    initialFingerAngle = getFingerAngleInParent(event)
                    initialRotation = rotation
                    startImageSize = currentImageSizePx
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaRawX = event.rawX - startRawX
                val deltaRawY = event.rawY - startRawY

                if (hypot(deltaRawX, deltaRawY) > 8f) {
                    hasMoved = true
                }

                when (currentMode) {
                    TouchMode.DRAG -> {
                        val targetX = initialViewX + deltaRawX
                        val targetY = initialViewY + deltaRawY

                        if (parentW > 0 && parentH > 0) {
                            val maxX = (parentW - width).toFloat().coerceAtLeast(0f)
                            val maxY = (parentH - height).toFloat().coerceAtLeast(0f)
                            x = targetX.coerceIn(0f, maxX)
                            y = targetY.coerceIn(0f, maxY)
                        } else {
                            x = targetX
                            y = targetY
                        }
                    }

                    TouchMode.RESIZE -> {
                        val currentDist = hypot(
                            (event.rawX - centerScreenX).toDouble(),
                            (event.rawY - centerScreenY).toDouble()
                        ).toFloat()

                        val scale = currentDist / startDistance
                        val targetSize = (startImageSize * scale).toInt()
                        val clamped = targetSize.coerceIn(minImageSizePx, maxImageSizePx)
                        updateStickerSizeAroundCenter(clamped)
                    }

                    TouchMode.ROTATE -> {
                        val currentTouchAngle = Math.toDegrees(
                            atan2(
                                (event.rawY - centerScreenY).toDouble(),
                                (event.rawX - centerScreenX).toDouble()
                            )
                        ).toFloat()

                        var deltaAngle = currentTouchAngle - startTouchAngle
                        while (deltaAngle > 180f) deltaAngle -= 360f
                        while (deltaAngle < -180f) deltaAngle += 360f

                        var newRotation = (initialRotation + deltaAngle) % 360f
                        if (newRotation < 0) newRotation += 360f
                        rotation = newRotation
                    }

                    TouchMode.PINCH_ROTATE -> {
                        if (event.pointerCount >= 2 && initialPinchDistance > 10f) {
                            // 1. Phóng to / thu nhỏ
                            val currentDist = getDistanceBetweenPointers(event)
                            val scale = currentDist / initialPinchDistance
                            val targetSize = (startImageSize * scale).toInt()
                            val clamped = targetSize.coerceIn(minImageSizePx, maxImageSizePx)
                            updateStickerSizeAroundCenter(clamped)

                            // 2. Xoay góc bằng 2 ngón tay
                            val currentAngle = getFingerAngleInParent(event)
                            var deltaAngle = currentAngle - initialFingerAngle
                            while (deltaAngle > 180f) deltaAngle -= 360f
                            while (deltaAngle < -180f) deltaAngle += 360f

                            var newRotation = (initialRotation + deltaAngle) % 360f
                            if (newRotation < 0) newRotation += 360f
                            rotation = newRotation
                        }
                    }

                    TouchMode.NONE -> {}
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (currentMode == TouchMode.PINCH_ROTATE && event.pointerCount <= 2) {
                    currentMode = TouchMode.NONE
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val clickDuration = System.currentTimeMillis() - downTime

                if (!hasMoved && clickDuration < 300) {
                    if (currentMode == TouchMode.ROTATE) {
                        // Chạm vào nút xoay -> quay nhanh một góc 45 độ
                        var newRot = (rotation + 45f) % 360f
                        if (newRot < 0) newRot += 360f
                        rotation = newRot
                        try {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        } catch (_: Exception) {}
                    } else if (!isSelectedSticker) {
                        isSelectedSticker = true
                        bringToFront()
                        onSelectedListener?.invoke(this)
                    }
                } else if (hasMoved) {
                    if (!isSelectedSticker) {
                        isSelectedSticker = true
                        bringToFront()
                        onSelectedListener?.invoke(this)
                    }
                }

                currentMode = TouchMode.NONE
                return true
            }

            else -> return super.onTouchEvent(event)
        }
    }

    /**
     * Thay đổi kích thước sticker đồng thời giữ nguyên vị trí tâm và điểm xoay
     */
    private fun updateStickerSizeAroundCenter(newImageSize: Int) {
        if (newImageSize == currentImageSizePx) return

        val oldTotalSize = currentImageSizePx + 2 * paddingPx
        currentImageSizePx = newImageSize
        val newTotalSize = currentImageSizePx + 2 * paddingPx

        val deltaSize = newTotalSize - oldTotalSize
        val newX = x - deltaSize / 2f
        val newY = y - deltaSize / 2f

        val parentView = parent as? View
        val parentW = parentView?.width ?: 0
        val parentH = parentView?.height ?: 0

        if (parentW > 0 && parentH > 0) {
            val maxX = (parentW - newTotalSize).toFloat().coerceAtLeast(0f)
            val maxY = (parentH - newTotalSize).toFloat().coerceAtLeast(0f)
            x = newX.coerceIn(0f, maxX)
            y = newY.coerceIn(0f, maxY)
        } else {
            x = newX
            y = newY
        }

        val lp = layoutParams ?: LayoutParams(newTotalSize, newTotalSize)
        lp.width = newTotalSize
        lp.height = newTotalSize
        layoutParams = lp

        pivotX = newTotalSize / 2f
        pivotY = newTotalSize / 2f
        requestLayout()
    }

    private fun getFingerAngleInParent(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        val localAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return (localAngle + rotation) % 360f
    }

    private fun getDistanceBetweenPointers(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return hypot(dx, dy)
    }
}
