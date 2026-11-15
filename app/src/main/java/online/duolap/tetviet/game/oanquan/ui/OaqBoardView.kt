package online.duolap.tetviet.game.oanquan.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import online.duolap.tetviet.R
import online.duolap.tetviet.game.oanquan.engine.MoveDirection
import online.duolap.tetviet.game.oanquan.engine.OAnQuanBoardState
import online.duolap.tetviet.game.oanquan.engine.OAnQuanStep
import online.duolap.tetviet.game.oanquan.engine.PlayerSide
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bàn cờ Ô Ăn Quan v2: bàn gỗ khung vàng, ô khoét lõm, từng viên sỏi được vẽ riêng.
 *
 * View giữ một bản sao trạng thái riêng ([display]) để diễn lại từng bước của nước đi:
 * bốc sỏi lên tay, tay di chuyển rải từng viên, sỏi bị ăn bay về phía người ăn.
 * Activity truyền trạng thái *trước* nước đi vào [animateSteps]; sau khi diễn xong thì gọi
 * [setBoardState] với trạng thái thật để đồng bộ lại.
 * Bản cũ vẫn giữ ở [OAnQuanBoardView].
 */
class OaqBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onMoveSelected: ((pitIndex: Int, direction: MoveDirection) -> Unit)? = null

    /** Gọi khi bắt đầu diễn mỗi bước, kèm tâm ô liên quan (toạ độ trong view) để Activity vẽ hiệu ứng. */
    var onStep: ((step: OAnQuanStep, pitCenter: PointF) -> Unit)? = null

    /** Gọi mỗi khi một viên sỏi rơi xuống ô (để rung nhẹ). */
    var onStoneLanded: (() -> Unit)? = null

    private var display = OAnQuanBoardState()
    private var interactive = true
    private var selectedPit = -1
    private var hintDirection: MoveDirection? = null
    private var hintUntilMs = 0L
    private var animationJob: Job? = null
    private var dealing = false

    private val density = resources.displayMetrics.density

    // ---------- Hình học ----------
    private val frameRect = RectF()
    private val lacquerRect = RectF()
    private val woodRect = RectF()
    private val pitRects = Array(12) { RectF() }
    private val quanPaths = arrayOf(Path(), Path())
    private val leftArrow = PointF()
    private val rightArrow = PointF()
    private var arrowRadius = 0f
    private var stoneRadius = 0f
    private var quanRadius = 0f
    private val grainPath = Path()
    private val quanCenters = arrayOf(PointF(), PointF())
    private val slotOut = FloatArray(2)

    // Vị trí cố định (đã chuẩn hoá -1..1) của từng viên sỏi trong mỗi ô, để sỏi không nhảy chỗ khi vẽ lại
    private val stoneSlots = Array(12) { FloatArray(MAX_STONES_SHOWN * 2) }
    private val pitShaders = arrayOfNulls<Shader>(12)
    private val quanShaders = arrayOfNulls<Shader>(2)
    private var frameShader: Shader? = null
    private var lacquerShader: Shader? = null
    private var woodShader: Shader? = null

    // ---------- Bút vẽ ----------
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.arimo_bold)
    }

    // ---------- Hoạt cảnh ----------
    private class Flyer(
        val fromX: Float, val fromY: Float,
        val toX: Float, val toY: Float,
        val startMs: Long, val durationMs: Long,
        val arc: Float, val color: Int, val radius: Float,
        val fadeOut: Boolean,
        val onLand: (() -> Unit)?
    )

    private val flyers = ArrayList<Flyer>(64)
    private val badgePopMs = LongArray(12)
    private val shakeMs = LongArray(12)
    private val flashMs = LongArray(12)
    private val quanHidden = BooleanArray(2)

    private var handVisible = false
    private var handX = 0f
    private var handY = 0f
    private var handCount = 0
    private var handBob = 0f

    init {
        isClickable = true
        contentDescription = context.getString(R.string.oanquan_title)
        // Sỏi rải ngẫu nhiên nhưng cố định cho từng ô
        for (pit in 0 until 12) {
            val rnd = Random(pit * 131 + 17)
            val slots = stoneSlots[pit]
            var placed = 0
            var tries = 0
            while (placed < MAX_STONES_SHOWN && tries < 2000) {
                tries++
                val x = rnd.nextFloat() * 2f - 1f
                val y = rnd.nextFloat() * 2f - 1f
                if (x * x + y * y > 0.78f) continue
                // Viên đầu nằm gần giữa, các viên sau giãn ra dần; nới khoảng cách nếu thử mãi không được
                val minDist = if (tries > 900) 0.22f else 0.42f
                var ok = true
                for (k in 0 until placed) {
                    if (hypot(slots[k * 2] - x, slots[k * 2 + 1] - y) < minDist) { ok = false; break }
                }
                if (!ok) continue
                slots[placed * 2] = x
                slots[placed * 2 + 1] = y
                placed++
            }
            // Sắp theo khoảng cách tới tâm để ô ít sỏi vẫn gọn ở giữa
            val order = (0 until placed).sortedBy { slots[it * 2] * slots[it * 2] + slots[it * 2 + 1] * slots[it * 2 + 1] }
            val copy = slots.copyOf()
            order.forEachIndexed { i, k -> slots[i * 2] = copy[k * 2]; slots[i * 2 + 1] = copy[k * 2 + 1] }
        }
    }

    // =====================================================================
    // API cho Activity
    // =====================================================================

    fun setBoardState(state: OAnQuanBoardState) {
        display = state.deepCopy()
        quanHidden.fill(false)
        selectedPit = -1
        hintDirection = null
        invalidate()
    }

    fun setInteractive(enabled: Boolean) {
        interactive = enabled
        if (!enabled) {
            selectedPit = -1
            hintDirection = null
        }
        invalidate()
    }

    /** Huỷ hoạt cảnh đang chạy (ví dụ khi đi lại hoặc bắt đầu ván mới). */
    fun cancelAnimation() {
        animationJob?.cancel()
        animationJob = null
        flyers.clear()
        handVisible = false
        dealing = false
        invalidate()
    }

    fun pitCenter(index: Int, out: PointF) {
        val r = pitRects[index]
        out.set(r.centerX(), r.centerY())
    }

    /** Làm sáng ô và mũi tên gợi ý trong vài giây. */
    fun showHint(pit: Int, direction: MoveDirection) {
        if (!display.currentTurn.ownsPit(pit)) return
        selectedPit = pit
        hintDirection = direction
        hintUntilMs = SystemClock.uptimeMillis() + HINT_MS
        invalidate()
    }

    /** Bàn trống (chưa có sỏi, chưa có Quan) để chờ hoạt cảnh mở ván. */
    fun clearForDeal(state: OAnQuanBoardState) {
        cancelAnimation()
        display = state.deepCopy()
        for (i in 0 until 12) display.pits[i] = 0
        quanHidden.fill(true)
        selectedPit = -1
        hintDirection = null
        interactive = false
        dealing = true
        invalidate()
    }

    /** Hoạt cảnh mở ván: hai quân Quan rơi xuống trước, rồi sỏi rải lần lượt vào 10 ô dân. */
    fun playDealIn(state: OAnQuanBoardState, scope: CoroutineScope, onDone: () -> Unit) {
        val target = state.pits.copyOf()
        clearForDeal(state)

        animationJob = scope.launch {
            delay(120)
            for ((slot, pit) in intArrayOf(OAnQuanBoardState.QUAN_TA_INDEX, OAnQuanBoardState.QUAN_HUU_INDEX).withIndex()) {
                val r = pitRects[pit]
                launchFlyer(r.centerX(), -quanRadius * 2f, r.centerX(), r.centerY(), 360L, 0f,
                    quanColor(pit), quanRadius, fadeOut = false) {
                    quanHidden[slot] = false
                    shakeMs[pit] = SystemClock.uptimeMillis()
                    onStoneLanded?.invoke()
                }
                delay(160)
            }
            delay(260)
            val maxRound = target.maxOrNull() ?: 0
            for (round in 0 until maxRound) {
                for (pit in DEAL_ORDER) {
                    if (target[pit] <= round) continue
                    val r = pitRects[pit]
                    val fromY = if (pit in 0..4) height + stoneRadius * 2f else -stoneRadius * 2f
                    launchFlyer(r.centerX(), fromY, r.centerX(), r.centerY(), 300L, 0f,
                        stoneColor(pit, round), stoneRadius, fadeOut = false) {
                        display.pits[pit]++
                        badgePopMs[pit] = SystemClock.uptimeMillis()
                    }
                    delay(22)
                }
            }
            delay(340)
            display = state.deepCopy()
            dealing = false
            invalidate()
            onDone()
        }
    }

    /**
     * Diễn lại từng bước của một nước đi, bắt đầu từ [before] (trạng thái trước nước đi).
     */
    fun animateSteps(
        before: OAnQuanBoardState,
        steps: List<OAnQuanStep>,
        scope: CoroutineScope,
        onComplete: () -> Unit
    ) {
        cancelAnimation()
        display = before.deepCopy()
        quanHidden.fill(false)
        setInteractive(false)
        // Nước đi dài (nhiều lượt bốc liên tiếp) thì tăng tốc để không bắt người chơi chờ lâu
        val drops = steps.count { it is OAnQuanStep.DropOne }
        val speed = when {
            drops > 36 -> 0.55f
            drops > 20 -> 0.75f
            else -> 1f
        }
        val tmp = PointF()

        animationJob = scope.launch {
            for (step in steps) {
                when (step) {
                    is OAnQuanStep.PickUp -> {
                        pitCenter(step.pitIndex, tmp)
                        onStep?.invoke(step, tmp)
                        pickUp(step.pitIndex, speed)
                    }
                    is OAnQuanStep.DropOne -> {
                        pitCenter(step.pitIndex, tmp)
                        onStep?.invoke(step, tmp)
                        dropOne(step, speed)
                    }
                    is OAnQuanStep.ChungQuan -> {
                        handVisible = false
                        pitCenter(step.quanPitIndex, tmp)
                        onStep?.invoke(step, tmp)
                        shakeMs[step.quanPitIndex] = SystemClock.uptimeMillis()
                        invalidate()
                        delay(700)
                    }
                    is OAnQuanStep.ChainGap -> {
                        pitCenter(step.emptyPitIndex, tmp)
                        onStep?.invoke(step, tmp)
                        flashMs[step.emptyPitIndex] = SystemClock.uptimeMillis()
                        invalidate()
                        delay((260 * speed).toLong())
                    }
                    is OAnQuanStep.Capture -> {
                        handVisible = false
                        pitCenter(step.targetPitIndex, tmp)
                        // Ô trống trước ô bị ăn nháy sáng để người chơi thấy vì sao được ăn
                        flashMs[step.targetPitIndex] = SystemClock.uptimeMillis()
                        invalidate()
                        delay(220)
                        onStep?.invoke(step, tmp)
                        capture(step)
                        delay(if (step.capturedQuan) 900 else 620)
                    }
                    is OAnQuanStep.DeadEnd -> {
                        handVisible = false
                        pitCenter(step.emptyNextPit, tmp)
                        onStep?.invoke(step, tmp)
                        flashMs[step.emptyNextPit] = SystemClock.uptimeMillis()
                        invalidate()
                        delay(380)
                    }
                    is OAnQuanStep.TurnFinished -> {
                        handVisible = false
                        display.currentTurn = step.nextPlayer
                        pitCenter(0, tmp)
                        onStep?.invoke(step, tmp)
                        invalidate()
                    }
                    is OAnQuanStep.ReliefStones -> {
                        pitCenter(step.player.ownedPits.first + 2, tmp)
                        onStep?.invoke(step, tmp)
                        relief(step)
                        delay(560)
                    }
                    is OAnQuanStep.GameOver -> {
                        handVisible = false
                        pitCenter(OAnQuanBoardState.QUAN_HUU_INDEX, tmp)
                        onStep?.invoke(step, tmp)
                        harvest()
                        display.isGameOver = true
                        display.winner = step.winner
                        delay(900)
                    }
                }
            }
            handVisible = false
            invalidate()
            onComplete()
        }
    }

    // =====================================================================
    // Các bước hoạt cảnh
    // =====================================================================

    private suspend fun pickUp(pit: Int, speed: Float) {
        val r = pitRects[pit]
        val count = display.pits[pit]
        val hx = r.centerX()
        val hy = r.centerY() - r.height() * 0.42f
        if (!handVisible) {
            handX = hx
            handY = hy
        } else {
            moveHand(hx, hy, (160 * speed).toLong())
        }
        display.pits[pit] = 0
        badgePopMs[pit] = SystemClock.uptimeMillis()
        val shown = min(count, 8)
        for (i in 0 until shown) {
            slotPosition(pit, i)
            val sx = slotOut[0]
            val sy = slotOut[1]
            launchFlyer(sx, sy, hx, hy, (200 * speed).toLong(), r.height() * 0.25f,
                stoneColor(pit, i), stoneRadius, fadeOut = true, onLand = null)
        }
        handCount = count
        handVisible = true
        invalidate()
        delay((240 * speed).toLong())
    }

    private suspend fun dropOne(step: OAnQuanStep.DropOne, speed: Float) {
        val r = pitRects[step.pitIndex]
        val hx = r.centerX()
        val hy = r.centerY() - r.height() * 0.42f
        moveHand(hx, hy, (130 * speed).toLong())
        handCount = step.remainingInHand
        val slotIndex = (step.currentPitCount - 1).coerceIn(0, MAX_STONES_SHOWN - 1)
        slotPosition(step.pitIndex, slotIndex)
        val tx = slotOut[0]
        val ty = slotOut[1]
        launchFlyer(hx, hy, tx, ty, (120 * speed).toLong(), -stoneRadius, stoneColor(step.pitIndex, slotIndex), stoneRadius, fadeOut = false) {
            display.pits[step.pitIndex] = step.currentPitCount
            badgePopMs[step.pitIndex] = SystemClock.uptimeMillis()
            onStoneLanded?.invoke()
        }
        if (step.remainingInHand == 0) {
            delay((130 * speed).toLong())
            handVisible = false
        } else {
            delay((60 * speed).toLong())
        }
    }

    private fun capture(step: OAnQuanStep.Capture) {
        val pit = step.targetPitIndex
        val r = pitRects[pit]
        // Sỏi bay về phía người ăn: người chơi 1 ở dưới, người chơi 2 ở trên
        val toY = if (step.player == PlayerSide.PLAYER_1) height + stoneRadius * 3f else -stoneRadius * 3f
        val shown = min(display.pits[pit], MAX_STONES_SHOWN)
        for (i in 0 until shown) {
            slotPosition(pit, i)
            val sx = slotOut[0]
            val sy = slotOut[1]
            launchFlyer(sx, sy, width / 2f + (sx - r.centerX()) * 2f, toY, 520L + i * 25L, r.height() * 0.5f,
                stoneColor(pit, i), stoneRadius, fadeOut = false, onLand = null)
        }
        if (step.capturedQuan) {
            val slot = if (pit == OAnQuanBoardState.QUAN_HUU_INDEX) 0 else 1
            quanHidden[slot] = true
            launchFlyer(r.centerX(), r.centerY(), width / 2f, toY, 700L, r.height() * 0.6f,
                quanColor(pit), quanRadius, fadeOut = false, onLand = null)
            display.removeQuanPieceAt(pit)
        }
        display.pits[pit] = 0
        badgePopMs[pit] = SystemClock.uptimeMillis()
        if (step.player == PlayerSide.PLAYER_1) display.player1Score = step.newPlayerScore
        else display.player2Score = step.newPlayerScore
        invalidate()
    }

    private suspend fun relief(step: OAnQuanStep.ReliefStones) {
        val fromY = if (step.player == PlayerSide.PLAYER_1) height + stoneRadius * 2f else -stoneRadius * 2f
        for (pit in step.player.ownedPits) {
            val r = pitRects[pit]
            launchFlyer(width / 2f, fromY, r.centerX(), r.centerY(), 340L, r.height() * 0.4f,
                stoneColor(pit, 0), stoneRadius, fadeOut = false) {
                display.pits[pit] = 1
                badgePopMs[pit] = SystemClock.uptimeMillis()
                onStoneLanded?.invoke()
            }
            delay(70)
        }
        if (step.player == PlayerSide.PLAYER_1) display.player1Score = step.currentScore
        else display.player2Score = step.currentScore
    }

    private fun harvest() {
        // Hết quan tàn dân: sỏi còn trên mỗi bên bay về chủ của nó
        for (pit in 0 until 12) {
            if (OAnQuanBoardState.isQuanPit(pit)) continue
            val shown = min(display.pits[pit], MAX_STONES_SHOWN)
            val toY = if (pit in 0..4) height + stoneRadius * 3f else -stoneRadius * 3f
            for (i in 0 until shown) {
                slotPosition(pit, i)
                val sx = slotOut[0]
                val sy = slotOut[1]
                launchFlyer(sx, sy, sx, toY, 600L + i * 30L, pitRects[pit].height() * 0.3f,
                    stoneColor(pit, i), stoneRadius, fadeOut = false, onLand = null)
            }
            display.pits[pit] = 0
            badgePopMs[pit] = SystemClock.uptimeMillis()
        }
        invalidate()
    }

    private suspend fun moveHand(toX: Float, toY: Float, durationMs: Long) {
        val fromX = handX
        val fromY = handY
        val lift = stoneRadius * 2.2f
        tween(durationMs, AccelerateDecelerateInterpolator()) { t ->
            handX = fromX + (toX - fromX) * t
            handY = fromY + (toY - fromY) * t - sin(t * PI.toFloat()) * lift
        }
    }

    private suspend fun tween(durationMs: Long, interp: Interpolator, update: (Float) -> Unit) {
        if (durationMs <= 0) {
            update(1f)
            invalidate()
            return
        }
        suspendCancellableCoroutine { cont ->
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                interpolator = interp
                addUpdateListener {
                    update(it.animatedValue as Float)
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                })
            }
            cont.invokeOnCancellation { animator.cancel() }
            animator.start()
        }
    }

    private fun launchFlyer(
        fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long, arc: Float,
        color: Int, radius: Float, fadeOut: Boolean, onLand: (() -> Unit)?
    ) {
        flyers += Flyer(fromX, fromY, toX, toY, SystemClock.uptimeMillis(), durationMs, arc, color, radius, fadeOut, onLand)
        postInvalidateOnAnimation()
    }

    // =====================================================================
    // Bố cục
    // =====================================================================

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val available = MeasureSpec.getSize(heightMeasureSpec)
        // wrap_content: chỉ xin vừa đủ cho bàn cờ và hai dải trên/dưới để thẻ người chơi ôm sát bàn
        val desired = ((w - dp(SIDE_DP) * 2) * BOARD_ASPECT + dp(ZONE_DP) * 2).toInt()
        val h = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> available
            MeasureSpec.AT_MOST -> min(desired, available)
            else -> desired
        }
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutBoard(w.toFloat(), h.toFloat())
    }

    private fun layoutBoard(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val side = dp(SIDE_DP)
        // Chừa khoảng trên/dưới bàn cho tay rải sỏi và nút chọn hướng
        val zone = dp(ZONE_DP)
        val boardW = w - side * 2
        val boardH = min(h - zone * 2, boardW * BOARD_ASPECT).coerceAtLeast(dp(120f))
        val top = (h - boardH) / 2f
        frameRect.set(side, top, side + boardW, top + boardH)
        lacquerRect.set(frameRect)
        lacquerRect.inset(dp(4f), dp(4f))
        woodRect.set(lacquerRect)
        woodRect.inset(dp(5f), dp(5f))

        val inner = RectF(woodRect).apply { inset(dp(7f), dp(8f)) }
        val unit = inner.width() / 7.3f
        val quanW = unit * 1.15f
        val gapX = dp(4f)
        val gapY = dp(8f)
        val rowH = (inner.height() - gapY) / 2f

        pitRects[OAnQuanBoardState.QUAN_TA_INDEX].set(inner.left, inner.top, inner.left + quanW, inner.bottom)
        pitRects[OAnQuanBoardState.QUAN_HUU_INDEX].set(inner.right - quanW, inner.top, inner.right, inner.bottom)
        val danLeft = inner.left + quanW + gapX
        val danW = (inner.width() - quanW * 2 - gapX * 2) / 5f
        for (col in 0..4) {
            val l = danLeft + col * danW + gapX / 2
            val r = l + danW - gapX
            pitRects[col].set(l, inner.top + rowH + gapY, r, inner.bottom)
            pitRects[10 - col].set(l, inner.top, r, inner.top + rowH)
        }

        stoneRadius = min(danW * 0.15f, rowH * 0.11f)
        quanRadius = min(quanW * 0.36f, inner.height() * 0.17f)
        arrowRadius = dp(21f)

        computeQuanCenter(OAnQuanBoardState.QUAN_HUU_INDEX, quanCenters[0])
        computeQuanCenter(OAnQuanBoardState.QUAN_TA_INDEX, quanCenters[1])
        buildQuanPath(quanPaths[0], pitRects[OAnQuanBoardState.QUAN_HUU_INDEX], isLeft = false)
        buildQuanPath(quanPaths[1], pitRects[OAnQuanBoardState.QUAN_TA_INDEX], isLeft = true)

        for (pit in 0 until 12) {
            val r = pitRects[pit]
            pitShaders[pit] = RadialGradient(
                r.centerX(), r.centerY() + r.height() * 0.12f, (min(r.width(), r.height()) * 0.75f).coerceAtLeast(1f),
                intArrayOf(0xFF1E0C04.toInt(), 0xFF3A1A0A.toInt(), 0xFF5C2E12.toInt()),
                floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP
            )
        }
        for ((slot, pit) in intArrayOf(OAnQuanBoardState.QUAN_HUU_INDEX, OAnQuanBoardState.QUAN_TA_INDEX).withIndex()) {
            val c = quanCenter(pit)
            quanShaders[slot] = RadialGradient(
                c.x - quanRadius * 0.35f, c.y - quanRadius * 0.4f, quanRadius * 1.5f,
                if (slot == 0) intArrayOf(0xFFFFF3C4.toInt(), 0xFFFFB300.toInt(), 0xFFC62828.toInt(), 0xFF6D0F0F.toInt())
                else intArrayOf(0xFFE3FFF0.toInt(), 0xFF5FD39C.toInt(), 0xFF1F7A52.toInt(), 0xFF0B3D27.toInt()),
                floatArrayOf(0f, 0.3f, 0.75f, 1f), Shader.TileMode.CLAMP
            )
        }

        frameShader = LinearGradient(0f, frameRect.top, 0f, frameRect.bottom,
            intArrayOf(0xFFFFE9A8.toInt(), 0xFFE0A526.toInt(), 0xFF9C6510.toInt()), null, Shader.TileMode.CLAMP)
        lacquerShader = LinearGradient(0f, lacquerRect.top, 0f, lacquerRect.bottom,
            0xFFB71C1C.toInt(), 0xFF6A0E0E.toInt(), Shader.TileMode.CLAMP)
        woodShader = LinearGradient(0f, woodRect.top, 0f, woodRect.bottom,
            intArrayOf(0xFFB9763C.toInt(), 0xFF97592A.toInt(), 0xFF7A431C.toInt()), null, Shader.TileMode.CLAMP)
        // Vân gỗ: vài đường cong nhạt chạy ngang bàn
        grainPath.reset()
        val grainRnd = Random(7)
        var y = woodRect.top + dp(6f)
        while (y < woodRect.bottom) {
            grainPath.moveTo(woodRect.left, y)
            val amp = dp(grainRnd.nextFloat() * 4f + 1f)
            val waves = 3 + grainRnd.nextInt(3)
            val seg = woodRect.width() / waves
            for (k in 0 until waves) {
                val x0 = woodRect.left + k * seg
                grainPath.cubicTo(x0 + seg * 0.33f, y - amp, x0 + seg * 0.66f, y + amp, x0 + seg, y)
            }
            y += dp(grainRnd.nextFloat() * 7f + 5f)
        }
    }

    private fun buildQuanPath(path: Path, r: RectF, isLeft: Boolean) {
        path.reset()
        val oval = RectF(if (isLeft) r.left else r.left - r.width(), r.top, if (isLeft) r.right + r.width() else r.right, r.bottom)
        if (isLeft) {
            path.moveTo(r.right, r.top)
            path.arcTo(oval, 270f, -180f, false)
        } else {
            path.moveTo(r.left, r.top)
            path.arcTo(oval, 270f, 180f, false)
        }
        path.close()
    }

    private fun quanCenter(pit: Int): PointF =
        quanCenters[if (pit == OAnQuanBoardState.QUAN_HUU_INDEX) 0 else 1]

    private fun computeQuanCenter(pit: Int, out: PointF) {
        val r = pitRects[pit]
        val x = if (pit == OAnQuanBoardState.QUAN_TA_INDEX) r.left + r.width() * 0.58f else r.right - r.width() * 0.58f
        out.set(x, r.centerY() - r.height() * 0.08f)
    }

    /** Toạ độ thật của viên sỏi thứ [i] trong ô [pit], ghi vào [slotOut] (không cấp phát mỗi khung hình). */
    private fun slotPosition(pit: Int, i: Int) {
        val r = pitRects[pit]
        val k = i.coerceIn(0, MAX_STONES_SHOWN - 1)
        val nx = stoneSlots[pit][k * 2]
        val ny = stoneSlots[pit][k * 2 + 1]
        if (OAnQuanBoardState.isQuanPit(pit)) {
            // Ô quan: sỏi dân nằm dưới chân quân Quan
            val c = quanCenter(pit)
            val rx = r.width() * 0.34f
            val ry = r.height() * 0.2f
            slotOut[0] = c.x + nx * rx
            slotOut[1] = c.y + quanRadius * 1.25f + ny * ry
        } else {
            val rx = r.width() / 2f - stoneRadius * 1.4f
            val ry = r.height() / 2f - stoneRadius * 1.7f
            slotOut[0] = r.centerX() + nx * rx
            slotOut[1] = r.centerY() - stoneRadius * 0.3f + ny * ry
        }
    }

    // =====================================================================
    // Vẽ
    // =====================================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (frameRect.isEmpty) return
        val now = SystemClock.uptimeMillis()
        var animating = false

        drawBoard(canvas)

        val canSelect = interactive && !display.isGameOver
        val glow = if (canSelect) 0.45f + 0.35f * sin(now / 260f) else 0f
        if (canSelect) animating = true

        for (pit in 0 until 12) {
            val shake = shakeOffset(pit, now)
            if (shake != 0f) animating = true
            canvas.save()
            canvas.translate(shake, 0f)
            if (OAnQuanBoardState.isQuanPit(pit)) drawQuanPit(canvas, pit, now) else drawDanPit(canvas, pit, canSelect, glow, now)
            if (flashMs[pit] > 0L && now - flashMs[pit] < FLASH_MS) {
                animating = true
                drawFlash(canvas, pit, (now - flashMs[pit]) / FLASH_MS.toFloat())
            }
            canvas.restore()
            if (badgePopMs[pit] > 0L && now - badgePopMs[pit] < POP_MS) animating = true
            drawBadge(canvas, pit, now)
        }

        if (selectedPit >= 0 && canSelect) {
            drawArrows(canvas, now)
            animating = true
        }

        if (flyers.isNotEmpty()) {
            animating = true
            drawFlyers(canvas, now)
        }

        if (handVisible) {
            animating = true
            handBob = sin(now / 120f) * dp(1.5f)
            drawHand(canvas)
        }

        if (animating) postInvalidateOnAnimation()
    }

    private fun drawBoard(canvas: Canvas) {
        val rOuter = dp(22f)
        // Bóng đổ dưới bàn
        fill.shader = null
        fill.color = 0x66000000
        canvas.drawRoundRect(frameRect.left + dp(2f), frameRect.top + dp(6f), frameRect.right - dp(2f), frameRect.bottom + dp(8f), rOuter, rOuter, fill)
        // Khung vàng
        fill.color = Color.BLACK
        fill.shader = frameShader
        canvas.drawRoundRect(frameRect, rOuter, rOuter, fill)
        // Viền sơn mài đỏ
        fill.color = Color.BLACK
        fill.shader = lacquerShader
        canvas.drawRoundRect(lacquerRect, rOuter - dp(4f), rOuter - dp(4f), fill)
        // Mặt gỗ
        fill.color = Color.BLACK
        fill.shader = woodShader
        val rWood = rOuter - dp(9f)
        canvas.drawRoundRect(woodRect, rWood, rWood, fill)
        fill.shader = null

        canvas.save()
        canvas.clipRect(woodRect)
        stroke.shader = null
        stroke.strokeWidth = dp(1f)
        stroke.color = 0x24000000
        canvas.drawPath(grainPath, stroke)
        canvas.restore()

        // Ánh sáng mép trên mặt gỗ
        stroke.strokeWidth = dp(1.5f)
        stroke.color = 0x55FFE0B2
        canvas.drawLine(woodRect.left + rWood, woodRect.top + dp(1f), woodRect.right - rWood, woodRect.top + dp(1f), stroke)

        // Hoa văn vàng bốn góc khung
        fill.color = 0xFFFFE082.toInt()
        val inset = dp(11f)
        for ((x, y) in listOf(
            frameRect.left + inset to frameRect.top + inset, frameRect.right - inset to frameRect.top + inset,
            frameRect.left + inset to frameRect.bottom - inset, frameRect.right - inset to frameRect.bottom - inset
        )) {
            canvas.drawCircle(x, y, dp(2.6f), fill)
        }

        // Rãnh giữa hai hàng ô dân
        val l = pitRects[10].left
        val r = pitRects[6].right
        val y = (pitRects[10].bottom + pitRects[0].top) / 2f
        stroke.strokeWidth = dp(2f)
        stroke.color = 0x66000000
        canvas.drawLine(l, y, r, y, stroke)
        stroke.color = 0x40FFE0B2
        stroke.strokeWidth = dp(1f)
        canvas.drawLine(l, y + dp(1.5f), r, y + dp(1.5f), stroke)
    }

    private fun drawCarvedPit(canvas: Canvas, pit: Int, path: Path?, radius: Float) {
        val r = pitRects[pit]
        fill.color = Color.BLACK
        fill.shader = pitShaders[pit]
        if (path != null) canvas.drawPath(path, fill) else canvas.drawRoundRect(r, radius, radius, fill)
        fill.shader = null
        // Mép dưới hắt sáng, mép trên đổ bóng: tạo cảm giác ô được khoét lõm
        stroke.strokeWidth = dp(1.5f)
        stroke.color = 0x80E8B07A.toInt()
        canvas.save()
        canvas.clipRect(r.left - dp(2f), r.centerY(), r.right + dp(2f), r.bottom + dp(2f))
        if (path != null) canvas.drawPath(path, stroke) else canvas.drawRoundRect(r, radius, radius, stroke)
        canvas.restore()
        stroke.strokeWidth = dp(2.5f)
        stroke.color = 0x99000000.toInt()
        canvas.save()
        canvas.clipRect(r.left - dp(2f), r.top - dp(2f), r.right + dp(2f), r.centerY())
        if (path != null) canvas.drawPath(path, stroke) else canvas.drawRoundRect(r, radius, radius, stroke)
        canvas.restore()
    }

    private fun drawDanPit(canvas: Canvas, pit: Int, canSelect: Boolean, glow: Float, now: Long) {
        val r = pitRects[pit]
        val radius = min(r.width(), r.height()) * 0.32f
        drawCarvedPit(canvas, pit, null, radius)

        val selectable = canSelect && display.currentTurn.ownsPit(pit) && display.pits[pit] > 0
        val selected = selectable && pit == selectedPit
        if (selected) {
            fill.color = 0x33FFD54F
            canvas.drawRoundRect(r, radius, radius, fill)
            stroke.strokeWidth = dp(3f)
            stroke.color = 0xFFFFE082.toInt()
            canvas.drawRoundRect(r, radius, radius, stroke)
        } else if (selectable) {
            stroke.strokeWidth = dp(2f)
            stroke.color = ColorUtils.setAlphaComponent(0xFFFFD54F.toInt(), (glow * 255).toInt().coerceIn(0, 255))
            canvas.drawRoundRect(r, radius, radius, stroke)
        }

        val lift = if (selected) -dp(3f) + sin(now / 180f) * dp(1f) else 0f
        drawStones(canvas, pit, display.pits[pit], lift)
    }

    private fun drawQuanPit(canvas: Canvas, pit: Int, now: Long) {
        val slot = if (pit == OAnQuanBoardState.QUAN_HUU_INDEX) 0 else 1
        drawCarvedPit(canvas, pit, quanPaths[slot], 0f)
        drawStones(canvas, pit, display.pits[pit], 0f)
        if (display.hasQuanPieceAt(pit) && !quanHidden[slot]) {
            val c = quanCenter(pit)
            drawQuanPiece(canvas, c.x, c.y, quanRadius, slot)
        }
    }

    private fun drawQuanPiece(canvas: Canvas, x: Float, y: Float, radius: Float, slot: Int) {
        fill.shader = null
        fill.color = 0x73000000
        canvas.drawOval(x - radius * 0.95f, y + radius * 0.55f, x + radius * 0.95f, y + radius * 1.12f, fill)
        val shader = quanShaders[slot]
        val c = quanCenter(if (slot == 0) OAnQuanBoardState.QUAN_HUU_INDEX else OAnQuanBoardState.QUAN_TA_INDEX)
        if (shader != null && abs(c.x - x) < 1f && abs(c.y - y) < 1f && abs(radius - quanRadius) < 1f) {
            fill.color = Color.BLACK
            fill.shader = shader
        } else {
            fill.shader = null
            fill.color = if (slot == 0) 0xFFE53935.toInt() else 0xFF2E9D6A.toInt()
        }
        canvas.drawOval(x - radius, y - radius * 0.9f, x + radius, y + radius * 0.9f, fill)
        fill.shader = null
        fill.color = 0xB3FFFFFF.toInt()
        canvas.drawOval(x - radius * 0.55f, y - radius * 0.7f, x - radius * 0.05f, y - radius * 0.38f, fill)
        stroke.strokeWidth = dp(1.5f)
        stroke.color = 0xFFFFE082.toInt()
        canvas.drawOval(x - radius, y - radius * 0.9f, x + radius, y + radius * 0.9f, stroke)
    }

    private fun drawStones(canvas: Canvas, pit: Int, count: Int, lift: Float) {
        val shown = min(count, MAX_STONES_SHOWN)
        for (i in 0 until shown) {
            slotPosition(pit, i)
            val x = slotOut[0]
            val y = slotOut[1]
            drawStone(canvas, x, y + lift, stoneRadius, stoneColor(pit, i), 1f)
        }
    }

    private fun drawStone(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Float) {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        fill.shader = null
        fill.color = 0x80000000.toInt()
        fill.alpha = (a * 0.5f).toInt()
        canvas.drawOval(x - radius * 0.95f, y - radius * 0.35f, x + radius * 1.05f, y + radius * 1.15f, fill)
        fill.color = color
        fill.alpha = a
        canvas.drawOval(x - radius, y - radius * 0.86f, x + radius, y + radius * 0.86f, fill)
        fill.color = ColorUtils.blendARGB(color, Color.WHITE, 0.45f)
        fill.alpha = a
        canvas.drawOval(x - radius * 0.72f, y - radius * 0.72f, x + radius * 0.4f, y + radius * 0.1f, fill)
        fill.color = Color.WHITE
        fill.alpha = (a * 0.85f).toInt()
        canvas.drawCircle(x - radius * 0.38f, y - radius * 0.42f, radius * 0.18f, fill)
        fill.alpha = 255
    }

    private fun drawBadge(canvas: Canvas, pit: Int, now: Long) {
        val count = display.pits[pit]
        val isQuan = OAnQuanBoardState.isQuanPit(pit)
        if (dealing && count == 0) return
        if (isQuan && count == 0 && !display.hasQuanPieceAt(pit)) return
        val r = pitRects[pit]
        val label = if (isQuan && display.hasQuanPieceAt(pit)) {
            if (count > 0) "Q+$count" else "Q"
        } else count.toString()
        val elapsed = now - badgePopMs[pit]
        val pop = if (badgePopMs[pit] > 0L && elapsed < POP_MS) {
            val t = elapsed / POP_MS.toFloat()
            1f + sin(t * PI.toFloat()) * 0.35f
        } else 1f

        text.textSize = dp(11f) * pop
        val tw = text.measureText(label)
        val h = dp(16f) * pop
        val w = (tw + dp(10f)).coerceAtLeast(h)
        val cx = r.centerX()
        val cy = when {
            isQuan -> r.bottom - dp(12f)
            pit in 0..4 -> r.bottom - dp(1f)
            else -> r.top + dp(1f)
        }
        val empty = count == 0 && !isQuan
        fill.shader = null
        fill.color = if (empty) 0xCC3A1A0A.toInt() else 0xFFFFF3D6.toInt()
        canvas.drawRoundRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, h / 2, h / 2, fill)
        stroke.strokeWidth = dp(1f)
        stroke.color = if (empty) 0x668D6E63 else 0xFFC8962E.toInt()
        canvas.drawRoundRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, h / 2, h / 2, stroke)
        text.color = if (empty) 0xFFBCAAA4.toInt() else 0xFF7A090D.toInt()
        canvas.drawText(label, cx, cy - (text.descent() + text.ascent()) / 2f, text)
    }

    private fun drawFlash(canvas: Canvas, pit: Int, t: Float) {
        val r = pitRects[pit]
        val a = ((1f - t) * 0.6f * 255).toInt().coerceIn(0, 255)
        fill.shader = null
        fill.color = ColorUtils.setAlphaComponent(0xFFFFF59D.toInt(), a)
        if (OAnQuanBoardState.isQuanPit(pit)) {
            canvas.drawPath(quanPaths[if (pit == OAnQuanBoardState.QUAN_HUU_INDEX) 0 else 1], fill)
        } else {
            val radius = min(r.width(), r.height()) * 0.32f
            canvas.drawRoundRect(r, radius, radius, fill)
        }
    }

    private fun drawArrows(canvas: Canvas, now: Long) {
        val r = pitRects[selectedPit]
        val bottomRow = selectedPit in 0..4
        val y = if (bottomRow) frameRect.bottom + dp(30f) else frameRect.top - dp(30f)
        val spread = arrowRadius * 1.25f
        val minX = arrowRadius + spread + dp(2f)
        val cx = r.centerX().coerceIn(minX, width - minX)
        leftArrow.set(cx - spread, y)
        rightArrow.set(cx + spread, y)

        // Dây nối từ ô tới cụm mũi tên
        stroke.strokeWidth = dp(2f)
        stroke.color = 0x99FFE082.toInt()
        canvas.drawLine(r.centerX(), if (bottomRow) r.bottom else r.top, cx, y, stroke)

        val hintActive = hintDirection != null && now < hintUntilMs
        if (!hintActive) hintDirection = null
        val leftDir = leftDirectionFor(selectedPit)
        for (isLeft in booleanArrayOf(true, false)) {
            val p = if (isLeft) leftArrow else rightArrow
            val dir = if (isLeft) leftDir else rightDirectionFor(selectedPit)
            val isHint = hintActive && dir == hintDirection
            val pulse = 1f + sin(now / (if (isHint) 110f else 200f)) * (if (isHint) 0.12f else 0.05f)
            val rad = arrowRadius * pulse
            if (isHint) {
                fill.shader = null
                fill.color = ColorUtils.setAlphaComponent(0xFFFFEB3B.toInt(), (120 + 100 * sin(now / 110f)).toInt().coerceIn(0, 255))
                canvas.drawCircle(p.x, p.y, rad * 1.45f, fill)
            }
            fill.color = 0x66000000
            canvas.drawCircle(p.x, p.y + dp(3f), rad, fill)
            fill.color = Color.BLACK
            fill.shader = LinearGradient(0f, p.y - rad, 0f, p.y + rad, 0xFFFFE57F.toInt(), 0xFFFFA000.toInt(), Shader.TileMode.CLAMP)
            canvas.drawCircle(p.x, p.y, rad, fill)
            fill.shader = null
            stroke.strokeWidth = dp(2f)
            stroke.color = 0xFFFFF8E1.toInt()
            canvas.drawCircle(p.x, p.y, rad, stroke)
            // Mũi tên
            val s = rad * 0.42f
            val dirX = if (isLeft) -1f else 1f
            stroke.strokeWidth = dp(3.5f)
            stroke.color = 0xFF7A090D.toInt()
            stroke.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(p.x - dirX * s, p.y, p.x + dirX * s, p.y, stroke)
            canvas.drawLine(p.x + dirX * s, p.y, p.x + dirX * s * 0.1f, p.y - s * 0.85f, stroke)
            canvas.drawLine(p.x + dirX * s, p.y, p.x + dirX * s * 0.1f, p.y + s * 0.85f, stroke)
            stroke.strokeCap = Paint.Cap.BUTT
        }
    }

    private fun drawFlyers(canvas: Canvas, now: Long) {
        val it = flyers.iterator()
        val landed = ArrayList<() -> Unit>(4)
        while (it.hasNext()) {
            val f = it.next()
            val t = ((now - f.startMs).toFloat() / f.durationMs).coerceIn(0f, 1f)
            val e = if (f.fadeOut) DECEL.getInterpolation(t) else ACCEL_DECEL.getInterpolation(t)
            val x = f.fromX + (f.toX - f.fromX) * e
            val y = f.fromY + (f.toY - f.fromY) * e - sin(e * PI.toFloat()) * f.arc
            val alpha = if (f.fadeOut) 1f - t * 0.7f else 1f
            if (f.radius > stoneRadius * 1.5f) {
                drawQuanPiece(canvas, x, y, f.radius, if (f.color == quanColor(OAnQuanBoardState.QUAN_HUU_INDEX)) 0 else 1)
            } else {
                drawStone(canvas, x, y, f.radius, f.color, alpha)
            }
            if (t >= 1f) {
                it.remove()
                f.onLand?.let(landed::add)
            }
        }
        landed.forEach { it() }
    }

    private fun drawHand(canvas: Canvas) {
        val x = handX
        val y = handY + handBob
        val shown = min(handCount, 6)
        // Bóng tay
        fill.shader = null
        fill.color = 0x40000000
        canvas.drawOval(x - stoneRadius * 2.4f, y + stoneRadius * 1.4f, x + stoneRadius * 2.4f, y + stoneRadius * 2.4f, fill)
        for (i in 0 until shown) {
            val a = i * (2 * PI / 6).toFloat() + 0.4f
            val d = if (i == 0) 0f else stoneRadius * 1.15f
            drawStone(canvas, x + cos(a) * d, y + sin(a) * d * 0.7f, stoneRadius, STONE_COLORS[i % STONE_COLORS.size], 1f)
        }
        if (handCount > 0) {
            val label = handCount.toString()
            text.textSize = dp(11f)
            val bx = x + stoneRadius * 2.2f
            val by = y - stoneRadius * 1.8f
            val h = dp(17f)
            val w = (text.measureText(label) + dp(10f)).coerceAtLeast(h)
            fill.color = 0xFFC62828.toInt()
            canvas.drawRoundRect(bx - w / 2, by - h / 2, bx + w / 2, by + h / 2, h / 2, h / 2, fill)
            stroke.strokeWidth = dp(1.2f)
            stroke.color = 0xFFFFE082.toInt()
            canvas.drawRoundRect(bx - w / 2, by - h / 2, bx + w / 2, by + h / 2, h / 2, h / 2, stroke)
            text.color = Color.WHITE
            canvas.drawText(label, bx, by - (text.descent() + text.ascent()) / 2f, text)
        }
    }

    private fun shakeOffset(pit: Int, now: Long): Float {
        val start = shakeMs[pit]
        if (start == 0L) return 0f
        val t = (now - start) / SHAKE_MS.toFloat()
        if (t >= 1f) return 0f
        return sin(t * 6f * PI.toFloat()) * dp(4f) * (1f - t)
    }

    private fun stoneColor(pit: Int, i: Int) = STONE_COLORS[(pit * 7 + i * 3) % STONE_COLORS.size]

    private fun quanColor(pit: Int) = if (pit == OAnQuanBoardState.QUAN_HUU_INDEX) 0xFFE53935.toInt() else 0xFF2E9D6A.toInt()

    // =====================================================================
    // Chạm
    // =====================================================================

    // Hướng theo góc nhìn người chơi dưới: hàng dưới đi sang trái là giảm chỉ số,
    // hàng trên đi sang trái là tăng chỉ số (10 -> 11)
    private fun leftDirectionFor(pit: Int) = if (pit in 0..4) MoveDirection.CLOCKWISE else MoveDirection.COUNTER_CLOCKWISE
    private fun rightDirectionFor(pit: Int) = if (pit in 0..4) MoveDirection.COUNTER_CLOCKWISE else MoveDirection.CLOCKWISE

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        if (!interactive || display.isGameOver) return false
        val x = event.x
        val y = event.y

        if (selectedPit >= 0) {
            val hit = arrowRadius * 1.3f
            val dir = when {
                hypot(x - leftArrow.x, y - leftArrow.y) < hit -> leftDirectionFor(selectedPit)
                hypot(x - rightArrow.x, y - rightArrow.y) < hit -> rightDirectionFor(selectedPit)
                else -> null
            }
            if (dir != null) {
                val pit = selectedPit
                selectedPit = -1
                hintDirection = null
                invalidate()
                performClick()
                onMoveSelected?.invoke(pit, dir)
                return true
            }
        }

        for (pit in 0 until 12) {
            val r = pitRects[pit]
            if (x >= r.left - dp(3f) && x <= r.right + dp(3f) && y >= r.top - dp(3f) && y <= r.bottom + dp(3f)) {
                if (display.currentTurn.ownsPit(pit) && display.pits[pit] > 0) {
                    selectedPit = if (selectedPit == pit) -1 else pit
                    if (selectedPit != pit) hintDirection = null
                    badgePopMs[pit] = SystemClock.uptimeMillis()
                    invalidate()
                    return true
                }
            }
        }
        if (selectedPit >= 0) {
            selectedPit = -1
            hintDirection = null
            invalidate()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        animationJob?.cancel()
        flyers.clear()
        super.onDetachedFromWindow()
    }

    private fun dp(v: Float) = v * density

    companion object {
        private const val MAX_STONES_SHOWN = 16
        private const val SIDE_DP = 4f
        private const val ZONE_DP = 56f
        private const val BOARD_ASPECT = 0.85f
        private const val POP_MS = 240L
        private const val SHAKE_MS = 520L
        private const val FLASH_MS = 420L
        private const val HINT_MS = 6000L

        /** Thứ tự rải sỏi lúc mở ván: chạy vòng quanh bàn cho đẹp mắt. */
        private val DEAL_ORDER = intArrayOf(0, 1, 2, 3, 4, 6, 7, 8, 9, 10)

        private val STONE_COLORS = intArrayOf(
            0xFFF3E9D2.toInt(), // trắng ngà
            0xFF8FD1B0.toInt(), // ngọc bích
            0xFFD9D2C3.toInt(), // xám đá
            0xFFE9BE85.toInt(), // hổ phách
            0xFFF2D4C9.toInt(), // hồng ngọc trai
            0xFFB8D8E8.toInt()  // xanh sương
        )
        private val DECEL = DecelerateInterpolator()
        private val ACCEL_DECEL = AccelerateDecelerateInterpolator()
    }
}
