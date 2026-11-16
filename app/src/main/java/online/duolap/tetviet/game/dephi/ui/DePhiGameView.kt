package online.duolap.tetviet.game.dephi.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import online.duolap.tetviet.R
import online.duolap.tetviet.game.dephi.engine.DePhiEngine
import online.duolap.tetviet.game.dephi.engine.DePhiEvent
import online.duolap.tetviet.game.dephi.engine.DePhiState
import online.duolap.tetviet.game.dephi.engine.ItemType
import online.duolap.tetviet.game.dephi.engine.ObstacleType
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Màn chơi "Cùng Dê Phi Đón Tết", vẽ bằng Canvas.
 *
 * Luật chơi nằm ở [DePhiEngine]; view này chỉ chạy vòng lặp theo nhịp màn hình (Choreographer),
 * chuyển toạ độ dp của engine sang pixel và vẽ. Mọi hình vector được dựng thành bitmap đúng một lần
 * khi đổi kích thước, nên mỗi khung hình chỉ vẽ bitmap (không tạo đối tượng mới trong onDraw).
 */
class DePhiGameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs), Choreographer.FrameCallback {

    interface Listener {
        /** Gọi mỗi khung hình khi đang chơi (để cập nhật HUD). */
        fun onTick(state: DePhiState)
        fun onEvent(event: DePhiEvent)
        /** Chạm màn hình khi chưa chơi / đang tạm dừng (để bắt đầu hoặc chơi tiếp). */
        fun onIdleTap()
    }

    enum class Mode { READY, RUNNING, PAUSED, OVER }

    var listener: Listener? = null
    var mode = Mode.READY
        private set
    var state: DePhiState = DePhiEngine.newGame(seed = 0)
        private set

    private val density = resources.displayMetrics.density
    /** px cho mỗi đơn vị thế giới (dp của engine), phóng to để dê và vật cản dễ nhìn trên điện thoại. */
    private val scale = density * WORLD_ZOOM
    /** Mặt đất ở khoảng 72% chiều cao: khu chơi nằm giữa tầm mắt, phần dưới lát gạch. */
    private var groundTop = 0f
    private val groundHeightPx get() = height - groundTop
    private val groundY get() = groundTop

    private var lastFrameNanos = 0L
    private var looping = false
    private var cosmeticSec = 0f // thời gian cho mây, mặt trời... (chạy cả khi chưa chơi)
    private var shakeSec = 0f

    /** Trang phục dê đang dùng; đổi thì dựng lại 3 khung hình. */
    var skin: GoatSkin = GoatSkin.TRANG
        set(value) {
            if (field == value) return
            field = value
            if (width > 0) renderGoat()
            invalidate()
        }

    // Bitmap dựng sẵn
    private val goatFrames = arrayOfNulls<Bitmap>(3)
    private val obstacleBitmaps = HashMap<ObstacleType, Bitmap>()
    private val itemBitmaps = HashMap<ItemType, Bitmap>()

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val skyPaint = Paint()
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFE7A0") }
    private val sunGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#40FFE7A0") }
    private val skyLanternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF9E40") }
    private val skyLanternGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33FFC46B") }
    private val skyLanternRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C62828") }

