package online.duolap.tetviet.data.model

import java.io.Serializable

/**
 * Đại diện cho một bức ảnh trong bộ sưu tập "Hình ảnh Tết".
 *
 * @param id Định danh duy nhất của hình ảnh
 * @param title Tiêu đề hoặc tên bức ảnh (VD: "Hoa Mai Vàng Phú Quý")
 * @param imageUrl Đường dẫn URL trực tuyến (https://...) hoặc URI / đường dẫn cục bộ
 * @param category Thể loại (VD: "Hoa Xuân", "Phong Cảnh", "Giao Thừa", "Sum Vầy", "Mâm Cỗ")
 * @param description Câu đối, lời thơ hoặc mô tả ngắn mang đậm không khí Tết
 * @param localResId Resource ID dự phòng (nếu ảnh ngoại tuyến hoặc load lỗi)
 */
data class TetPhoto(
    val id: String,
    val title: String,
    val imageUrl: String,
    val category: String = "Tất cả",
    val description: String = "",
    val localResId: Int = 0
) : Serializable

