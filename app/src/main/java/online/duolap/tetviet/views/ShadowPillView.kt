package online.duolap.tetviet.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import online.duolap.tetviet.R

class ShadowPillView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    @ColorInt
    private var pillBgColor: Int = Color.parseColor("#FFFDF9")
    @ColorInt
    private var pillStrokeColor: Int = Color.parseColor("#EFE4D6")
    private var pillStrokeWidth: Float = dpToPx(1f)
    private var pillCornerRadius: Float = -1f // -1f = tự động bo tròn nửa chiều cao (Capsule)

    @ColorInt
    private var pillShadowColor: Int = Color.parseColor("#38B88060")
    private var pillShadowRadius: Float = dpToPx(8f)
    private var pillShadowDx: Float = dpToPx(0f)
    private var pillShadowDy: Float = dpToPx(3.5f)

    var isSelectedState: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateContentColors()
                invalidate()
            }
        }

    @ColorInt
    private var selectedStartColor: Int = Color.parseColor("#B71C1C")
    @ColorInt
    private var selectedEndColor: Int = Color.parseColor("#8A000A")
    @ColorInt
    private var selectedTextColor: Int = Color.parseColor("#FFF7E6")
    @ColorInt
    private var selectedShadowColor: Int = Color.parseColor("#4D8A000A")

    private var pillTextString: String? = null
    @ColorInt
    private var pillTextColor: Int = Color.parseColor("#7A090D")
    private var pillTextSizePx: Float = spToPx(12.5f)
    private var pillFontResId: Int = R.font.arimo_bold
    @DrawableRes
    private var pillIconResId: Int = 0
    @ColorInt
    private var pillIconTint: Int = Color.parseColor("#7A090D")
    private var pillIconSizePx: Int = dpToPx(18f).toInt()
    private var pillIconPaddingPx: Int = dpToPx(6f).toInt()

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val pillRect = RectF()
    private val strokeRect = RectF()
    private var selectedGradient: LinearGradient? = null
    private var lastWidth: Int = -1

    private var contentContainer: LinearLayout? = null
    val ivIcon: ImageView by lazy { ImageView(context) }
    val tvText: TextView by lazy { TextView(context) }

    init {
        // Cần thiết để hàm Paint.setShadowLayer vẽ đổ bóng mờ Gaussian chuẩn Skia trên mọi phiên bản Android
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true

        attrs?.let { attributeSet ->
            val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.ShadowPillView)
            try {
                pillBgColor = typedArray.getColor(R.styleable.ShadowPillView_pillBgColor, pillBgColor)
                pillStrokeColor = typedArray.getColor(R.styleable.ShadowPillView_pillStrokeColor, pillStrokeColor)
                pillStrokeWidth = typedArray.getDimension(R.styleable.ShadowPillView_pillStrokeWidth, pillStrokeWidth)
                pillCornerRadius =
                    typedArray.getDimension(R.styleable.ShadowPillView_pillCornerRadius, pillCornerRadius)

                pillShadowColor = typedArray.getColor(R.styleable.ShadowPillView_pillShadowColor, pillShadowColor)
                pillShadowRadius =
                    typedArray.getDimension(R.styleable.ShadowPillView_pillShadowRadius, pillShadowRadius)
                pillShadowDx = typedArray.getDimension(R.styleable.ShadowPillView_pillShadowDx, pillShadowDx)
                pillShadowDy = typedArray.getDimension(R.styleable.ShadowPillView_pillShadowDy, pillShadowDy)

                pillTextString = typedArray.getString(R.styleable.ShadowPillView_pillText)
                pillTextColor = typedArray.getColor(R.styleable.ShadowPillView_pillTextColor, pillTextColor)
                pillTextSizePx = typedArray.getDimension(R.styleable.ShadowPillView_pillTextSize, pillTextSizePx)
                pillFontResId = typedArray.getResourceId(R.styleable.ShadowPillView_pillFont, pillFontResId)
                pillIconResId = typedArray.getResourceId(R.styleable.ShadowPillView_pillIcon, 0)
                pillIconTint = typedArray.getColor(R.styleable.ShadowPillView_pillIconTint, pillIconTint)
                pillIconSizePx =
                    typedArray.getDimensionPixelSize(R.styleable.ShadowPillView_pillIconSize, pillIconSizePx)
                pillIconPaddingPx =
                    typedArray.getDimensionPixelSize(R.styleable.ShadowPillView_pillIconPadding, pillIconPaddingPx)

                isSelectedState = typedArray.getBoolean(R.styleable.ShadowPillView_pillIsSelected, isSelectedState)
                selectedStartColor =
                    typedArray.getColor(R.styleable.ShadowPillView_pillSelectedStartColor, selectedStartColor)
                selectedEndColor =
                    typedArray.getColor(R.styleable.ShadowPillView_pillSelectedEndColor, selectedEndColor)
                selectedTextColor =
                    typedArray.getColor(R.styleable.ShadowPillView_pillSelectedTextColor, selectedTextColor)
                selectedShadowColor =
                    typedArray.getColor(R.styleable.ShadowPillView_pillSelectedShadowColor, selectedShadowColor)
            } finally {
                typedArray.recycle()
            }
        }

        setupBuiltInContent()
        updatePaddings()
    }

    private fun setupBuiltInContent() {
        if (!pillTextString.isNullOrEmpty() || pillIconResId != 0) {
            contentContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.CENTER)
            }

            if (pillIconResId != 0) {
                ivIcon.layoutParams = LinearLayout.LayoutParams(pillIconSizePx, pillIconSizePx).apply {
                    marginEnd = pillIconPaddingPx
                }
                ivIcon.setImageResource(pillIconResId)
                if (pillIconTint != 0 && !isSelectedState) {
                    ivIcon.imageTintList = ColorStateList.valueOf(pillIconTint)
                }
                contentContainer?.addView(ivIcon)
            }

            if (!pillTextString.isNullOrEmpty()) {
                tvText.text = pillTextString
                tvText.setTextSize(TypedValue.COMPLEX_UNIT_PX, pillTextSizePx)
                tvText.includeFontPadding = false
                try {
                    tvText.typeface = ResourcesCompat.getFont(context, pillFontResId)
                } catch (_: Exception) {
                }
                contentContainer?.addView(tvText)
            }

            addView(contentContainer)
            updateContentColors()
        }
    }

    private fun updatePaddings() {
        // Dự trù lề ngoài cho vệt bóng đổ không bị cắn xén
        val padLeft = (pillShadowRadius + (-pillShadowDx).coerceAtLeast(0f) + dpToPx(8f)).toInt()
        val padTop = (pillShadowRadius + (-pillShadowDy).coerceAtLeast(0f) + dpToPx(4f)).toInt()
        val padRight = (pillShadowRadius + pillShadowDx.coerceAtLeast(0f) + dpToPx(8f)).toInt()
        val padBottom = (pillShadowRadius + pillShadowDy.coerceAtLeast(0f) + dpToPx(5f)).toInt()

        super.setPadding(padLeft, padTop, padRight, padBottom)
    }

    private fun updateContentColors() {
        if (isSelectedState) {
            tvText.setTextColor(selectedTextColor)
            ivIcon.imageTintList = null // Giữ nguyên màu gốc icon hoặc dùng tone sáng
        } else {
            tvText.setTextColor(pillTextColor)
            if (pillIconTint != 0) {
                ivIcon.imageTintList = ColorStateList.valueOf(pillIconTint)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        lastWidth = w
        selectedGradient = null

        // Tạo hiệu ứng Ripple bo theo hình capsule của viên thuốc
        setupRippleForeground()
    }

    private fun setupRippleForeground() {
        val padLeft = pillShadowRadius + (-pillShadowDx).coerceAtLeast(0f)
        val padTop = pillShadowRadius + (-pillShadowDy).coerceAtLeast(0f)
        val padRight = pillShadowRadius + pillShadowDx.coerceAtLeast(0f)
        val padBottom = pillShadowRadius + pillShadowDy.coerceAtLeast(0f)

        val pillH = (height - (padTop + padBottom)).coerceAtLeast(1f)
        val radius = if (pillCornerRadius >= 0f) pillCornerRadius else (pillH / 2f)

        val outerRadii = floatArrayOf(
            radius, radius,
            radius, radius,
            radius, radius,
            radius, radius
        )
        val mask = ShapeDrawable(RoundRectShape(outerRadii, null, null))
        val rippleColor = if (isSelectedState) Color.parseColor("#33FFFFFF") else Color.parseColor("#207A090D")

        val ripple = RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
        foreground = ripple
    }

    override fun onDraw(canvas: Canvas) {
        val padLeft = pillShadowRadius + (-pillShadowDx).coerceAtLeast(0f)
        val padTop = pillShadowRadius + (-pillShadowDy).coerceAtLeast(0f)
        val padRight = pillShadowRadius + pillShadowDx.coerceAtLeast(0f)
        val padBottom = pillShadowRadius + pillShadowDy.coerceAtLeast(0f)

        val left = padLeft
        val top = padTop
        val right = width.toFloat() - padRight
        val bottom = height.toFloat() - padBottom

        if (right <= left || bottom <= top) {
            super.onDraw(canvas)
            return
        }

        pillRect.set(left, top, right, bottom)
        val radius = if (pillCornerRadius >= 0f) pillCornerRadius else (pillRect.height() / 2f)

        // 1. Vẽ bóng đổ ấm áp mềm mịn (Warm Diffused Glow)
        val currentShadowColor = if (isSelectedState) selectedShadowColor else pillShadowColor
        shadowPaint.color = if (isSelectedState) selectedStartColor else pillBgColor
        shadowPaint.setShadowLayer(pillShadowRadius, pillShadowDx, pillShadowDy, currentShadowColor)
        canvas.drawRoundRect(pillRect, radius, radius, shadowPaint)

        // 2. Vẽ bề mặt viên thuốc (Surface fill)
        if (isSelectedState) {
            if (selectedGradient == null) {
                selectedGradient = LinearGradient(
                    left, top, right, top,
                    selectedStartColor, selectedEndColor,
                    Shader.TileMode.CLAMP
                )
            }
            bgPaint.shader = selectedGradient
        } else {
            bgPaint.shader = null
            bgPaint.color = pillBgColor
        }
        canvas.drawRoundRect(pillRect, radius, radius, bgPaint)

        // 3. Vẽ đường viền tinh tế
        if (pillStrokeWidth > 0f && !isSelectedState) {
            val halfStroke = pillStrokeWidth / 2f
            strokeRect.set(left + halfStroke, top + halfStroke, right - halfStroke, bottom - halfStroke)
            val strokeRadius = (radius - halfStroke).coerceAtLeast(0f)
            strokePaint.strokeWidth = pillStrokeWidth
            strokePaint.color = pillStrokeColor
            canvas.drawRoundRect(strokeRect, strokeRadius, strokeRadius, strokePaint)
        }

        super.onDraw(canvas)
    }

    // --- Các hàm tiện ích hỗ trợ code Kotlin ---
    var text: CharSequence
        get() = tvText.text
        set(value) {
            pillTextString = value.toString()
            tvText.text = value
            if (contentContainer == null) {
                setupBuiltInContent()
            }
        }

    fun setIcon(@DrawableRes iconResId: Int) {
        pillIconResId = iconResId
        ivIcon.setImageResource(iconResId)
        if (contentContainer == null) {
            setupBuiltInContent()
        }
    }

    fun setShadowProperties(
        @ColorInt color: Int = pillShadowColor,
        radiusDp: Float = 8f,
        dxDp: Float = 0f,
        dyDp: Float = 3.5f
    ) {
        pillShadowColor = color
        pillShadowRadius = dpToPx(radiusDp)
        pillShadowDx = dpToPx(dxDp)
        pillShadowDy = dpToPx(dyDp)
        updatePaddings()
        invalidate()
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun spToPx(sp: Float): Float {
        return sp * resources.displayMetrics.scaledDensity
    }
}
