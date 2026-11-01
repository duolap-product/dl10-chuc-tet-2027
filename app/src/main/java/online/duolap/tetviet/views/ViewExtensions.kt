package online.duolap.tetviet.views

import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Extension function thiết lập OnClickListener chống click nhanh/duplicate click (debounce)
 * và hỗ trợ hiệu ứng thu nhỏ rồi phóng về 1.0 (bounce animation).
 *
 * @param hasAnim Nếu true thì có hiệu ứng view thu nhỏ rồi phóng về 1.0f trước khi thực hiện click. Mặc định false.
 * @param debounceTime Thời gian chặn click trùng lặp (mili-giây), mặc định 500ms.
 * @param onSafeClick Callback xử lý sự kiện click an toàn.
 */
fun View.setSafeOnClickListener(
    hasAnim: Boolean = false,
    debounceTime: Long = 500L,
    onSafeClick: (View) -> Unit
) {
    var lastClickTime = 0L

    setOnClickListener { v ->
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastClickTime < debounceTime) {
            return@setOnClickListener
        }
        lastClickTime = currentTime

        if (hasAnim) {
            v.animate().cancel()
            v.scaleX = 1.0f
            v.scaleY = 1.0f

            v.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(90)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(110)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction {
                            onSafeClick(v)
                        }
                        .start()
                }
                .start()
        } else {
            onSafeClick(v)
        }
    }
}

///**
// * Overload cho phép truyền [debounceTime] trước [hasAnim].
// */
//fun View.setSafeOnClickListener(
//    debounceTime: Long,
//    hasAnim: Boolean = false,
//    onSafeClick: (View) -> Unit
//) {
//    setOnSafeClickListener(hasAnim = hasAnim, debounceTime = debounceTime, onSafeClick = onSafeClick)
//}
//
///**
// * Alias cho [setSafeOnClickListener] với thứ tự tham số (hasAnim, debounceTime).
// */
//fun View.setOnSafeClickListener(
//    hasAnim: Boolean = false,
//    debounceTime: Long = 500L,
//    onSafeClick: (View) -> Unit
//) {
//    setSafeOnClickListener(hasAnim = hasAnim, debounceTime = debounceTime, onSafeClick = onSafeClick)
//}
//
///**
// * Alias cho [setSafeOnClickListener] với thứ tự tham số (debounceTime, hasAnim).
// */
//fun View.setOnSafeClickListener(
//    debounceTime: Long,
//    hasAnim: Boolean = false,
//    onSafeClick: (View) -> Unit
//) {
//    setSafeOnClickListener(hasAnim = hasAnim, debounceTime = debounceTime, onSafeClick = onSafeClick)
//}
