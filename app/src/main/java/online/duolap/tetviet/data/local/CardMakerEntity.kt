package online.duolap.tetviet.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "card_maker")
data class CardMakerEntity(
    @PrimaryKey
    val id: String,

    val url: String,

    val description: String? = null,

    /**
     * Đây là thông tin yêu cầu từ phía admin: Xem bao lượt quảng cáo để mở khoá tính năng
     */
    @ColumnInfo("required_ad_views")
    val requiredAdViews: Int = 0,

    /**
     * Đây là thông tin lượt quảng cáo user đã xem
     */
    @ColumnInfo("watch_ad_viewed")
    val watchAdViewed: Int = 0,

    @ColumnInfo("create_at")
    val createAt: Long = 0L,

    @ColumnInfo("cm_type")
    val cmType: Int,

    @ColumnInfo("access_type")
    val accessType: String = "free",

    @ColumnInfo("ratio")
    val ratio: String? = null,

    @ColumnInfo("is_unlocked")
    val isUnlocked: Boolean = false,

    @ColumnInfo("unlocked_at")
    val unlockedAt: Long? = null,

    /**
     * Lý do vì sao được mở khoá.
     *
     */
    @ColumnInfo("unlocked_reason")
    val unlockedReason: String = UnlockReason.DEFAULT_FREE.value,
)

enum class UnlockReason(val value: String) {
    /** Mặc định miễn phí cho mọi người dùng */
    DEFAULT_FREE("DEFAULT_FREE"),

    /** Mở khóa do đã xem đủ số lượt quảng cáo yêu cầu */
    WATCHED_ADS("WATCHED_ADS"),

    /** Mở khóa nhờ quyền lợi của gói VIP / Premium / Subscription */
    VIP_SUBSCRIPTION("VIP_SUBSCRIPTION"),
}
