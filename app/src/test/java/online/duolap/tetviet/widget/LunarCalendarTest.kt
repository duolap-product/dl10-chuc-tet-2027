package online.duolap.tetviet.widget

import online.duolap.tetviet.widget.LunarCalendar.LunarDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LunarCalendarTest {

    @Test
    fun tetDaysAreFirstOfFirstMonth() {
        assertEquals(LunarDate(1, 1, 2024, false), LunarCalendar.fromSolar(10, 2, 2024))
        assertEquals(LunarDate(1, 1, 2025, false), LunarCalendar.fromSolar(29, 1, 2025))
        assertEquals(LunarDate(1, 1, 2026, false), LunarCalendar.fromSolar(17, 2, 2026))
        assertEquals(LunarDate(1, 1, 2027, false), LunarCalendar.fromSolar(6, 2, 2027))
    }

    @Test
    fun dayBeforeTetIsEndOfPreviousLunarYear() {
        val newYearEve = LunarCalendar.fromSolar(5, 2, 2027)
        assertEquals(12, newYearEve.month)
        assertEquals(2026, newYearEve.year)
    }

    @Test
    fun midAutumn2026() {
        assertEquals(LunarDate(15, 8, 2026, false), LunarCalendar.fromSolar(25, 9, 2026))
    }

    @Test
    fun leapSecondMonth2023() {
        // Năm Quý Mão 2023 có tháng 2 nhuận, bắt đầu 22/03/2023
        assertEquals(LunarDate(1, 2, 2023, true), LunarCalendar.fromSolar(22, 3, 2023))
        assertEquals(LunarDate(1, 2, 2023, false), LunarCalendar.fromSolar(20, 2, 2023))
    }
}
