package online.duolap.tetviet.data.sync

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

/**
 * Quản lý thời điểm đồng bộ gần nhất (Timestamp & Date) để tối ưu hóa lượt đọc Firestore.
 * Đảm bảo chỉ đồng bộ thực sự 1 lần / 1 ngày.
 */
class AppSyncPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_firestore_sync_prefs", Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Thời điểm (milis wall-clock) của lần đồng bộ thành công gần nhất.
     */
    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        set(value) {
            prefs.edit {
                putLong(KEY_LAST_SYNC_TIMESTAMP, value)
                    .putString(KEY_LAST_SYNC_DATE, dateFormat.format(Date(value)))
            }
        }

    /**
     * Mốc thời gian updatedAt lớn nhất của các documents đã tải về, phục vụ truy vấn tăng dần (incremental query).
     */
    var lastDocUpdatedAt: Long
        get() = prefs.getLong(KEY_LAST_DOC_UPDATED_AT, prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L))
        set(value) {
            prefs.edit {
                putLong(KEY_LAST_DOC_UPDATED_AT, value)
            }
        }

    val lastSyncDateFormatted: String
        get() {
            val ts = lastSyncTimestamp
            return if (ts > 0L) dateFormat.format(Date(ts)) else "Chưa từng đồng bộ"
        }

    fun getLastSyncTimestamp(collectionName: String): Long {
        return prefs.getLong("${KEY_LAST_SYNC_TIMESTAMP}_$collectionName", 0L)
    }

    fun setLastSyncTimestamp(collectionName: String, value: Long) {
        lastSyncTimestamp = value
        prefs.edit {
            putLong("${KEY_LAST_SYNC_TIMESTAMP}_$collectionName", value)
                .putString("${KEY_LAST_SYNC_DATE}_$collectionName", dateFormat.format(Date(value)))
        }
    }

    fun getLastSyncDateFormatted(collectionName: String): String {
        val ts = getLastSyncTimestamp(collectionName)
        return if (ts > 0L) dateFormat.format(Date(ts)) else "Chưa từng đồng bộ"
    }

    /**
     * Kiểm tra xem có nên thực hiện đồng bộ hay không (1 lần / 1 ngày).
     * @param forceRefresh Nếu true, bỏ qua kiểm tra và bắt buộc đồng bộ ngay lập tức.
     */
    fun shouldSync(forceRefresh: Boolean = false): Boolean {
        return shouldSync("", forceRefresh)
    }

    fun shouldSync(collectionName: String, forceRefresh: Boolean = false): Boolean {
        if (forceRefresh) return true

        val lastSync = if (collectionName.isEmpty()) lastSyncTimestamp else getLastSyncTimestamp(collectionName)
        if (lastSync <= 0L) {
            // Chưa từng đồng bộ bao giờ -> Cần đồng bộ lần đầu
            return true
        }

        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastSync

        // Trường hợp thời gian bị lùi (ví dụ do chỉnh tay đồng hồ) -> cho phép đồng bộ
        if (timeDiff < 0) return true

        // Nếu đã qua 24 giờ kể từ lần đồng bộ trước -> Chắc chắn cần đồng bộ
        if (timeDiff >= ONE_DAY_MILLIS) return true

        // Kiểm tra xem có cùng ngày theo lịch (Calendar Day) hay không
        val isSameDay = isSameCalendarDay(lastSync, currentTime)
        if (isSameDay) {
            // Đã đồng bộ trong ngày hôm nay rồi -> Bỏ qua
            return false
        }

        // Nếu sang ngày mới, nhưng thời gian cách lần trước chưa đủ MIN_INTERVAL (ví dụ vừa đồng bộ lúc 23:59 mà 00:05 mở lại)
        // thì chờ đủ ít nhất 4 tiếng để tránh spam server Firestore
        return timeDiff >= MIN_SYNC_INTERVAL_MILLIS
    }

    private fun isSameCalendarDay(time1: Long, time2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        private const val KEY_LAST_SYNC_TIMESTAMP = "key_last_sync_timestamp"
        private const val KEY_LAST_SYNC_DATE = "key_last_sync_date"
        private const val KEY_LAST_DOC_UPDATED_AT = "key_last_doc_updated_at"

        val ONE_DAY_MILLIS = TimeUnit.DAYS.toMillis(1) // 24 giờ = 86,400,000 ms
        val MIN_SYNC_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(4) // 4 giờ
    }
}
