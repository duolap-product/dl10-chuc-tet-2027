package online.duolap.tetviet.data.local

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import online.duolap.tetviet.data.model.WishCategory

@Keep
@Entity(tableName = "wishing_tree_wishes")
data class WishEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val category: String = WishCategory.HEALTH.name,
    val authorName: String = "Người gửi ẩn danh",
    val createdAt: Long = System.currentTimeMillis(),
    val isUserCreated: Boolean = false,
    val branchIndex: Int = 0,
    val tagColorType: Int = 0, // 0 = Cream, 1 = Red, 2 = Gold
    val blessingCount: Int = 0,
    /** Id document trên Firestore (wishing_tree_wishes); null = chỉ có trong máy (điều ước mẫu, điều ước cũ). */
    val remoteId: String? = null,
    /** Điều ước của người dùng: [SYNC_LOCAL] / [SYNC_PENDING] chờ tải lên / [SYNC_DONE] / [SYNC_FAILED]. */
    @ColumnInfo(defaultValue = "0") val syncState: Int = SYNC_LOCAL,
    /** Lá cộng đồng bị ẩn (do người dùng này báo cáo, hoặc đủ lượt báo cáo). */
    @ColumnInfo(defaultValue = "0") val isHidden: Boolean = false,
    /** Ngày treo theo giờ VN ([online.duolap.tetviet.widget.TetCountdown.dayIndex]). */
    @ColumnInfo(defaultValue = "0") val dayKey: Long = 0,
    /** Số lời chúc người dùng đã thấy, để báo "+N lời chúc mới" cho điều ước của mình. */
    @ColumnInfo(defaultValue = "0") val seenBlessingCount: Int = 0
) {
    companion object {
        const val SYNC_LOCAL = 0
        const val SYNC_PENDING = 1
        const val SYNC_DONE = 2
        const val SYNC_FAILED = 3
    }
}

