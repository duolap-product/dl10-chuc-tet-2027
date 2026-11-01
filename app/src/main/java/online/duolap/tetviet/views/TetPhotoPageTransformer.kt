package online.duolap.tetviet.views

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * Hiệu ứng chuyển cảnh 3D Carousel cao cấp dành cho trình xem ảnh Tết:
 * - Khi vuốt ảnh: Thẻ ảnh trung tâm nổi bật với tỉ lệ 1.0, độ sáng 100% và đổ bóng sâu.
 * - Các ảnh 2 bên: Thu nhỏ mượt mà về 0.88x, độ mờ 60%, xoay nhẹ 3D góc Y (-10 đến +10 độ)
 *   tạo cảm giác như đang lật các tấm thiệp Tết nghệ thuật đầy cuốn hút.
 */
class TetPhotoPageTransformer : ViewPager2.PageTransformer {

    companion object {
        private const val MIN_SCALE = 0.88f
        private const val MIN_ALPHA = 0.55f
        private const val MAX_ROTATION_Y = 10f
    }

    override fun transformPage(page: View, position: Float) {
        page.apply {
            when {
                position < -1 -> {
                    // [-Infinity, -1): Ảnh đã cuộn khuất sang trái
                    alpha = MIN_ALPHA
                    scaleX = MIN_SCALE
                    scaleY = MIN_SCALE
                    rotationY = -MAX_ROTATION_Y
                    translationZ = 0f
                }
                position <= 1 -> {
                    // [-1, 1]: Ảnh đang hiển thị trong tầm mắt
                    val factor = 1 - abs(position)

                    // 1. Phóng to thu nhỏ tỉ lệ (Scale)
                    val scaleFactor = MIN_SCALE + (1 - MIN_SCALE) * factor
                    scaleX = scaleFactor
                    scaleY = scaleFactor

                    // 2. Độ trong suốt mượt mà (Alpha)
                    alpha = MIN_ALPHA + (1 - MIN_ALPHA) * factor

                    // 3. Hiệu ứng xoay 3D nhẹ trục Y (RotationY)
                    rotationY = -position * MAX_ROTATION_Y

                    // 4. Độ nổi khối đổ bóng (Elevation & TranslationZ)
                    translationZ = factor * 16f
                }
                else -> {
                    // (1, +Infinity]: Ảnh đã cuộn khuất sang phải
                    alpha = MIN_ALPHA
                    scaleX = MIN_SCALE
                    scaleY = MIN_SCALE
                    rotationY = MAX_ROTATION_Y
                    translationZ = 0f
                }
            }
        }
    }
}
