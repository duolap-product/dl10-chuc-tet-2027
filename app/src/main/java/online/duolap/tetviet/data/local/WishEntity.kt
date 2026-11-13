package online.duolap.tetviet.data.local

import androidx.annotation.Keep
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
    val blessingCount: Int = 0
)

