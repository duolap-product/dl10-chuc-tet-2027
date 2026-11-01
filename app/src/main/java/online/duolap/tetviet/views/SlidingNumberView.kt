package online.duolap.tetviet.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.toColorInt
import online.duolap.tetviet.R
import kotlin.math.abs

@SuppressLint("ResourceType")
class SlidingNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#7A090D".toColorInt()
        textAlign = Paint.Align.LEFT
    }

    private var currentText: String = ""
    private var oldText: String = ""
    private var targetText: String = ""
    private var animProgress: Float = 1f
    private var animator: ValueAnimator? = null
    private var changingIndices: Set<Int> = emptySet()

    var text: String
        get() = targetText.ifEmpty { currentText }
        set(value) {
            setText(value, animate = true)
        }

    init {
        // Load custom font liberationsans_bold
        try {
            val tf = ResourcesCompat.getFont(context, R.font.liberationsans_bold)
            tf?.let { textPaint.typeface = it }
        } catch (_: Exception) {
        }

        // Default text size: 21sp
        val defaultSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            21f,
            resources.displayMetrics
        )
        textPaint.textSize = defaultSize

        if (attrs != null) {
            context.withStyledAttributes(
                attrs, intArrayOf(
                    android.R.attr.textSize,
                    android.R.attr.textColor,
                    android.R.attr.text
                )
            ) {
                val textSizePx = getDimension(0, defaultSize)
                textPaint.textSize = textSizePx

                val textColor = getColor(1, "#7A090D".toColorInt())
                textPaint.color = textColor

                val initialText = getString(2)
                if (!initialText.isNullOrEmpty()) {
                    currentText = initialText
                    targetText = initialText
                    oldText = initialText
                }
            }
        }
    }

    fun setTextSizeSp(sp: Float) {
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            resources.displayMetrics
        )
        requestLayout()
        invalidate()
    }

    fun setTextColorInt(colorInt: Int) {
        textPaint.color = colorInt
        invalidate()
    }

    /**
     * Set new text with optional slide animation.
     */
    fun setText(newText: String, animate: Boolean = true) {
        if (newText == targetText && animProgress == 1f) return

        // If first time, or animate disabled, or different length: switch immediately
        if (currentText.isEmpty() || !animate || currentText.length != newText.length) {
            animator?.cancel()
            currentText = newText
            oldText = newText
            targetText = newText
            changingIndices = emptySet()
            animProgress = 1f
            requestLayout()
            invalidate()
            return
        }

        oldText = if (targetText.isNotEmpty()) targetText else currentText
        targetText = newText

        val indices = mutableSetOf<Int>()
        for (i in newText.indices) {
            if (i < oldText.length && oldText[i] != newText[i]) {
                indices.add(i)
            }
        }

        if (indices.isEmpty()) {
            currentText = newText
            animProgress = 1f
            invalidate()
            return
        }

        changingIndices = indices
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { va ->
                animProgress = va.animatedValue as Float
                invalidate()
            }
            doOnEnd {
                currentText = targetText
                animProgress = 1f
                changingIndices = emptySet()
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fontMetrics = textPaint.fontMetrics
        val textHeight = abs(fontMetrics.ascent) + fontMetrics.descent

        val maxDigitWidth = (0..9).maxOf { textPaint.measureText(it.toString()) }
        val charCount = maxOf(targetText.length, currentText.length, 2)
        val desiredWidth = (maxDigitWidth * charCount + paddingLeft + paddingRight).toInt()
        val desiredHeight = (textHeight + paddingTop + paddingBottom).toInt()

        val resolvedWidth = resolveSize(desiredWidth, widthMeasureSpec)
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    @SuppressLint("UseKtx")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val displayText = targetText.ifEmpty { currentText }
        if (displayText.isEmpty()) return

        canvas.save()
        // Clip so sliding digits don't overflow outside view bounds
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())

        val fontMetrics = textPaint.fontMetrics
        val centerY = height / 2f
        val baseLineY = centerY - (fontMetrics.descent + fontMetrics.ascent) / 2f
        val moveDistance = (abs(fontMetrics.ascent) + fontMetrics.descent) * 0.95f

        // Tabular digit width: calculate max width of digits 0-9 for steady layout
        val maxDigitWidth = (0..9).maxOf { textPaint.measureText(it.toString()) }
        val digitWidth = maxDigitWidth + 1f
        val totalWidth = displayText.length * digitWidth
        val startX = (width - totalWidth) / 2f

        for (i in displayText.indices) {
            val colLeft = startX + i * digitWidth

            if (i in changingIndices && animProgress < 1f) {
                // 1. Old digit slides downward and fades out
                val oldChar = if (i < oldText.length) oldText[i].toString() else ""
                if (oldChar.isNotEmpty()) {
                    val oldWidth = textPaint.measureText(oldChar)
                    val oldX = colLeft + (digitWidth - oldWidth) / 2f
                    val oldY = baseLineY + (animProgress * moveDistance)
                    val oldAlpha = ((1f - animProgress).coerceIn(0f, 1f) * 255).toInt()
                    textPaint.alpha = oldAlpha
                    canvas.drawText(oldChar, oldX, oldY, textPaint)
                }

                // 2. New digit slides down from top and fades in
                val newChar = displayText[i].toString()
                val newWidth = textPaint.measureText(newChar)
                val newX = colLeft + (digitWidth - newWidth) / 2f
                val newY = baseLineY + ((animProgress - 1f) * moveDistance)
                val newAlpha = (animProgress.coerceIn(0f, 1f) * 255).toInt()
                textPaint.alpha = newAlpha
                canvas.drawText(newChar, newX, newY, textPaint)
            } else {
                // Static digit
                val ch = displayText[i].toString()
                val chWidth = textPaint.measureText(ch)
                val chX = colLeft + (digitWidth - chWidth) / 2f
                textPaint.alpha = 255
                canvas.drawText(ch, chX, baseLineY, textPaint)
            }
        }

        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}