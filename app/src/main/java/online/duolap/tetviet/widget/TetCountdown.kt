package online.duolap.tetviet.widget

import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Mốc thời gian Tết dùng chung cho màn hình chính và widget.
 * Mọi phép tính theo giờ Việt Nam (GMT+7, không có giờ mùa hè) để kết quả không lệch theo múi giờ máy.
 */
object TetCountdown {

    val TIME_ZONE: TimeZone = TimeZone.getTimeZone("GMT+7")

    /** Mùng 1 Tết Nguyên Đán Đinh Mùi: 06/02/2027 00:00:00 GMT+7. */
    val TET_START_MILLIS: Long = vietnamMidnight(2027, Calendar.FEBRUARY, 6)

    /** Mùng 1 Tết năm trước (Bính Ngọ): 17/02/2026, mốc 0% của thanh tiến trình. */
    val PREVIOUS_TET_START_MILLIS: Long = vietnamMidnight(2026, Calendar.FEBRUARY, 17)

    private val DAY_MILLIS = TimeUnit.DAYS.toMillis(1)

    /**
     * Số ngày tròn còn lại đến Mùng 1 Tết, cách tính giống màn hình chính
     * (tối 11/11/2026 -> 86 ngày + vài giờ). Bằng 0 từ khi Tết đến.
     */
    fun fullDaysUntilTet(nowMillis: Long = System.currentTimeMillis()): Long {
        return ((TET_START_MILLIS - nowMillis) / DAY_MILLIS).coerceAtLeast(0L)
    }

    /**
     * Phần lẻ dưới 1 ngày sau khi trừ [fullDaysUntilTet]. Tết rơi đúng 00:00 nên đây cũng là
     * thời gian còn lại đến nửa đêm (giờ VN) — lúc số ngày giảm đi 1.
     */
    fun remainderOfDayMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val remaining = TET_START_MILLIS - nowMillis
        return if (remaining <= 0) 0L else remaining % DAY_MILLIS
    }

    /**
     * Số ngày lịch còn lại đến Mùng 1 Tết, dùng cho widget Cổ điển (hôm 11/11/2026 -> 87).
     * Bằng 0 vào Mùng 1, âm sau Tết.
     */
    fun calendarDaysUntilTet(nowMillis: Long = System.currentTimeMillis()): Int {
        val startOfToday = nextDayStartMillis(nowMillis) - DAY_MILLIS
        return ((TET_START_MILLIS - startOfToday) / DAY_MILLIS).toInt()
    }

    fun isTetArrived(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis >= TET_START_MILLIS

    /** Phần trăm chặng đường từ Tết năm trước đến Tết năm nay (0–100). */
    fun progressPercent(nowMillis: Long = System.currentTimeMillis()): Int {
        val total = TET_START_MILLIS - PREVIOUS_TET_START_MILLIS
        val elapsed = nowMillis - PREVIOUS_TET_START_MILLIS
        return (elapsed * 100 / total).toInt().coerceIn(0, 100)
    }

    /** Số thứ tự của ngày (theo giờ VN), dùng để đổi lời chúc mỗi ngày. */
    fun dayIndex(nowMillis: Long = System.currentTimeMillis()): Long {
        return (nowMillis + TIME_ZONE.getOffset(nowMillis)) / DAY_MILLIS
    }

    /** Thời điểm bắt đầu ngày kế tiếp (00:00 GMT+7). */
    fun nextDayStartMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance(TIME_ZONE).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis + DAY_MILLIS
    }

    private fun vietnamMidnight(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance(TIME_ZONE).apply {
            clear()
            set(year, month, day)
        }.timeInMillis
    }
}
