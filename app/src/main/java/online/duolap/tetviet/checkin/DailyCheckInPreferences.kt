package online.duolap.tetviet.checkin

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import online.duolap.tetviet.data.local.BauCuaPreferences
import online.duolap.tetviet.widget.TetCountdown
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Điểm danh hằng ngày nhận lì xì (xu dùng chung cho Bầu Cua, Ô Ăn Quan, Đập Chuột).
 *
 * Chuỗi 7 ngày, xu tăng dần, ngày 7 là lì xì lớn. Bỏ lỡ một ngày thì chuỗi quay về ngày 1; nhận xong
 * ngày 7 thì sang vòng mới. "Ngày" tính theo giờ Việt Nam ([TetCountdown.dayIndex]).
 * Thay cho quà hằng ngày cũ trong Bầu Cua: [BauCuaPreferences.hasClaimedDailyGiftToday] chỉ còn được
 * đọc để người đã nhận quà kiểu cũ trong ngày cập nhật không nhận thêm lần nữa.
 */
@Singleton
class DailyCheckInPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val coinPrefs: BauCuaPreferences
) {
    data class Status(
        /** Ngày trong chuỗi (1..7) của hôm nay: ngày vừa nhận, hoặc ngày sẽ nhận nếu chưa nhận. */
        val dayNumber: Int,
        val claimedToday: Boolean,
        /** Số ngày liên tiếp đã nhận tính đến hiện tại (0 nếu chuỗi đã đứt). */
        val streak: Int,
        /** Xu của hôm nay (chưa nhân đôi). */
        val todayReward: Int,
        /** Ngày trong chuỗi và số xu của lần nhận kế tiếp (ngày mai nếu hôm nay đã nhận). */
        val nextDayNumber: Int,
        val nextReward: Int
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(computeStatus())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Gọi khi màn hình hiện lại (có thể đã sang ngày mới). */
    fun refresh(): Status = computeStatus().also { _status.value = it }

    fun hasClaimedToday(): Boolean = computeStatus().claimedToday

    /**
     * Nhận lì xì hôm nay. [multiplier] = 2 khi người dùng xem quảng cáo nhân đôi (có trần [VIDEO_REWARD_CAP]).
     * Trả về số xu đã cộng, hoặc null nếu hôm nay đã nhận rồi.
     */
    fun claim(multiplier: Int = 1): Int? {
        val status = computeStatus()
        if (status.claimedToday) return null
        // Xem quảng cáo: x2 nhưng không quá VIDEO_REWARD_CAP, giống thưởng video trong Bầu Cua
        val amount = if (multiplier > 1) videoRewardFor(status.todayReward) else status.todayReward
        coinPrefs.addCoins(amount)
        prefs.edit {
            putLong(KEY_LAST_CLAIM_DAY, today())
            putInt(KEY_LAST_CLAIM_DAY_NUMBER, status.dayNumber)
            putInt(KEY_STREAK, status.streak + 1)
            putInt(KEY_TOTAL_CLAIMS, prefs.getInt(KEY_TOTAL_CLAIMS, 0) + 1)
        }
        refresh()
        return amount
    }

    /**
     * Cộng thưởng xem video trong Bầu Cua: [videoRewardFor] lì xì hôm nay. Không giới hạn số lần,
     * người chơi muốn xem bao nhiêu tuỳ ý; chỉ giới hạn số xu mỗi lần bằng [VIDEO_REWARD_CAP].
     */
    fun claimVideoReward(): Int {
        val amount = videoRewardFor(computeStatus().todayReward)
        coinPrefs.addCoins(amount)
        return amount
    }

    var isReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_REMINDER_ENABLED, value) }

    /** Ngày (theo [TetCountdown.dayIndex]) người dùng mở app gần nhất, để thôi nhắc người đã bỏ app. */
    fun markAppOpenedToday() = prefs.edit { putLong(KEY_LAST_OPEN_DAY, today()) }

    fun daysSinceLastActive(): Long {
        val lastActive = maxOf(prefs.getLong(KEY_LAST_CLAIM_DAY, -1), prefs.getLong(KEY_LAST_OPEN_DAY, -1))
        return if (lastActive < 0) Long.MAX_VALUE else today() - lastActive
    }

    private fun today() = TetCountdown.dayIndex()

    private fun computeStatus(): Status {
        val today = today()
        val lastDay = prefs.getLong(KEY_LAST_CLAIM_DAY, -1)
        if (lastDay != today && coinPrefs.hasClaimedDailyGiftToday()) {
            // Đã nhận quà kiểu cũ trong Bầu Cua hôm nay: ghi nhận là ngày 1 để mai nối chuỗi ngày 2
            prefs.edit {
                putLong(KEY_LAST_CLAIM_DAY, today)
                putInt(KEY_LAST_CLAIM_DAY_NUMBER, 1)
                putInt(KEY_STREAK, 1)
            }
            return statusFor(today, today, 1, 1, legacyClaimedToday = false)
        }
        return statusFor(
            today = today,
            lastClaimDay = lastDay,
            lastClaimDayNumber = prefs.getInt(KEY_LAST_CLAIM_DAY_NUMBER, 0),
            storedStreak = prefs.getInt(KEY_STREAK, 0),
            legacyClaimedToday = lastDay != today && coinPrefs.hasClaimedDailyGiftToday()
        )
    }

    companion object {
        const val CYCLE = 7
        /**
         * Xu từng ngày trong chuỗi, thấp nhất 10, ngày 7 là lì xì lớn 100. Cả tuần 265 xu
         * (x2 nếu xem video mỗi ngày).
         */
        val REWARDS = intArrayOf(10, 15, 20, 30, 40, 50, 100)

        /** Mỗi lần xem quảng cáo nhận xu (nút x2 điểm danh, video trong Bầu Cua): x2 lì xì hôm nay, không quá mức này. */
        const val VIDEO_REWARD_CAP = 100

        fun videoRewardFor(todayReward: Int): Int = (todayReward * 2).coerceAtMost(VIDEO_REWARD_CAP)

        fun rewardFor(dayNumber: Int): Int = REWARDS[(dayNumber - 1).coerceIn(0, CYCLE - 1)]

        /**
         * Trạng thái điểm danh từ dữ liệu đã lưu (hàm thuần, có unit test).
         * [legacyClaimedToday]: hôm nay đã nhận quà kiểu cũ trong Bầu Cua (trước khi có điểm danh).
         */
        fun statusFor(
            today: Long,
            lastClaimDay: Long,
            lastClaimDayNumber: Int,
            storedStreak: Int,
            legacyClaimedToday: Boolean
        ): Status = when {
            lastClaimDay == today || legacyClaimedToday -> {
                val dayNumber = if (legacyClaimedToday) 1 else lastClaimDayNumber.coerceIn(1, CYCLE)
                val next = dayNumber % CYCLE + 1
                Status(
                    dayNumber = dayNumber,
                    claimedToday = true,
                    streak = if (legacyClaimedToday) 1 else storedStreak,
                    todayReward = rewardFor(dayNumber),
                    nextDayNumber = next,
                    nextReward = rewardFor(next)
                )
            }
            lastClaimDay == today - 1 -> {
                val dayNumber = lastClaimDayNumber.coerceIn(1, CYCLE) % CYCLE + 1
                Status(dayNumber, false, storedStreak, rewardFor(dayNumber), dayNumber, rewardFor(dayNumber))
            }
            // Chưa nhận bao giờ hoặc chuỗi đã đứt: claim() đếm lại từ ngày 1
            else -> Status(1, false, 0, rewardFor(1), 1, rewardFor(1))
        }

        private const val PREFS_NAME = "daily_checkin_prefs"
        private const val KEY_LAST_CLAIM_DAY = "last_claim_day"
        private const val KEY_LAST_CLAIM_DAY_NUMBER = "last_claim_day_number"
        private const val KEY_STREAK = "streak"
        private const val KEY_TOTAL_CLAIMS = "total_claims"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_LAST_OPEN_DAY = "last_open_day"
    }
}
