package online.duolap.tetviet.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.toColorInt
import online.duolap.tetviet.data.model.WheelItem
import online.duolap.tetviet.data.model.WheelStickerManager
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class LuckyWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var items: List<WheelItem> = emptyList()

    private var rotationAngle = 0f
    var isSpinning = false
        private set

    private var wheelTypeface: Typeface? = null
    private val drawableCache = mutableMapOf<Int, Drawable>()

    private fun getCachedDrawable(resId: Int): Drawable? {
        return drawableCache.getOrPut(resId) {
            ContextCompat.getDrawable(context, resId)?.mutate() ?: return null
        }
    }

    // Paints
    private val sectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = "#E8C37B".toColorInt()
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        textAlign = Paint.Align.RIGHT
        setShadowLayer(4f, 1f, 2f, "#80000000".toColorInt())
    }
    private val outerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val bulbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#FFFFEE".toColorInt()
    }
    private val centerCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#7A090D".toColorInt()
        textSize = 38f
        textAlign = Paint.Align.CENTER
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#FFD700".toColorInt()
        setShadowLayer(6f, 0f, 3f, "#60000000".toColorInt())
    }
    private val pointerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = "#7A090D".toColorInt()
    }

    init {
        var tf: Typeface? = null
        if (attrs != null) {
            context.withStyledAttributes(attrs, intArrayOf(android.R.attr.fontFamily)) {
                val fontId = getResourceId(0, 0)
                if (fontId != 0) {
                    try {
                        tf = ResourcesCompat.getFont(context, fontId)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        if (tf == null) {
            try {
                tf = ResourcesCompat.getFont(context, online.duolap.tetviet.R.font.liberationsans_bold)
            } catch (_: Exception) {}
        }
        setWheelTypeface(tf)
    }

    fun setWheelTypeface(typeface: Typeface?) {
        this.wheelTypeface = typeface
        if (typeface != null) {
            textPaint.typeface = typeface
            textPaint.isFakeBoldText = false
            centerTextPaint.typeface = typeface
            centerTextPaint.isFakeBoldText = false
        } else {
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.isFakeBoldText = true
            centerTextPaint.typeface = Typeface.DEFAULT_BOLD
            centerTextPaint.isFakeBoldText = true
        }
        invalidate()
    }

    fun setWheelFontResource(fontResId: Int) {
        try {
            val tf = ResourcesCompat.getFont(context, fontResId)
            setWheelTypeface(tf)
        } catch (_: Exception) {}
    }

    private val wheelRect = RectF()
    private val pointerPath = Path()

    fun setItems(newItems: List<WheelItem>) {
        if (isSpinning) return
        this.items = newItems
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    @SuppressLint("DrawAllocation", "UseKtx")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f

        val pointerHeight = 36f
        val rimThickness = 22f
        val radius = (min(width, height) / 2f) - pointerHeight - 10f

        wheelRect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        // 1. Draw Rotating Wheel (Sectors & Texts)
        canvas.save()
        canvas.rotate(rotationAngle, centerX, centerY)

        val sweepAngle = 360f / items.size
        for (i in items.indices) {
            val item = items[i]
            val startAngle = i * sweepAngle

            // Sector path: triangular arc slice
            val sectorPath = Path().apply {
                moveTo(centerX, centerY)
                arcTo(wheelRect, startAngle, sweepAngle, false)
                close()
            }

            // 1. Sector background color
            try {
                sectorPaint.color = item.colorHex.toColorInt()
            } catch (_: Exception) {
                sectorPaint.color = if (i % 2 == 0) "#C62828".toColorInt() else "#FFA000".toColorInt()
            }
            canvas.drawPath(sectorPath, sectorPaint)

            // 2. Sector background image / sticker clipped to triangle sector
            val stickerResId = WheelStickerManager.getDrawableResId(item.bgImageName)
            if (stickerResId != 0) {
                val drawable = getCachedDrawable(stickerResId)
                if (drawable != null) {
                    canvas.save()
                    // Clip canvas strictly to sector triangle
                    canvas.clipPath(sectorPath)

                    // Rotate to sector middle axis
                    val midAngle = startAngle + sweepAngle / 2f
                    canvas.rotate(midAngle, centerX, centerY)

                    // Position sticker at outer half of sector
                    val stickerSize = radius * 0.52f
                    val sLeft = (centerX + radius * 0.62f - stickerSize / 2f).toInt()
                    val sTop = (centerY - stickerSize / 2f).toInt()
                    val sRight = (sLeft + stickerSize).toInt()
                    val sBottom = (sTop + stickerSize).toInt()

                    drawable.setBounds(sLeft, sTop, sRight, sBottom)
                    drawable.alpha = 150
                    drawable.draw(canvas)

                    canvas.restore()
                }
            }

            // 3. Divider stroke
            canvas.drawPath(sectorPath, strokePaint)

            // 4. Draw Sector Text
            canvas.save()
            val textAngle = startAngle + sweepAngle / 2f
            canvas.rotate(textAngle, centerX, centerY)

            // Dynamically adjust text size based on item count
            val dynamicTextSize = when {
                items.size <= 6 -> radius * 0.11f
                items.size <= 8 -> radius * 0.095f
                items.size <= 10 -> radius * 0.082f
                else -> radius * 0.07f
            }
            textPaint.textSize = dynamicTextSize

            // Text distance from center
            val textX = centerX + radius - rimThickness - 16f
            val textBounds = Rect()
            textPaint.getTextBounds(item.text, 0, item.text.length, textBounds)
            val textY = centerY + (textBounds.height() / 2f)

            // Truncate if text is too long
            var displayText = item.text
            if (displayText.length > 13) {
                displayText = displayText.take(11) + "…"
            }

            canvas.drawText(displayText, textX, textY, textPaint)
            canvas.restore()
        }

        // 2. Outer Decorative Gold Rim with Bulbs
        outerRimPaint.strokeWidth = rimThickness
        outerRimPaint.color = "#FFA800".toColorInt()
        canvas.drawCircle(centerX, centerY, radius + (rimThickness / 2f), outerRimPaint)

        outerRimPaint.strokeWidth = 2f
        outerRimPaint.color = "#FFE8A3".toColorInt()
        canvas.drawCircle(centerX, centerY, radius + rimThickness, outerRimPaint)
        canvas.drawCircle(centerX, centerY, radius, outerRimPaint)

        // Draw bulbs around the rim
        val totalBulbs = maxOf(16, items.size * 2)
        val bulbRadius = 4.5f
        val bulbOrbitRadius = radius + (rimThickness / 2f)
        for (b in 0 until totalBulbs) {
            val angleRad = Math.toRadians((b * (360.0 / totalBulbs)))
            val bx = (centerX + bulbOrbitRadius * cos(angleRad)).toFloat()
            val by = (centerY + bulbOrbitRadius * sin(angleRad)).toFloat()

            bulbPaint.color = if (b % 2 == 0) "#FFFFEE".toColorInt() else "#FFD54F".toColorInt()
            canvas.drawCircle(bx, by, bulbRadius, bulbPaint)
        }

        // 3. Center Hub Cap
        val centerRadius = radius * 0.28f
        centerCapPaint.shader = RadialGradient(
            centerX, centerY, centerRadius,
            intArrayOf("#FFF8E7".toColorInt(), "#FFD54F".toColorInt(), "#E59800".toColorInt()),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, centerRadius, centerCapPaint)

        strokePaint.color = "#8A000A".toColorInt()
        strokePaint.strokeWidth = 3f
        canvas.drawCircle(centerX, centerY, centerRadius, strokePaint)

        centerTextPaint.textSize = centerRadius * 0.58f
        val capBounds = Rect()
        centerTextPaint.getTextBounds("LỘC", 0, 3, capBounds)
        canvas.drawText("LỘC", centerX, centerY + (capBounds.height() / 2f), centerTextPaint)

        canvas.restore() // End rotating canvas

        // 4. Fixed Pointer Indicator at Top (12 o'clock)
        val pointerTop = centerY - radius - rimThickness - 6f
        val pointerBottom = centerY - radius + 18f
        val pointerHalfWidth = 18f

        pointerPath.reset()
        pointerPath.moveTo(centerX, pointerBottom) // Tip pointing downward into the wheel
        pointerPath.lineTo(centerX - pointerHalfWidth, pointerTop)
        pointerPath.lineTo(centerX + pointerHalfWidth, pointerTop)
        pointerPath.close()

        canvas.drawPath(pointerPath, pointerPaint)
        canvas.drawPath(pointerPath, pointerStrokePaint)

        // Small jewel in pointer
        bulbPaint.color = "#C41E24".toColorInt()
        canvas.drawCircle(centerX, pointerTop + 10f, 5f, bulbPaint)
    }

    /**
     * Start spinning the wheel to land on [targetIndex]
     */
    fun startSpin(targetIndex: Int, onEnd: (WheelItem) -> Unit) {
        if (isSpinning || items.isEmpty() || targetIndex !in items.indices) return
        isSpinning = true

        val sweepAngle = 360f / items.size
        // 12 o'clock in canvas coordinates is 270 degrees
        val targetSectorCenter = targetIndex * sweepAngle + sweepAngle / 2f
        val neededNormalizedAngle = (270f - targetSectorCenter + 360f) % 360f

        val fullRotations = 6 * 360f // 6 full spins
        val currentMod = rotationAngle % 360f
        val finalAngle = rotationAngle - currentMod + fullRotations + neededNormalizedAngle

        val animator = ValueAnimator.ofFloat(rotationAngle, finalAngle).apply {
            duration = 4600L
            interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isSpinning = false
                    rotationAngle = finalAngle % 360f
                    invalidate()
                    onEnd(items[targetIndex])
                }
            })
        }
        animator.start()
    }
}