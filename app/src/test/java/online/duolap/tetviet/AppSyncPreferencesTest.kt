package online.duolap.tetviet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AppSyncPreferencesTest {

    private fun isSameCalendarDay(time1: Long, time2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun shouldSyncCheck(
        lastSync: Long,
        currentTime: Long,
        forceRefresh: Boolean = false
    ): Boolean {
        if (forceRefresh) return true
        if (lastSync <= 0L) return true

        val timeDiff = currentTime - lastSync
        if (timeDiff < 0) return true
        if (timeDiff >= TimeUnit.DAYS.toMillis(1)) return true

        val isSameDay = isSameCalendarDay(lastSync, currentTime)
        if (isSameDay) return false

        return timeDiff >= TimeUnit.HOURS.toMillis(4)
    }

    @Test
    fun testFirstLaunchMustSync() {
        assertTrue(shouldSyncCheck(lastSync = 0L, currentTime = System.currentTimeMillis()))
    }

    @Test
    fun testForceRefreshMustSync() {
        val now = System.currentTimeMillis()
        assertTrue(shouldSyncCheck(lastSync = now - 1000L, currentTime = now, forceRefresh = true))
    }

    @Test
    fun testSameDayWithinMinutesShouldSkip() {
        val now = System.currentTimeMillis()
        val thirtyMinutesAgo = now - TimeUnit.MINUTES.toMillis(30)
        assertFalse(shouldSyncCheck(lastSync = thirtyMinutesAgo, currentTime = now))
    }

    @Test
    fun testNextDayAfterManyHoursMustSync() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.OCTOBER, 28, 14, 0, 0)
        val monday2pm = cal.timeInMillis

        cal.set(2026, Calendar.OCTOBER, 29, 8, 0, 0)
        val tuesday8am = cal.timeInMillis

        assertTrue(shouldSyncCheck(lastSync = monday2pm, currentTime = tuesday8am))
    }

    @Test
    fun testNextDayTooSoonShouldSkip() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.OCTOBER, 28, 23, 55, 0)
        val mondayMidnight = cal.timeInMillis

        cal.set(2026, Calendar.OCTOBER, 29, 0, 10, 0)
        val tuesdayMorning = cal.timeInMillis

        // Less than 4 hours between 23:55 and 00:10 -> should skip
        assertFalse(shouldSyncCheck(lastSync = mondayMidnight, currentTime = tuesdayMorning))
    }

    @Test
    fun testOver24HoursMustSync() {
        val now = System.currentTimeMillis()
        val twentyFiveHoursAgo = now - TimeUnit.HOURS.toMillis(25)
        assertTrue(shouldSyncCheck(lastSync = twentyFiveHoursAgo, currentTime = now))
    }
}

