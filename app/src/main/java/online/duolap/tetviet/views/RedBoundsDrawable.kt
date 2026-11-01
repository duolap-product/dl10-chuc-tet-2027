package online.duolap.tetviet.views

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Drawable hiển thị viền đỏ bao quanh bounds của TextView
 * cùng với nút kéo thay đổi kích thước (resize handle) to, rõ ràng và dễ thao tác ở góc dưới-phải.
 */
class RedBoundsDrawable(
    private val density: Float = 3f,
    private val strokeColor: Int = Color.parseColor("#E53935") // Màu đỏ Tết nổi bật
) : Drawable() {

    var isBoundsVisible: Boolean = true
        set(value) {
            field = value
            invalidateSelf()
        }

    var isResizing: Boolean = false
        set(value) {
            field = value
            invalidateSelf()
        }

    private val strokeWidthPx: Float = 2.5f * density
    // Nút kéo góc dưới-phải: bán kính 20dp (đường kính 40dp) to bản, cực kỳ dễ nhìn và dễ chạm
    private val baseHandleRadiusPx: Float = 20f * density

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }

    private val cornerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.FILL
    }

    private val resizeHandleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(6f * density, 0f, 2f * density, Color.parseColor("#33000000"))
    }

    private val resizeHandleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val rectF = RectF()
    private val arrowPath = Path()

    override fun draw(canvas: Canvas) {
        if (!isBoundsVisible) return

        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        val halfStroke = strokeWidthPx / 2f
        rectF.set(
            b.left + halfStroke,
            b.top + halfStroke,
            b.right - halfStroke,
            b.bottom - halfStroke
        )

        // 1. Vẽ khung chữ nhật viền đỏ bao quanh bounds
        canvas.drawRect(rectF, borderPaint)

        // 2. Vẽ 3 chốt định vị tròn nhỏ ở góc trên-trái, trên-phải, dưới-trái
        val dotRadius = 4.5f * density
        canvas.drawCircle(rectF.left, rectF.top, dotRadius, cornerDotPaint)
        canvas.drawCircle(rectF.right, rectF.top, dotRadius, cornerDotPaint)
        canvas.drawCircle(rectF.left, rectF.bottom, dotRadius, cornerDotPaint)

        // 3. Vẽ nút kéo to ở góc dưới-phải (Vòng tròn nền trắng viền đỏ + biểu tượng mũi tên chéo 2 chiều ⤡)
        val handleRadius = if (isResizing) baseHandleRadiusPx * 1.15f else baseHandleRadiusPx
        val handleCenterX = rectF.right - (4f * density)
        val handleCenterY = rectF.bottom - (4f * density)

        // Nền nút kéo (đổi màu vàng kem nhẹ khi đang được user kéo)
        resizeHandleBgPaint.color = if (isResizing) Color.parseColor("#FFF3E0") else Color.WHITE
        canvas.drawCircle(handleCenterX, handleCenterY, handleRadius, resizeHandleBgPaint)

        // Viền đỏ cho nút kéo
        canvas.drawCircle(handleCenterX, handleCenterY, handleRadius, resizeHandleStrokePaint)

        // Biểu tượng mũi tên kéo giãn 2 chiều ⤡
        val arrowHalfSize = handleRadius * 0.48f
        val arrowHead = 4.5f * density

        arrowPath.reset()
        // Trục chéo
        arrowPath.moveTo(handleCenterX - arrowHalfSize, handleCenterY - arrowHalfSize)
        arrowPath.lineTo(handleCenterX + arrowHalfSize, handleCenterY + arrowHalfSize)

        // Mũi tên góc trên-trái
        arrowPath.moveTo(handleCenterX - arrowHalfSize, handleCenterY - arrowHalfSize + arrowHead)
        arrowPath.lineTo(handleCenterX - arrowHalfSize, handleCenterY - arrowHalfSize)
        arrowPath.lineTo(handleCenterX - arrowHalfSize + arrowHead, handleCenterY - arrowHalfSize)

        // Mũi tên góc dưới-phải
        arrowPath.moveTo(handleCenterX + arrowHalfSize, handleCenterY + arrowHalfSize - arrowHead)
        arrowPath.lineTo(handleCenterX + arrowHalfSize, handleCenterY + arrowHalfSize)
        arrowPath.lineTo(handleCenterX + arrowHalfSize - arrowHead, handleCenterY + arrowHalfSize)

        canvas.drawPath(arrowPath, arrowPaint)
    }

    override fun getPadding(padding: Rect): Boolean {
        // Khoảng đệm để chữ không chạm vào viền và nút góc dưới-phải
        val padH = (14f * density).toInt()
        val padV = (12f * density).toInt()
        val padBottom = (16f * density).toInt()
        padding.set(padH, padV, padH, padBottom)
        return true
    }

    override fun setAlpha(alpha: Int) {
        borderPaint.alpha = alpha
        cornerDotPaint.alpha = alpha
        resizeHandleBgPaint.alpha = alpha
        resizeHandleStrokePaint.alpha = alpha
        arrowPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        borderPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
