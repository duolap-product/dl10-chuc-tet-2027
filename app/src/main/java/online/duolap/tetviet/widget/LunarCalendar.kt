package online.duolap.tetviet.widget

import java.util.Calendar
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * Đổi dương lịch sang âm lịch Việt Nam (múi giờ +7), theo thuật toán của Hồ Ngọc Đức.
 * Chạy hoàn toàn offline, đủ chính xác cho các năm 1900–2100.
 */
object LunarCalendar {

    data class LunarDate(val day: Int, val month: Int, val year: Int, val isLeapMonth: Boolean)

    private const val TIME_ZONE = 7.0

    /** Ngày âm lịch của thời điểm [millis], tính theo giờ Việt Nam. */
    fun fromMillis(millis: Long): LunarDate {
        val cal = Calendar.getInstance(TetCountdown.TIME_ZONE).apply { timeInMillis = millis }
        return fromSolar(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
    }

    fun fromSolar(dd: Int, mm: Int, yy: Int): LunarDate {
        val dayNumber = jdFromDate(dd, mm, yy)
        val k = floor((dayNumber - 2415021.076998695) / 29.530588853).toInt()
        var monthStart = getNewMoonDay(k + 1)
        if (monthStart > dayNumber) {
            monthStart = getNewMoonDay(k)
        }

        var a11 = getLunarMonth11(yy)
        var b11 = a11
        val lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = getLunarMonth11(yy - 1)
        } else {
            lunarYear = yy + 1
            b11 = getLunarMonth11(yy + 1)
        }

        val lunarDay = dayNumber - monthStart + 1
        val diff = floor((monthStart - a11) / 29.0).toInt()
        var isLeap = false
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapMonthDiff = getLeapMonthOffset(a11)
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10
                if (diff == leapMonthDiff) isLeap = true
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12

        var resultYear = lunarYear
        if (lunarMonth >= 11 && diff < 4) resultYear -= 1
        return LunarDate(lunarDay, lunarMonth, resultYear, isLeap)
    }

    private fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return jd
    }

    /** Thời điểm sóc (trăng mới) thứ [k] tính từ 1/1/1900, theo ngày Julius. */
    private fun newMoon(k: Int): Double {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = PI / 180
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c1 = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * dr * m)
        c1 = c1 - 0.4068 * sin(mpr * dr) + 0.0161 * sin(dr * 2 * mpr)
        c1 -= 0.0004 * sin(dr * 3 * mpr)
        c1 = c1 + 0.0104 * sin(dr * 2 * f) - 0.0051 * sin(dr * (m + mpr))
        c1 = c1 - 0.0074 * sin(dr * (m - mpr)) + 0.0004 * sin(dr * (2 * f + m))
        c1 = c1 - 0.0004 * sin(dr * (2 * f - m)) - 0.0006 * sin(dr * (2 * f + mpr))
        c1 = c1 + 0.0010 * sin(dr * (2 * f - mpr)) + 0.0005 * sin(dr * (2 * mpr + m))
        val deltaT = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        return jd1 + c1 - deltaT
    }

    /** Kinh độ mặt trời (radian) tại ngày Julius [jdn]. */
    private fun sunLongitude(jdn: Double): Double {
        val t = (jdn - 2451545.0) / 36525
        val t2 = t * t
        val dr = PI / 180
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= PI * 2 * floor(l / (PI * 2))
        return l
    }

    private fun getNewMoonDay(k: Int): Int = floor(newMoon(k) + 0.5 + TIME_ZONE / 24).toInt()

    private fun getSunLongitude(dayNumber: Int): Int =
        floor(sunLongitude(dayNumber - 0.5 - TIME_ZONE / 24) / PI * 6).toInt()

    /** Ngày bắt đầu tháng 11 âm lịch (tháng chứa Đông chí) của năm [yy]. */
    private fun getLunarMonth11(yy: Int): Int {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = floor(off / 29.530588853).toInt()
        var nm = getNewMoonDay(k)
        if (getSunLongitude(nm) >= 9) {
            nm = getNewMoonDay(k - 1)
        }
        return nm
    }

    private fun getLeapMonthOffset(a11: Int): Int {
        val k = floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var last: Int
        var i = 1
        var arc = getSunLongitude(getNewMoonDay(k + i))
        do {
            last = arc
            i++
            arc = getSunLongitude(getNewMoonDay(k + i))
        } while (arc != last && i < 14)
        return i - 1
    }
}
