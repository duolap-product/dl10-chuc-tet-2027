package online.duolap.tetviet.checkin

import online.duolap.tetviet.checkin.DailyCheckInPreferences.Companion.statusFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCheckInStatusTest {

    private val today = 20_000L

    @Test
    fun firstTimeStartsAtDayOne() {
        val s = statusFor(today, lastClaimDay = -1, lastClaimDayNumber = 0, storedStreak = 0, legacyClaimedToday = false)
        assertFalse(s.claimedToday)
        assertEquals(1, s.dayNumber)
        assertEquals(0, s.streak)
        assertEquals(10, s.todayReward)
    }

    @Test
    fun claimedYesterdayContinuesStreak() {
        val s = statusFor(today, today - 1, lastClaimDayNumber = 3, storedStreak = 3, legacyClaimedToday = false)
        assertFalse(s.claimedToday)
        assertEquals(4, s.dayNumber)
        assertEquals(3, s.streak)
        assertEquals(30, s.todayReward)
    }

    @Test
    fun missedOneDayResetsToDayOne() {
        val s = statusFor(today, today - 2, lastClaimDayNumber = 5, storedStreak = 5, legacyClaimedToday = false)
        assertEquals(1, s.dayNumber)
        assertEquals(0, s.streak)
    }

    @Test
    fun claimedTodayShowsTomorrowReward() {
        val s = statusFor(today, today, lastClaimDayNumber = 6, storedStreak = 6, legacyClaimedToday = false)
        assertTrue(s.claimedToday)
        assertEquals(6, s.dayNumber)
        assertEquals(7, s.nextDayNumber)
        assertEquals(100, s.nextReward)
    }

    @Test
    fun afterDaySevenCycleRestarts() {
        val claimed = statusFor(today, today, lastClaimDayNumber = 7, storedStreak = 7, legacyClaimedToday = false)
        assertEquals(1, claimed.nextDayNumber)
        val nextDay = statusFor(today + 1, today, lastClaimDayNumber = 7, storedStreak = 7, legacyClaimedToday = false)
        assertEquals(1, nextDay.dayNumber)
        assertEquals(7, nextDay.streak) // chuỗi ngày liên tiếp vẫn giữ, chỉ vòng xu quay lại
    }

    @Test
    fun legacyBauCuaGiftCountsAsClaimedToday() {
        val s = statusFor(today, lastClaimDay = -1, lastClaimDayNumber = 0, storedStreak = 0, legacyClaimedToday = true)
        assertTrue(s.claimedToday)
        assertEquals(2, s.nextDayNumber)
    }

    @Test
    fun videoRewardIsDoubleButCapped() {
        assertEquals(20, DailyCheckInPreferences.videoRewardFor(10))
        assertEquals(80, DailyCheckInPreferences.videoRewardFor(40))
        assertEquals(DailyCheckInPreferences.VIDEO_REWARD_CAP, DailyCheckInPreferences.videoRewardFor(50))
        assertEquals(DailyCheckInPreferences.VIDEO_REWARD_CAP, DailyCheckInPreferences.videoRewardFor(150))
        assertEquals(DailyCheckInPreferences.VIDEO_REWARD_CAP, DailyCheckInPreferences.videoRewardFor(300))
    }
}
