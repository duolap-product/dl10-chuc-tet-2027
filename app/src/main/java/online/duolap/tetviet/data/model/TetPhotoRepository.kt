package online.duolap.tetviet.data.model

import online.duolap.tetviet.R

object TetPhotoRepository {

    val categories = listOf(
        "Tất cả",
        "Hoa Xuân",
        "Không Khí Tết",
        "Giao Thừa",
        "Ẩm Thực Tết",
        "Sum Vầy"
    )

    val sampleImages = listOf(
        TetPhoto(
            id = "tet_img_01",
            title = "Hoa Mai Vàng Phú Quý",
            imageUrl = "https://images.unsplash.com/photo-1548625361-19597c4125b2?auto=format&fit=crop&w=1200&q=85",
            category = "Hoa Xuân",
            description = "Hoa mai vàng nở rộ đón xuân sang, mang tài lộc và phú quý đến mọi nhà.",
            localResId = R.drawable.bg_card_free_01
        ),
        TetPhoto(
            id = "tet_img_02",
            title = "Hoa Đào Miền Bắc Sắc Xuân",
            imageUrl = "https://images.unsplash.com/photo-1518895949257-7621c3c786d7?auto=format&fit=crop&w=1200&q=85",
            category = "Hoa Xuân",
            description = "Sắc hồng hoa đào thắm đượm không khí Tết truyền thống đất Bắc.",
            localResId = R.drawable.banner_tet_customs
        ),
        TetPhoto(
            id = "tet_img_03",
            title = "Pháo Hoa Rực Rỡ Đêm Giao Thừa",
            imageUrl = "https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=1200&q=85",
            category = "Giao Thừa",
            description = "Khoảnh khắc thiêng liêng chuyển giao năm cũ sang năm mới Bính Ngọ 2027.",
            localResId = R.drawable.img_tet_header_banner
        ),
        TetPhoto(
            id = "tet_img_04",
            title = "Phố Đèn Lồng Đỏ Đón Xuân",
            imageUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=1200&q=85",
            category = "Không Khí Tết",
            description = "Đèn lồng đỏ lung linh thắp sáng ước nguyện may mắn, bình an cho gia đình.",
            localResId = R.drawable.img_update_tet_header
        ),
        TetPhoto(
            id = "tet_img_05",
            title = "Bánh Chưng Xanh Ngày Tết",
            imageUrl = "https://images.unsplash.com/photo-1563245372-f21724e3856d?auto=format&fit=crop&w=1200&q=85",
            category = "Ẩm Thực Tết",
            description = "Bánh chưng xanh, dưa hành, câu đối đỏ - hương vị Tết đậm đà bản sắc dân tộc.",
            localResId = R.drawable.bg_card_free_02
        ),
        TetPhoto(
            id = "tet_img_06",
            title = "Mâm Ngũ Quả Cầu May",
            imageUrl = "https://images.unsplash.com/photo-1610832958506-aa56368176cf?auto=format&fit=crop&w=1200&q=85",
            category = "Ẩm Thực Tết",
            description = "Mâm ngũ quả ngày Tết dâng cúng tổ tiên, nguyện cầu năm mới sung túc, ấm no.",
            localResId = R.drawable.bg_card_free_03
        ),
        TetPhoto(
            id = "tet_img_07",
            title = "Chợ Hoa Xuân Rực Rỡ",
            imageUrl = "https://images.unsplash.com/photo-1526047932273-341f2a7631f9?auto=format&fit=crop&w=1200&q=85",
            category = "Không Khí Tết",
            description = "Người dân nô nức du xuân sắm Tết, chở sắc xuân rộn ràng về từng góc phố.",
            localResId = R.drawable.banner_tet_customs
        ),
        TetPhoto(
            id = "tet_img_08",
            title = "Gia Đình Sum Vầy Đón Tết",
            imageUrl = "https://images.unsplash.com/photo-1511632765486-a01980e01a18?auto=format&fit=crop&w=1200&q=85",
            category = "Sum Vầy",
            description = "Tết là dịp đoàn viên, sum họp bên bữa cơm gia đình đầm ấm tình thân.",
            localResId = R.drawable.img_update_tet_header
        ),
        TetPhoto(
            id = "tet_img_09",
            title = "Bao Lì Xì Đỏ May Mắn",
            imageUrl = "https://images.unsplash.com/photo-1577741314755-048d8525d31e?auto=format&fit=crop&w=1200&q=85",
            category = "Không Khí Tết",
            description = "Trao nhau phong bao lì xì đỏ thắm, gửi gắm lời chúc may mắn, vạn sự như ý.",
            localResId = R.drawable.bg_card_free_01
        ),
        TetPhoto(
            id = "tet_img_10",
            title = "Đêm Phố Cổ Hội An Lung Linh",
            imageUrl = "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=1200&q=85",
            category = "Không Khí Tết",
            description = "Hội An đêm xuân huyền ảo với dòng sông Hoài hoa đăng rực rỡ sắc màu.",
            localResId = R.drawable.img_tet_header_banner
        ),
        TetPhoto(
            id = "tet_img_11",
            title = "Cúc Mâm Xôi Vàng Óng",
            imageUrl = "https://images.unsplash.com/photo-1508615039623-a25605d2b022?auto=format&fit=crop&w=1200&q=85",
            category = "Hoa Xuân",
            description = "Những chậu cúc mâm xôi trổ vàng tươi tắn, biểu trưng cho sự đủ đầy, trường thọ.",
            localResId = R.drawable.bg_card_free_02
        ),
        TetPhoto(
            id = "tet_img_12",
            title = "Chào Xuân Bính Ngọ 2027",
            imageUrl = "https://images.unsplash.com/photo-1467810563316-b5476525c0f9?auto=format&fit=crop&w=1200&q=85",
            category = "Giao Thừa",
            description = "Chúc Mừng Năm Mới 2027 - Vạn Sự Như Ý, An Khang Thịnh Vượng, Phát Tài Phát Lộc!",
            localResId = R.drawable.img_update_tet_header
        )
    )

    fun getImagesByCategory(category: String): List<TetPhoto> {
        return if (category == "Tất cả") {
            sampleImages
        } else {
            sampleImages.filter { it.category == category }
        }
    }
}

