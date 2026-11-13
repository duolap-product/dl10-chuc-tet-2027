package online.duolap.tetviet.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class TetCountdownTest {

    /** Thời điểm theo giờ Việt Nam (GMT+7). */
    private fun vnTime(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long {
        return Calendar.getInstance(TetCountdown.TIME_ZONE).apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
    }

    @Test
    fun fullDaysMatchMainScreen() {
        // Tối 11/11/2026 22:32: màn hình chính hiện 86 ngày 01:28
        val now = vnTime(2026, Calendar.NOVEMBER, 11, 22, 32)
        assertEquals(86L, TetCountdown.fullDaysUntilTet(now))
        assertEquals(TimeUnit.MINUTES.toMillis(88), TetCountdown.remainderOfDayMillis(now))
    }

    @Test
    fun remainderEndsAtVietnamMidnight() {
        val now = vnTime(2026, Calendar.NOVEMBER, 11, 22, 32)
        assertEquals(TetCountdown.nextDayStartMillis(now), now + TetCountdown.remainderOfDayMillis(now))
    }

    @Test
    fun classicWidgetCountsCalendarDays() {
        // Khớp thiết kế widget Cổ điển: tối 11/11/2026 hiện 87 ngày
        assertEquals(87, TetCountdown.calendarDaysUntilTet(vnTime(2026, Calendar.NOVEMBER, 11, 22, 32)))
        assertEquals(87, TetCountdown.calendarDaysUntilTet(vnTime(2026, Calendar.NOVEMBER, 11, 0, 0)))
        assertEquals(1, TetCountdown.calendarDaysUntilTet(vnTime(2027, Calendar.FEBRUARY, 5, 23, 59)))
        assertEquals(0, TetCountdown.calendarDaysUntilTet(vnTime(2027, Calendar.FEBRUARY, 6, 8, 0)))
        assertEquals(-1, TetCountdown.calendarDaysUntilTet(vnTime(2027, Calendar.FEBRUARY, 7, 8, 0)))
    }

    @Test
    fun lastDayBeforeTet() {
        val now = vnTime(2027, Calendar.FEBRUARY, 5, 20, 0)
        assertEquals(0L, TetCountdown.fullDaysUntilTet(now))
        assertEquals(TimeUnit.HOURS.toMillis(4), TetCountdown.remainderOfDayMillis(now))
        assertFalse(TetCountdown.isTetArrived(now))
    }

    @Test
    fun tetArrived() {
        val now = vnTime(2027, Calendar.FEBRUARY, 6, 8, 0)
        assertTrue(TetCountdown.isTetArrived(now))
        assertEquals(0L, TetCountdown.fullDaysUntilTet(now))
        assertEquals(0L, TetCountdown.remainderOfDayMillis(now))
        assertEquals(100, TetCountdown.progressPercent(now))
    }

    @Test
    fun progressFromPreviousTet() {
        assertEquals(0, TetCountdown.progressPercent(vnTime(2026, Calendar.FEBRUARY, 17)))
        // 13/11/2026: đã qua 269 / 354 ngày
        assertEquals(75, TetCountdown.progressPercent(vnTime(2026, Calendar.NOVEMBER, 13)))
    }

    @Test
    fun usesVietnamTimeRegardlessOfDeviceZone() {
        // 18:00 UTC ngày 11/11 = 01:00 ngày 12/11 ở Việt Nam
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 11, 18, 0)
        }.timeInMillis
        assertEquals(vnTime(2026, Calendar.NOVEMBER, 13), TetCountdown.nextDayStartMillis(utc))
        assertEquals(
            TetCountdown.dayIndex(vnTime(2026, Calendar.NOVEMBER, 12, 12)),
            TetCountdown.dayIndex(utc)
        )
    }
}