    // Ba lớp nền vẽ sẵn thành ảnh lặp ngang (làng xa, cây mai + đèn lồng, đường gạch)
    private var farTile: Bitmap? = null
    private var midTile: Bitmap? = null
    private var groundTile: Bitmap? = null
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33000000") }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF3C4")
        textSize = 16 * density
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.arimo_bold)
        setShadowLayer(3 * density, 0f, 1.5f * density, Color.parseColor("#B3000000"))
    }

    // Hạt lấp lánh khi nhặt lộc và chữ "+điểm" bay lên (mảng cố định, không cấp phát khi chơi)
    private val maxParticles = 64
    private val px = FloatArray(maxParticles)
    private val py = FloatArray(maxParticles)
    private val pvx = FloatArray(maxParticles)
    private val pvy = FloatArray(maxParticles)
    private val plife = FloatArray(maxParticles)
    private val pcolor = IntArray(maxParticles)
    private val maxPopups = 8
    private val popX = FloatArray(maxPopups)
    private val popY = FloatArray(maxPopups)
    private val popLife = FloatArray(maxPopups)
    private val popText = arrayOfNulls<String>(maxPopups)
    private val fxRandom = Random(7)

    private companion object {
        const val WORLD_ZOOM = 1.2f
        const val GROUND_FRACTION = 0.72f
    }

    init {
        isClickable = true
        isFocusable = true
    }

    // --- Điều khiển từ Activity -------------------------------------------------------------

    fun newGame() {
        state = DePhiEngine.newGame(spawnX = width / scale)
        plife.fill(0f)
        popLife.fill(0f)
        shakeSec = 0f
        mode = Mode.RUNNING
        startLoop()
    }

    /** Hồi sinh sau va chạm (đã xem quảng cáo): chờ người chơi chạm để chạy tiếp. */
    fun revive(): Boolean {
        if (mode != Mode.OVER || !DePhiEngine.revive(state)) return false
        plife.fill(0f)
        shakeSec = 0f
        mode = Mode.PAUSED
        lastFrameNanos = 0L
        return true
    }

    fun pause() {
        if (mode == Mode.RUNNING) mode = Mode.PAUSED
    }

    fun resume() {
        if (mode == Mode.PAUSED) {
            mode = Mode.RUNNING
            lastFrameNanos = 0L
        }
    }

    /** Màn hình hiện/ẩn: dừng hẳn vòng lặp khi không hiển thị để không tốn pin. */
    fun setVisibleToUser(visible: Boolean) {
        if (visible) startLoop() else stopLoop()
    }

    // --- Vòng lặp ------------------------------------------------------------------------------

    private fun startLoop() {
        if (looping || !isAttachedToWindow) return
        looping = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stopLoop() {
        looping = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!looping) return
        val dtMs = if (lastFrameNanos == 0L) 16L else ((frameTimeNanos - lastFrameNanos) / 1_000_000L).coerceIn(0L, 50L)
        lastFrameNanos = frameTimeNanos
        val dt = dtMs / 1000f
        cosmeticSec += dt
        if (shakeSec > 0f) shakeSec = (shakeSec - dt).coerceAtLeast(0f)

        if (mode == Mode.RUNNING) {
            for (event in DePhiEngine.step(state, dtMs)) {
                handleEvent(event)
                listener?.onEvent(event)
            }
            listener?.onTick(state)
        }
        updateEffects(dt)
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun handleEvent(event: DePhiEvent) {
        when (event) {
            is DePhiEvent.Collected -> {
                val sx = event.x * scale
                val sy = groundY - event.y * scale
                val colors = when (event.type) {
                    ItemType.GOLD -> intArrayOf(0xFFFFD54F.toInt(), 0xFFFFF3B0.toInt())
                    ItemType.LIXI -> intArrayOf(0xFFE53935.toInt(), 0xFFFFD54F.toInt())
                    ItemType.BANH_CHUNG -> intArrayOf(0xFF66BB6A.toInt(), 0xFFFFF3B0.toInt())
                }
                burst(sx, sy, if (event.type == ItemType.GOLD) 6 else 14, colors)
                popup(sx, sy - 10 * density, "+${event.type.points}")
            }
            is DePhiEvent.Crashed -> {
                mode = Mode.OVER
                shakeSec = 0.35f
                burst((DePhiEngine.GOAT_X + 40) * scale, groundY - 30 * scale, 16,
                    intArrayOf(0xFFFF7043.toInt(), 0xFFFFD54F.toInt()))
            }
            DePhiEvent.Landed -> burst(
                (DePhiEngine.GOAT_X + 20) * scale, groundY - 2 * scale, 5,
                intArrayOf(0xFFD7B48A.toInt(), 0xFFC49A6C.toInt()), upward = false
            )
            is DePhiEvent.Milestone -> popup(width / 2f, height * 0.32f, "${event.meters} m")
        }
    }

    private fun burst(x: Float, y: Float, count: Int, colors: IntArray, upward: Boolean = true) {
        var made = 0
        for (i in 0 until maxParticles) {
            if (plife[i] > 0f) continue
            px[i] = x
            py[i] = y
            pvx[i] = (fxRandom.nextFloat() - 0.5f) * 260 * density
            pvy[i] = if (upward) -(80 + fxRandom.nextFloat() * 220) * density else -(20 + fxRandom.nextFloat() * 50) * density
            plife[i] = 0.5f + fxRandom.nextFloat() * 0.3f
            pcolor[i] = colors[made % colors.size]
            if (++made >= count) break
        }
    }

    private fun popup(x: Float, y: Float, text: String) {
        val i = (0 until maxPopups).minByOrNull { popLife[it] } ?: return
        popX[i] = x
        popY[i] = y
        popLife[i] = 0.8f
        popText[i] = text
    }

    private fun updateEffects(dt: Float) {
        val g = 600 * density
        for (i in 0 until maxParticles) {
            if (plife[i] <= 0f) continue
            plife[i] -= dt
            pvy[i] += g * dt
            px[i] += pvx[i] * dt
            py[i] += pvy[i] * dt
        }
        for (i in 0 until maxPopups) {
            if (popLife[i] <= 0f) continue
            popLife[i] -= dt
            popY[i] -= 40 * density * dt
        }
    }

    // --- Vẽ ------------------------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        groundTop = h * GROUND_FRACTION
        state.spawnX = w / scale
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, h - groundHeightPx,
            intArrayOf(
                Color.parseColor("#7A1414"), Color.parseColor("#C8352A"),
                Color.parseColor("#F47B3E"), Color.parseColor("#FFC877"), Color.parseColor("#FFE6B8")
            ),
            floatArrayOf(0f, 0.3f, 0.6f, 0.85f, 1f), Shader.TileMode.CLAMP
        )
        renderGoat()
        buildSceneTiles(w, h)
        obstacleBitmaps[ObstacleType.PHAO] = render(R.drawable.ic_dp_phao, ObstacleType.PHAO.width, ObstacleType.PHAO.height)
        obstacleBitmaps[ObstacleType.CHAU_MAI] = render(R.drawable.ic_dp_chau_mai, ObstacleType.CHAU_MAI.width, ObstacleType.CHAU_MAI.height)
        obstacleBitmaps[ObstacleType.HOP_QUA] = render(R.drawable.ic_dp_hop_qua, ObstacleType.HOP_QUA.width, ObstacleType.HOP_QUA.height)
        itemBitmaps[ItemType.GOLD] = render(R.drawable.ic_dp_gold, ItemType.GOLD.size, ItemType.GOLD.size)
        itemBitmaps[ItemType.BANH_CHUNG] = render(R.drawable.ic_dp_banh_chung, ItemType.BANH_CHUNG.size, ItemType.BANH_CHUNG.size)
        itemBitmaps[ItemType.LIXI] = render(R.drawable.ic_custom_red_packet, ItemType.LIXI.size, ItemType.LIXI.size)
    }

    private fun renderGoat() {
        goatFrames[0] = render(skin.run1, DePhiEngine.GOAT_W, DePhiEngine.GOAT_H)
        goatFrames[1] = render(skin.run2, DePhiEngine.GOAT_W, DePhiEngine.GOAT_H)
        goatFrames[2] = render(skin.jump, DePhiEngine.GOAT_W, DePhiEngine.GOAT_H)
    }

    private fun render(@DrawableRes res: Int, wDp: Float, hDp: Float): Bitmap {
        val w = (wDp * scale).toInt().coerceAtLeast(1)
        val h = (hDp * scale).toInt().coerceAtLeast(1)
        val bmp = createBitmap(w, h)
        ContextCompat.getDrawable(context, res)!!.apply {
            setBounds(0, 0, w, h)
            draw(Canvas(bmp))
        }
        return bmp
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || goatFrames[0] == null) return
        val save = canvas.save()
        if (shakeSec > 0f) {
            val amp = 8 * density * (shakeSec / 0.35f)
            canvas.translate(sin(cosmeticSec * 90f) * amp, sin(cosmeticSec * 71f) * amp * 0.6f)
        }
        val worldPx = state.distanceDp * scale
        drawBackground(canvas, worldPx)
        drawGround(canvas, worldPx)

        val gy = groundY
        for (o in state.obstacles) {
            val bmp = obstacleBitmaps[o.type] ?: continue
            canvas.drawBitmap(bmp, o.x * scale, gy - bmp.height, bitmapPaint)
        }
        for (item in state.items) {
            if (item.collected) continue
            val bmp = itemBitmaps[item.type] ?: continue
            val bob = sin((cosmeticSec + item.x / 97f) * 2 * PI.toFloat()) * 2.5f * scale
            canvas.drawBitmap(bmp, item.x * scale, gy - item.y * scale - bmp.height + bob, bitmapPaint)
        }
        drawGoat(canvas, gy)

        for (i in 0 until maxParticles) {
            if (plife[i] <= 0f) continue
            particlePaint.color = pcolor[i]
            particlePaint.alpha = (255 * (plife[i] / 0.8f).coerceIn(0f, 1f)).toInt()
            canvas.drawCircle(px[i], py[i], 3.2f * density, particlePaint)
        }
        for (i in 0 until maxPopups) {
            val text = popText[i] ?: continue
            if (popLife[i] <= 0f) continue
            popupPaint.alpha = (255 * (popLife[i] / 0.8f).coerceIn(0f, 1f)).toInt()
            canvas.drawText(text, popX[i], popY[i], popupPaint)
        }
        canvas.restoreToCount(save)
    }

    private fun drawBackground(canvas: Canvas, worldPx: Float) {
        val gy = groundY
        canvas.drawRect(0f, 0f, width.toFloat(), gy + 1, skyPaint)

        // Mặt trời lặn sau mái đình
        val sunX = width * 0.7f
        val sunY = gy * 0.62f
        canvas.drawCircle(sunX, sunY, 70 * density + sin(cosmeticSec * 1.2f) * 4 * density, sunGlowPaint)
        canvas.drawCircle(sunX, sunY, 44 * density, sunPaint)

        // Đèn trời bay lên chậm, lắc nhẹ
        for (k in 0 until 6) {
            val speed = (14 + k * 3) * density
            val span = gy + 60 * density
            val y = span - ((cosmeticSec * speed + k * span / 6f) % span) - 30 * density
            val x = width * ((k * 0.17f + 0.08f) % 1f) + sin(cosmeticSec * 0.9f + k) * 10 * density - worldPx * 0.02f % width
            val xx = ((x % width) + width) % width
            val sc = density * (0.7f + 0.12f * (k % 3))
            canvas.drawCircle(xx, y, 13 * sc, skyLanternGlowPaint)
            canvas.drawRoundRect(xx - 5 * sc, y - 7 * sc, xx + 5 * sc, y + 7 * sc, 3 * sc, 3 * sc, skyLanternPaint)
            canvas.drawRect(xx - 5 * sc, y + 5 * sc, xx + 5 * sc, y + 7 * sc, skyLanternRimPaint)
        }

        drawTiled(canvas, farTile, worldPx * 0.15f, gy - (farTile?.height ?: 0))
        drawTiled(canvas, midTile, worldPx * 0.45f, gy - (midTile?.height ?: 0))
    }

    private fun drawGround(canvas: Canvas, worldPx: Float) {
        drawTiled(canvas, groundTile, worldPx, groundY)
    }

    private fun drawTiled(canvas: Canvas, tile: Bitmap?, shift: Float, top: Float) {
        tile ?: return
        val w = tile.width
        var x = -(shift % w)
        while (x < width) {
            canvas.drawBitmap(tile, x, top, null)
            x += w
        }
    }

    /** Vẽ sẵn 3 lớp nền (một lần mỗi khi đổi kích thước); lặp ngang liền mạch theo bề rộng ô. */
    private fun buildSceneTiles(w: Int, h: Int) {
        val d = density
        val tileW = (420 * d).toInt()
        val gy = h - groundHeightPx
        val rnd = Random(2027)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Lớp xa: bóng làng quê (mái đình cong, nhà, tháp chùa, cửa sổ sáng đèn)
        val farH = (gy * 0.36f).toInt().coerceAtLeast((60 * d).toInt())
        farTile = createBitmap(tileW, farH).also { bmp ->
            val c = Canvas(bmp)
            val silhouette = Color.parseColor("#8A2A1F")
            val window = Color.parseColor("#FFD27A")
            var x = 0f
            while (x < tileW) {
                val bw = (44 + rnd.nextInt(40)) * d
                val bh = (22 + rnd.nextInt(26)) * d
                if (x + bw > tileW - 10 * d) break // chừa mép để ô lặp nối liền
                val base = farH.toFloat()
                p.color = silhouette
                c.drawRect(x, base - bh, x + bw, base, p)
                // Mái cong kiểu đình
                val roof = Path().apply {
                    moveTo(x - 8 * d, base - bh + 2 * d)
                    quadTo(x - 2 * d, base - bh - 2 * d, x + 4 * d, base - bh - 10 * d)
                    lineTo(x + bw - 4 * d, base - bh - 10 * d)
                    quadTo(x + bw + 2 * d, base - bh - 2 * d, x + bw + 8 * d, base - bh + 2 * d)
                    close()
                }
                c.drawPath(roof, p)
                // Cửa sổ sáng đèn
                p.color = window
                val wins = (bw / (14 * d)).toInt().coerceAtLeast(1)
                for (k in 0 until wins) {
                    if (rnd.nextFloat() < 0.6f) {
                        val wx = x + 6 * d + k * 14 * d
                        c.drawRect(wx, base - bh + 8 * d, wx + 5 * d, base - bh + 14 * d, p)
                    }
                }
                x += bw + (14 + rnd.nextInt(24)) * d
                // Thỉnh thoảng một tháp chùa nhiều tầng
                if (rnd.nextFloat() < 0.25f && x < tileW - 40 * d) {
                    p.color = silhouette
                    val tx = x
                    var tw = 26 * d
                    var ty = base
                    repeat(3) { lvl ->
                        val th = (14 - lvl * 2) * d
                        c.drawRect(tx + (26 * d - tw) / 2, ty - th, tx + (26 * d + tw) / 2, ty, p)
                        val r = Path().apply {
                            moveTo(tx + (26 * d - tw) / 2 - 6 * d, ty - th + 2 * d)
                            lineTo(tx + 13 * d, ty - th - 7 * d)
                            lineTo(tx + (26 * d + tw) / 2 + 6 * d, ty - th + 2 * d)
                            close()
                        }
                        c.drawPath(r, p)
                        ty -= th + 5 * d
                        tw -= 6 * d
                    }
                    x += 40 * d
                }
            }
        }

        // Lớp giữa: cây mai vàng và dây đèn lồng đỏ giữa các cột
        val midH = (110 * d).toInt()
        midTile = createBitmap(tileW, midH).also { bmp ->
            val c = Canvas(bmp)
            val trunk = Color.parseColor("#5D3A1F")
            val base = midH.toFloat()
            // Dây đèn lồng giữa 2 cột
            p.color = Color.parseColor("#4E2A14")
            val poleA = 20 * d
            val poleB = tileW * 0.5f + 20 * d
            for (px0 in floatArrayOf(poleA, poleB)) c.drawRect(px0, base - 96 * d, px0 + 4 * d, base, p)
            val wire = Path().apply {
                moveTo(poleA + 2 * d, base - 92 * d)
                quadTo((poleA + poleB) / 2, base - 70 * d, poleB + 2 * d, base - 92 * d)
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.5f * d
            c.drawPath(wire, p)
            p.style = Paint.Style.FILL
            for (k in 1..5) {
                val t = k / 6f
                val lx = poleA + (poleB - poleA) * t
                val ly = base - 92 * d + 22 * d * 4 * t * (1 - t) + 6 * d
                p.color = Color.parseColor("#E53935")
                c.drawOval(lx - 7 * d, ly, lx + 7 * d, ly + 13 * d, p)
                p.color = Color.parseColor("#FFD54F")
                c.drawRect(lx - 4 * d, ly - 1.5f * d, lx + 4 * d, ly + 1 * d, p)
                c.drawRect(lx - 4 * d, ly + 12 * d, lx + 4 * d, ly + 14.5f * d, p)
            }
            // Cây mai
            for (tx in floatArrayOf(tileW * 0.3f, tileW * 0.8f)) {
                p.color = trunk
                c.drawRect(tx - 3 * d, base - 52 * d, tx + 3 * d, base, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3 * d
                p.strokeCap = Paint.Cap.ROUND
                c.drawLine(tx, base - 40 * d, tx - 18 * d, base - 62 * d, p)
                c.drawLine(tx, base - 46 * d, tx + 16 * d, base - 66 * d, p)
                p.style = Paint.Style.FILL
                repeat(26) {
                    val bx = tx + (rnd.nextFloat() - 0.5f) * 56 * d
                    val by = base - 50 * d - rnd.nextFloat() * 30 * d
                    p.color = if (rnd.nextBoolean()) Color.parseColor("#FFC107") else Color.parseColor("#FFD54F")
                    c.drawCircle(bx, by, (2.6f + rnd.nextFloat() * 1.6f) * d, p)
                }
            }
        }

        // Mặt đường lát gạch đỏ, viền vàng
        val gh = groundHeightPx.toInt() + 2
        val gTileW = (96 * d).toInt()
        groundTile = createBitmap(gTileW, gh).also { bmp ->
            val c = Canvas(bmp)
            p.color = Color.parseColor("#9C3B26")
            c.drawRect(0f, 0f, gTileW.toFloat(), gh.toFloat(), p)
            p.color = Color.parseColor("#F2C14E")
            c.drawRect(0f, 0f, gTileW.toFloat(), 4 * d, p)
            p.color = Color.parseColor("#7A090D")
            c.drawRect(0f, 4 * d, gTileW.toFloat(), 7 * d, p)
            // Hàng gạch so le
            p.color = Color.parseColor("#B8503A")
            val rowH = 13 * d
            var y = 9 * d
            var row = 0
            while (y < gh) {
                val off = if (row % 2 == 0) 0f else 24 * d
                var x = -off
                while (x < gTileW) {
                    c.drawRoundRect(x + 1.5f * d, y, x + 46.5f * d, y + rowH - 2.5f * d, 2 * d, 2 * d, p)
                    x += 48 * d
                }
                y += rowH
                row++
            }
        }
    }

    private fun drawGoat(canvas: Canvas, gy: Float) {
        val frame = when {
            !state.onGround -> goatFrames[2]
            mode == Mode.RUNNING -> goatFrames[((state.distanceDp / 22f).toInt()) % 2]
            mode == Mode.READY -> goatFrames[((cosmeticSec * 4).toInt()) % 2] // chạy tại chỗ ở màn chờ
            else -> goatFrames[0]
        } ?: return
        val left = DePhiEngine.GOAT_X * scale
        val bottom = gy - state.goatY * scale
        // Bóng dưới chân, nhỏ dần khi nhảy cao
        val shadowScale = 1f - (state.goatY / DePhiEngine.JUMP_HEIGHT).coerceIn(0f, 1f) * 0.5f
        val cx = left + frame.width * 0.47f
        canvas.drawOval(
            cx - 20 * scale * shadowScale, gy - 3 * scale,
            cx + 20 * scale * shadowScale, gy + 3 * scale, shadowPaint
        )
        bitmapPaint.alpha = if (mode == Mode.OVER && ((cosmeticSec * 10).toInt() % 2 == 0)) 140 else 255
        canvas.drawBitmap(frame, left, bottom - frame.height, bitmapPaint)
        bitmapPaint.alpha = 255
    }

    // --- Chạm ----------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (mode == Mode.RUNNING) {
                if (DePhiEngine.jump(state)) performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            } else {
                listener?.onIdleTap()
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startLoop()
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        super.onDetachedFromWindow()
    }
}
