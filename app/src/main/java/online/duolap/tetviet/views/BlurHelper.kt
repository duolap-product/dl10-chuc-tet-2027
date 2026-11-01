package online.duolap.tetviet.views

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import androidx.core.graphics.scale

/**
 * Tiện ích làm mờ (blur) ảnh nền tối ưu hiệu năng:
 * - Trên Android 12+ (API 31+): Sử dụng RenderEffect phần cứng siêu mượt.
 * - Trên Android < 12: Thu nhỏ ảnh (downsampling) kết hợp thuật toán Fast Gaussian Blur
 *   xử lý trên Dispatchers.Default để đảm bảo không bị giật lag UI (60fps).
 */
object BlurHelper {

    fun applyBlurEffect(imageView: ImageView, blurRadius: Float = 25f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val blurEffect = RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius,
                    Shader.TileMode.CLAMP
                )
                imageView.setRenderEffect(blurEffect)
                return
            } catch (_: Exception) {}
        }
    }

    /**
     * Làm mờ bitmap bằng thuật toán downsampling + Fast Blur đa luồng
     */
    suspend fun blurBitmap(source: Bitmap, radius: Int = 18, scale: Float = 0.25f): Bitmap {
        return withContext(Dispatchers.Default) {
            val width = max(1, (source.width * scale).toInt())
            val height = max(1, (source.height * scale).toInt())

            val scaledBitmap = source.scale(width, height, false)
            fastBlur(scaledBitmap, radius)
        }
    }

    private fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        val bitmap = sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        if (radius < 1) return bitmap

        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) {
            dv[idx] = idx / divsum
        }

        var yw: Int = 0
        yi = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var rbs: Int
        val routsum: Int
        val goutsum: Int
        val boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        for (curY in 0 until h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            var rout = 0
            var gout = 0
            var bout = 0
            rsum = 0
            gsum = 0
            bsum = 0
            for (curI in -radius..radius) {
                p = pix[yi + minOf(wm, maxOf(curI, 0))]
                val sir = stack[curI + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)

                val rbsVal = radius + 1 - kotlin.math.abs(curI)
                rsum += sir[0] * rbsVal
                gsum += sir[1] * rbsVal
                bsum += sir[2] * rbsVal
                if (curI > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    rout += sir[0]
                    gout += sir[1]
                    bout += sir[2]
                }
            }
            stackpointer = radius

            for (curX in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= rout
                gsum -= gout
                bsum -= bout

                stackstart = stackpointer - radius + div
                val sir = stack[stackstart % div]

                rout -= sir[0]
                gout -= sir[1]
                bout -= sir[2]

                if (curY == 0) {
                    vmin[curX] = minOf(curX + radius + 1, wm)
                }
                p = pix[yw + vmin[curX]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                val nextSir = stack[stackpointer % div]

                rout += nextSir[0]
                gout += nextSir[1]
                bout += nextSir[2]

                rinsum -= nextSir[0]
                ginsum -= nextSir[1]
                binsum -= nextSir[2]

                yi++
            }
            yw += w
        }

        for (curX in 0 until w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            var rout = 0
            var gout = 0
            var bout = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (curI in -radius..radius) {
                yi = maxOf(0, yp) + curX
                val sir = stack[curI + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]

                val rbsVal = radius + 1 - kotlin.math.abs(curI)
                rsum += r[yi] * rbsVal
                gsum += g[yi] * rbsVal
                bsum += b[yi] * rbsVal

                if (curI > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    rout += sir[0]
                    gout += sir[1]
                    bout += sir[2]
                }

                if (curI < hm) {
                    yp += w
                }
            }
            yi = curX
            stackpointer = radius
            for (curY in 0 until h) {
                pix[yi] = (-0x1000000 and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= rout
                gsum -= gout
                bsum -= bout

                stackstart = stackpointer - radius + div
                val sir = stack[stackstart % div]

                rout -= sir[0]
                gout -= sir[1]
                bout -= sir[2]

                if (curX == 0) {
                    vmin[curY] = minOf(curY + radius + 1, hm) * w
                }
                p = curX + vmin[curY]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                val nextSir = stack[stackpointer]

                rout += nextSir[0]
                gout += nextSir[1]
                bout += nextSir[2]

                rinsum -= nextSir[0]
                ginsum -= nextSir[1]
                binsum -= nextSir[2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }
}
