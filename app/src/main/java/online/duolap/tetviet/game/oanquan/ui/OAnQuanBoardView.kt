package online.duolap.tetviet.game.oanquan.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.duolap.tetviet.game.oanquan.engine.MoveDirection
import online.duolap.tetviet.game.oanquan.engine.OAnQuanBoardState
import online.duolap.tetviet.game.oanquan.engine.OAnQuanStep
import online.duolap.tetviet.game.oanquan.engine.PlayerSide
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class OAnQuanBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var boardState: OAnQuanBoardState = OAnQuanBoardState()
    private var activePlayer: PlayerSide = PlayerSide.PLAYER_1
    private var isInteractive: Boolean = true

    // Ô đang được người chơi chọn để đi
    private var selectedPitIndex: Int = -1

    // Listener khi người chơi chọn xong ô và hướng đi
    var onMoveSelectedListener: ((pitIndex: Int, direction: MoveDirection) -> Unit)? = null
    var onStepAnimatedListener: ((OAnQuanStep) -> Unit)? = null

    // Vùng chữ nhật của 12 ô cờ
    private val pitBounds = Array(12) { RectF() }
    private val leftArrowBounds = RectF()
    private val rightArrowBounds = RectF()

    // Paints
    private val boardWoodPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pitFloorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pitBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pitHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pebblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val quanPiecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Animation state
    private var animatingPitIndex: Int = -1
    private var animFloatingText: String? = null
    private var animFloatingAlpha: Float = 0f
    private var animPulseScale: Float = 1.0f
    private var animationJob: Job? = null

    init {
        boardWoodPaint.color = Color.parseColor("#4A2511")
        boardBorderPaint.style = Paint.Style.STROKE
        boardBorderPaint.strokeWidth = 6f
        boardBorderPaint.color = Color.parseColor("#D4AF37")

        pitFloorPaint.color = Color.parseColor("#38190B")
        pitBorderPaint.style = Paint.Style.STROKE
        pitBorderPaint.strokeWidth = 3f
        pitBorderPaint.color = Color.parseColor("#8B5E3C")

        pitHighlightPaint.style = Paint.Style.STROKE
        pitHighlightPaint.strokeWidth = 5f
        pitHighlightPaint.color = Color.parseColor("#FFD700")

        pebblePaint.color = Color.parseColor("#E6D7C3")
        quanPiecePaint.color = Color.parseColor("#FFB703")

        textBadgeBgPaint.color = Color.parseColor("#F5E6CA")
        textBadgePaint.color = Color.parseColor("#7A090D")
        textBadgePaint.textAlign = Paint.Align.CENTER
        textBadgePaint.isFakeBoldText = true

        arrowBgPaint.color = Color.parseColor("#D32F2F")
        arrowTextPaint.color = Color.WHITE
        arrowTextPaint.textAlign = Paint.Align.CENTER
        arrowTextPaint.isFakeBoldText = true
    }

    fun setBoardState(state: OAnQuanBoardState, localPlayer: PlayerSide = PlayerSide.PLAYER_1) {
        this.boardState = state
        this.activePlayer = state.currentTurn
        this.selectedPitIndex = -1
        invalidate()
    }

    fun setInteractive(interactive: Boolean) {
        this.isInteractive = interactive
        if (!interactive) selectedPitIndex = -1
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculatePitLayout(w.toFloat(), h.toFloat())
    }

    private fun calculatePitLayout(w: Float, h: Float) {
        val padding = 16f
        val boardLeft = padding
        val boardTop = padding
        val boardRight = w - padding
        val boardBottom = h - padding
        val boardWidth = boardRight - boardLeft
        val boardHeight = boardBottom - boardTop

        // Bàn cờ chia theo chiều ngang:
        // [Quan Tả 11 (width ~ 1.2 unit)] [5 Ô Dân (mỗi ô 1 unit)] [Quan Hữu 5 (width ~ 1.2 unit)]
        // Tổng unit = 1.2 + 5.0 + 1.2 = 7.4 units
        val unitWidth = boardWidth / 7.4f
        val quanWidth = unitWidth * 1.2f
        val danWidth = unitWidth
        val danHeight = (boardHeight - 20f) / 2f

        val leftQuanRight = boardLeft + quanWidth
        val rightQuanLeft = boardRight - quanWidth

        // 1. Ô Quan Tả (Index 11 - Semicircle bên trái)
        pitBounds[11].set(boardLeft, boardTop + 10f, leftQuanRight, boardBottom - 10f)

        // 2. Ô Quan Hữu (Index 5 - Semicircle bên phải)
        pitBounds[5].set(rightQuanLeft, boardTop + 10f, boardRight, boardBottom - 10f)

        // 3. 5 Ô Dân Hàng Dưới (Player 1: 0, 1, 2, 3, 4 từ trái qua phải)
        val bottomRowTop = boardTop + danHeight + 10f
        val bottomRowBottom = boardBottom - 10f
        for (i in 0..4) {
            val left = leftQuanRight + i * danWidth + 4f
            val right = left + danWidth - 8f
            pitBounds[i].set(left, bottomRowTop + 4f, right, bottomRowBottom - 4f)
        }

        // 4. 5 Ô Dân Hàng Trên (Player 2: 10, 9, 8, 7, 6 từ trái qua phải)
        val topRowTop = boardTop + 10f
        val topRowBottom = topRowTop + danHeight - 4f
        for (col in 0..4) {
            val pitIndex = 10 - col // col 0 -> 10, col 1 -> 9, ..., col 4 -> 6
            val left = leftQuanRight + col * danWidth + 4f
            val right = left + danWidth - 8f
            pitBounds[pitIndex].set(left, topRowTop + 4f, right, topRowBottom - 4f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. Vẽ nền gỗ bàn cờ bo tròn tổng thể
        val boardRect = RectF(12f, 12f, w - 12f, h - 12f)
        canvas.drawRoundRect(boardRect, 28f, 28f, boardWoodPaint)
        canvas.drawRoundRect(boardRect, 28f, 28f, boardBorderPaint)

        // 2. Vẽ 2 Ô Quan (Index 11 bên trái, Index 5 bên phải)
        drawQuanPit(canvas, 11, pitBounds[11], isLeft = true)
        drawQuanPit(canvas, 5, pitBounds[5], isLeft = false)

        // 3. Vẽ 10 Ô Dân
        for (i in 0..4) {
            drawDanPit(canvas, i, pitBounds[i])
        }
        for (i in 6..10) {
            drawDanPit(canvas, i, pitBounds[i])
        }

        // 4. Nếu có ô đang chọn -> vẽ 2 nút mũi tên chọn hướng đi
        if (selectedPitIndex in 0..11) {
            drawDirectionArrows(canvas, selectedPitIndex)
        }

        // 5. Hiệu ứng chữ nổi (Floating Text) khi ăn quân hoặc chững quan
        if (animFloatingText != null && animatingPitIndex in 0..11) {
            drawFloatingBadge(canvas, animatingPitIndex, animFloatingText!!, animFloatingAlpha)
        }
    }

    private fun drawDanPit(canvas: Canvas, index: Int, rect: RectF) {
        val isSelected = (index == selectedPitIndex)
        val isSelectable = isInteractive && boardState.currentTurn.ownsPit(index) && boardState.pits[index] > 0
        val isAnimating = (index == animatingPitIndex)

        // Nền ô dân
        canvas.drawRoundRect(rect, 16f, 16f, pitFloorPaint)
        canvas.drawRoundRect(rect, 16f, 16f, pitBorderPaint)

        // Hiệu ứng phát sáng viền khi có thể chọn
        if (isSelected) {
            pitHighlightPaint.strokeWidth = 6f
            pitHighlightPaint.color = Color.parseColor("#FFD700")
            canvas.drawRoundRect(rect, 16f, 16f, pitHighlightPaint)
        } else if (isSelectable) {
            pitHighlightPaint.strokeWidth = 3f
            pitHighlightPaint.color = Color.parseColor("#80FFD700")
            canvas.drawRoundRect(rect, 16f, 16f, pitHighlightPaint)
        }

        val stonesCount = boardState.pits[index]
        val cx = rect.centerX()
        val cy = rect.centerY()

        // Vẽ các viên sỏi nhỏ rải tự nhiên
        val pebbleRadius = min(rect.width(), rect.height()) * 0.08f
        val displayStones = min(stonesCount, 8)
        for (s in 0 until displayStones) {
            val angle = s * (2 * Math.PI / displayStones) + (index * 0.5)
            val dist = pebbleRadius * 1.6f
            val px = cx + (dist * cos(angle)).toFloat()
            val py = cy + (dist * sin(angle)).toFloat()
            canvas.drawCircle(px, py, pebbleRadius, pebblePaint)
        }

        // Huy hiệu số quân
        val badgeRadius = min(rect.width(), rect.height()) * 0.20f * (if (isAnimating) animPulseScale else 1.0f)
        canvas.drawCircle(cx, cy, badgeRadius, textBadgeBgPaint)
        textBadgePaint.textSize = badgeRadius * 1.3f
        val textY = cy - (textBadgePaint.descent() + textBadgePaint.ascent()) / 2f
        canvas.drawText(stonesCount.toString(), cx, textY, textBadgePaint)
    }

    private fun drawQuanPit(canvas: Canvas, index: Int, rect: RectF, isLeft: Boolean) {
        val isAnimating = (index == animatingPitIndex)
        val path = Path()

        if (isLeft) {
            // Semicircle quay sang trái
            path.moveTo(rect.right, rect.top)
            path.arcTo(rect, 270f, -180f, false)
            path.lineTo(rect.right, rect.bottom)
            path.close()
        } else {
            // Semicircle quay sang phải
            path.moveTo(rect.left, rect.top)
            path.arcTo(rect, 270f, 180f, false)
            path.lineTo(rect.left, rect.bottom)
            path.close()
        }

        canvas.drawPath(path, pitFloorPaint)
        canvas.drawPath(path, pitBorderPaint)

        val cx = rect.centerX()
        val cy = rect.centerY()
        val hasQuan = boardState.hasQuanPieceAt(index)
        val bonusDan = boardState.pits[index]

        if (hasQuan) {
            // Vẽ quân Quan lớn màu vàng kim
            val quanSize = min(rect.width(), rect.height()) * 0.32f * (if (isAnimating) animPulseScale else 1.0f)
            quanPiecePaint.shader = RadialGradient(
                cx, cy - quanSize * 0.2f, quanSize,
                intArrayOf(Color.parseColor("#FFF3B0"), Color.parseColor("#E09F3E"), Color.parseColor("#9E2A2B")),
                null, Shader.TileMode.CLAMP
            )
            val quanRect = RectF(cx - quanSize, cy - quanSize, cx + quanSize, cy + quanSize)
            canvas.drawRoundRect(quanRect, 18f, 18f, quanPiecePaint)

            // Chữ "QUAN" nhỏ trên mặt quân
            arrowTextPaint.textSize = quanSize * 0.55f
            val textY = cy - (arrowTextPaint.descent() + arrowTextPaint.ascent()) / 2f
            canvas.drawText("QUAN", cx, textY, arrowTextPaint)

            // Nếu có sỏi dân tích lũy thêm trong ô quan
            if (bonusDan > 0) {
                val badgeRadius = quanSize * 0.45f
                val badgeX = cx + quanSize * 0.7f
                val badgeY = cy - quanSize * 0.7f
                canvas.drawCircle(badgeX, badgeY, badgeRadius, textBadgeBgPaint)
                textBadgePaint.textSize = badgeRadius * 1.2f
                val bY = badgeY - (textBadgePaint.descent() + textBadgePaint.ascent()) / 2f
                canvas.drawText("+$bonusDan", badgeX, bY, textBadgePaint)
            }
        } else if (bonusDan > 0) {
            // Quan đã bị ăn nhưng có sỏi dân rơi vào
            val badgeRadius = min(rect.width(), rect.height()) * 0.24f
            canvas.drawCircle(cx, cy, badgeRadius, textBadgeBgPaint)
            textBadgePaint.textSize = badgeRadius * 1.2f
            val textY = cy - (textBadgePaint.descent() + textBadgePaint.ascent()) / 2f
            canvas.drawText(bonusDan.toString(), cx, textY, textBadgePaint)
        }
    }

    private fun drawDirectionArrows(canvas: Canvas, pitIndex: Int) {
        val rect = pitBounds[pitIndex]
        val arrowWidth = rect.width() * 0.42f
        val arrowHeight = 36f

        // Đặt 2 mũi tên ngay trên/dưới ô tùy thuộc ô ở hàng trên hay hàng dưới
        val isBottomRow = pitIndex in 0..4
        val arrowY = if (isBottomRow) rect.top - arrowHeight - 6f else rect.bottom + 6f

        leftArrowBounds.set(rect.left, arrowY, rect.left + arrowWidth, arrowY + arrowHeight)
        rightArrowBounds.set(rect.right - arrowWidth, arrowY, rect.right, arrowY + arrowHeight)

        // Nút mũi tên Trái
        canvas.drawRoundRect(leftArrowBounds, 12f, 12f, arrowBgPaint)
        arrowTextPaint.textSize = 22f
        var textY = leftArrowBounds.centerY() - (arrowTextPaint.descent() + arrowTextPaint.ascent()) / 2f
        canvas.drawText("⮜", leftArrowBounds.centerX(), textY, arrowTextPaint)

        // Nút mũi tên Phải
        canvas.drawRoundRect(rightArrowBounds, 12f, 12f, arrowBgPaint)
        textY = rightArrowBounds.centerY() - (arrowTextPaint.descent() + arrowTextPaint.ascent()) / 2f
        canvas.drawText("⮞", rightArrowBounds.centerX(), textY, arrowTextPaint)
    }

    private fun drawFloatingBadge(canvas: Canvas, pitIndex: Int, text: String, alpha: Float) {
        val rect = pitBounds[pitIndex]
        val cx = rect.centerX()
        val cy = rect.top - 20f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B71C1C")
            this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        }

        val textWidth = textPaint.measureText(text)
        val badgeRect = RectF(cx - textWidth / 2f - 16f, cy - 24f, cx + textWidth / 2f + 16f, cy + 20f)
        canvas.drawRoundRect(badgeRect, 14f, 14f, bgPaint)
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, cx, textY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractive || event.action != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)

        val x = event.x
        val y = event.y

        // 1. Kiểm tra xem người dùng có bấm vào 1 trong 2 mũi tên chọn hướng không
        if (selectedPitIndex != -1) {
            if (leftArrowBounds.contains(x, y)) {
                // Nhấn mũi tên Trái:
                // Đối với Bottom (0..4): Đi sang trái là CLOCKWISE (giảm index)
                // Đối với Top (6..10): Đi sang trái (từ góc nhìn P1) là tăng index (10 -> 9 -> 8) tức COUNTER_CLOCKWISE
                val direction = if (selectedPitIndex in 0..4) MoveDirection.CLOCKWISE else MoveDirection.COUNTER_CLOCKWISE
                val pit = selectedPitIndex
                selectedPitIndex = -1
                invalidate()
                onMoveSelectedListener?.invoke(pit, direction)
                return true
            }

            if (rightArrowBounds.contains(x, y)) {
                // Nhấn mũi tên Phải:
                // Đối với Bottom (0..4): Đi sang phải là COUNTER_CLOCKWISE (tăng index)
                // Đối với Top (6..10): Đi sang phải là CLOCKWISE (giảm index)
                val direction = if (selectedPitIndex in 0..4) MoveDirection.COUNTER_CLOCKWISE else MoveDirection.CLOCKWISE
                val pit = selectedPitIndex
                selectedPitIndex = -1
                invalidate()
                onMoveSelectedListener?.invoke(pit, direction)
                return true
            }
        }

        // 2. Kiểm tra xem có bấm vào một ô dân hợp lệ không
        for (i in 0..11) {
            if (pitBounds[i].contains(x, y)) {
                if (boardState.currentTurn.ownsPit(i) && boardState.pits[i] > 0) {
                    selectedPitIndex = if (selectedPitIndex == i) -1 else i
                    invalidate()
                    return true
                }
            }
        }

        // Bấm ra ngoài ô hợp lệ -> bỏ chọn
        if (selectedPitIndex != -1) {
            selectedPitIndex = -1
            invalidate()
            return true
        }

        return super.onTouchEvent(event)
    }

    /**
     * Chạy animation mượt mà cho các bước rải quân, ăn quân, chững quan.
     */
    fun animateSteps(
        steps: List<OAnQuanStep>,
        coroutineScope: CoroutineScope,
        onComplete: () -> Unit
    ) {
        animationJob?.cancel()
        setInteractive(false)

        animationJob = coroutineScope.launch {
            for (step in steps) {
                withContext(Dispatchers.Main) {
                    onStepAnimatedListener?.invoke(step)
                }

                when (step) {
                    is OAnQuanStep.PickUp -> {
                        animatingPitIndex = step.pitIndex
                        pulsePit()
                        delay(220)
                    }
                    is OAnQuanStep.DropOne -> {
                        animatingPitIndex = step.pitIndex
                        boardState.pits[step.pitIndex] = step.currentPitCount
                        pulsePit()
                        delay(170)
                    }
                    is OAnQuanStep.ChungQuan -> {
                        animatingPitIndex = step.quanPitIndex
                        showFloating("Chững Quan!", 650)
                        delay(650)
                    }
                    is OAnQuanStep.Capture -> {
                        animatingPitIndex = step.targetPitIndex
                        boardState.pits[step.targetPitIndex] = 0
                        if (step.capturedQuan) {
                            boardState.removeQuanPieceAt(step.targetPitIndex)
                        }
                        if (step.player == PlayerSide.PLAYER_1) {
                            boardState.player1Score = step.newPlayerScore
                        } else {
                            boardState.player2Score = step.newPlayerScore
                        }
                        val captureText = if (step.capturedQuan) "Ăn Quan! +${step.pointsAwarded}" else "+${step.pointsAwarded}"
                        showFloating(captureText, 700)
                        delay(700)
                    }
                    is OAnQuanStep.ChainGap -> {
                        animatingPitIndex = step.emptyPitIndex
                        delay(200)
                    }
                    is OAnQuanStep.ReliefStones -> {
                        for (pit in step.player.ownedPits) {
                            boardState.pits[pit] = 1
                        }
                        if (step.player == PlayerSide.PLAYER_1) {
                            boardState.player1Score = step.currentScore
                        } else {
                            boardState.player2Score = step.currentScore
                        }
                        invalidate()
                        delay(500)
                    }
                    is OAnQuanStep.GameOver -> {
                        boardState.isGameOver = true
                        boardState.player1Score = step.p1Final
                        boardState.player2Score = step.p2Final
                        for (p in 0..11) {
                            if (!OAnQuanBoardState.isQuanPit(p)) boardState.pits[p] = 0
                        }
                        invalidate()
                        delay(400)
                    }
                    else -> {
                        delay(100)
                    }
                }
                withContext(Dispatchers.Main) {
                    invalidate()
                }
            }

            animatingPitIndex = -1
            animFloatingText = null
            withContext(Dispatchers.Main) {
                invalidate()
                setInteractive(!boardState.isGameOver)
                onComplete()
            }
        }
    }

    private fun pulsePit() {
        val animator = ValueAnimator.ofFloat(1.0f, 1.25f, 1.0f).apply {
            duration = 160
            interpolator = OvershootInterpolator()
            addUpdateListener {
                animPulseScale = it.animatedValue as Float
                invalidate()
            }
        }
        animator.start()
    }

    private fun showFloating(text: String, durationMs: Long) {
        animFloatingText = text
        val animator = ValueAnimator.ofFloat(1.0f, 0.0f).apply {
            duration = durationMs
            addUpdateListener {
                animFloatingAlpha = it.animatedValue as Float
                invalidate()
            }
        }
        animator.start()
    }
}

