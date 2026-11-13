package online.duolap.tetviet.ads

/**
 * Chặn quảng cáo App Open khi người dùng chỉ tạm rời app rồi quay lại (chia sẻ, mua VIP, cập nhật...),
 * hoặc mở app từ widget/thông báo để xem nhanh.
 */
object AppOpenGate {

    /** Đặt vào Intent mở app từ widget/thông báo: lần vào app đó không hiện App Open. */
    const val EXTRA_SKIP_APP_OPEN = "extra_skip_app_open"

    @Volatile
    private var skipNextForeground = false

    /** Gọi ngay trước khi đưa người dùng ra ngoài app có chủ đích (chia sẻ, Google Play...). */
    fun skipNextForeground() {
        skipNextForeground = true
    }

    internal fun consumeSkip(): Boolean {
        val skip = skipNextForeground
        skipNextForeground = false
        return skip
    }
}
