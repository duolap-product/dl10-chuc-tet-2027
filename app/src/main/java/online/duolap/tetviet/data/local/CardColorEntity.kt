package online.duolap.tetviet.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bảng Room lưu trữ danh sách mã màu tạo thiệp (tetColors)
 * Được đồng bộ từ collection Firestore `tao_thiep_mau_sac`.
 */
@Entity(tableName = "card_color")
data class CardColorEntity(
    @PrimaryKey
    val id: String,

    val name: String,

    @ColumnInfo("color_hex")
    val colorHex: String,

    @ColumnInfo("order_index")
    val orderIndex: Int = 0
)

