package online.duolap.tetviet.data

import online.duolap.tetviet.R

enum class TetCustomCategory(val displayName: String) {
    ALL("Tất cả"),
    BEFORE_TET("Trước Tết"),
    NEW_YEAR_EVE("Giao thừa"),
    TET_DAYS("Ngày Tết"),
    AFTER_TET("Sau Tết")
}

data class TetCustomItem(
    val id: String,
    val title: String,
    val category: TetCustomCategory,
    val description: String,
    val iconResId: Int,
    val meaning: String,
    val ritual: String,
    val taboo: String
)

object TetCustomRepository {

    fun getAll(): List<TetCustomItem> = listOf(
        // 1. Nguồn gốc và ý nghĩa Tết
        TetCustomItem(
            id = "nguon_goc_tet",
            title = "Nguồn gốc và ý nghĩa Tết Nguyên Đán",
            category = TetCustomCategory.TET_DAYS,
            description = "Tết là thời điểm mở đầu năm âm lịch, đoàn viên gia đình, tưởng nhớ tổ tiên và gửi gắm ước vọng cho năm mới.",
            iconResId = R.drawable.ic_custom_blossom,
            meaning = "Tết Nguyên Đán là lễ hội cổ truyền lớn nhất của dân tộc Việt Nam. 'Nguyên' nghĩa là bắt đầu, 'Đán' là buổi sớm mai. Tết là dịp để mọi người tạ ơn đất trời, hướng về cội nguồn tổ tiên, thắt chặt tình thân gia đình sau một năm dài bôn ba.",
            ritual = "Chuẩn bị mâm cỗ thịnh soạn dâng cúng tổ tiên, con cháu quây quần quanh mâm cơm giao thừa, chúc thọ ông bà cha mẹ và mừng tuổi trẻ nhỏ bằng phong bao đỏ.",
            taboo = "Giữ tâm thế hoan hỉ, nói lời hay ý đẹp, không cãi vã, trách mắng hay buồn rầu trong những ngày đầu năm mới để cả năm luôn thuận hòa, may mắn."
        ),

        // 2. Ông Công Ông Táo
        TetCustomItem(
            id = "ong_cong_ong_tao",
            title = "Ông Công Ông Táo – 23 tháng Chạp",
            category = TetCustomCategory.BEFORE_TET,
            description = "Ngày 23 tháng Chạp là mốc quan trọng mở đầu chuỗi chuẩn bị Tết trong nhiều gia đình Việt.",
            iconResId = R.drawable.ic_custom_stove,
            meaning = "Thần Bếp (Táo Quân) định đoạt cát hung của gia đình, cưỡi cá chép bay về chầu trời để tâu bày việc làm trong năm với Ngọc Hoàng Thượng Đế.",
            ritual = "Lễ cúng trước 12h trưa ngày 23 tháng Chạp gồm: bộ ba mũ Táo quân, mâm cỗ mặn/chay, hoa quả, vàng mã và cá chép đỏ sống để phóng sinh tiễn Táo quân.",
            taboo = "Không cúng sau giờ Ngọ (13h) ngày 23 tháng Chạp; khi thả cá chép thả nhẹ nhàng sát mặt nước, không vứt bọc nilon bừa bãi."
        ),

        // 3. Tảo mộ gia tộc
        TetCustomItem(
            id = "tao_mo_gia_toc",
            title = "Tảo mộ và viếng nghĩa trang gia tộc",
            category = TetCustomCategory.BEFORE_TET,
            description = "Con cháu tụ họp về quê hương, sửa sang phần mộ tổ tiên, mời ông bà tiền nhân về ăn Tết cùng con cháu.",
            iconResId = R.drawable.ic_custom_temple,
            meaning = "Thể hiện đạo lý 'Uống nước nhớ nguồn', tỏ lòng hiếu thảo, kính trọng các bậc sinh thành và tiền nhân đã khuất.",
            ritual = "Dọn sạch cỏ dại, đắp thêm đất mới hoặc quét vôi mộ phần, thắp nén hương thơm, dâng hoa tươi và khấn mời linh hồn gia tiên về ngự tại bàn thờ gia đình đón xuân.",
            taboo = "Đi đứng trang nghiêm, không cười đùa huyên náo, tránh dẫm đạp lên các ngôi mộ xung quanh trong nghĩa trang."
        ),

        // 4. Dọn dẹp và trang hoàng nhà cửa
        TetCustomItem(
            id = "don_dep_nha_cua",
            title = "Dọn dẹp, trang hoàng nhà cửa đón Tết",
            category = TetCustomCategory.BEFORE_TET,
            description = "Quét sạch bụi bặm của năm cũ, tân trang lại tổ ấm để đón luồng sinh khí tài lộc của năm mới.",
            iconResId = R.drawable.ic_custom_broom,
            meaning = "Quét dọn những điều không may mắn, xui rủi của năm cũ, tạo không gian thanh tịnh, ấm cúng và rước vượng khí vào nhà.",
            ritual = "Lau chùi cửa nẻo, bao sái lư hương bằng nước thơm (ngũ vị hương), treo đèn lồng, dán câu đối đỏ và bày biện hoa tươi khắp phòng khách.",
            taboo = "Hoàn tất việc dọn dẹp trước chiều 30 Tết; tránh làm đổ vỡ đồ dùng, bát đĩa khi dọn nhà cuối năm."
        ),

        // 5. Gói bánh Chưng bánh Tét
        TetCustomItem(
            id = "banh_chung_banh_tet",
            title = "Gói bánh Chưng, nấu bánh Tét sum vầy",
            category = TetCustomCategory.BEFORE_TET,
            description = "Bánh Chưng vuông miền Bắc tượng trưng cho Đất, bánh Tét tròn miền Nam tượng trưng cho Trời và sự đùm bọc.",
            iconResId = R.drawable.ic_custom_banh_chung,
            meaning = "Gắn liền với truyền thuyết Lang Liêu thời Hùng Vương, tượng trưng cho lòng hiếu thảo, đất trời che chở và tinh hoa nền nông nghiệp lúa nước.",
            ritual = "Cả gia đình quây quần chọn lá dong xanh, gạo nếp cái hoa vàng, đỗ xanh và thịt mỡ, thức thâu đêm trông nồi bánh sôi sùng sục bên bếp lửa ấm.",
            taboo = "Gói bánh đều tay, luộc đủ thời gian (10-12 tiếng) và châm nước nóng liên tục để hạt nếp dền dẻo, không bị sượng."
        ),

        // 6. Chợ hoa xuân
        TetCustomItem(
            id = "cho_hoa_xuan",
            title = "Chợ hoa Tết: Đào, Mai, Quất rực rỡ",
            category = TetCustomCategory.BEFORE_TET,
            description = "Hoa đào miền Bắc xua đuổi tà ma, hoa mai miền Nam mang lại phú quý, quất cảnh trĩu quả tượng trưng tài lộc dồi dào.",
            iconResId = R.drawable.ic_custom_blossom,
            meaning = "Sắc hoa ngày Tết mang lại sinh khí bừng nở cho ngôi nhà, khởi đầu một mùa xuân căng tràn nhựa sống, bình an và thịnh vượng.",
            ritual = "Cả nhà cùng nhau dạo chợ xuân từ 25 đến 29 tháng Chạp, chọn cành đào thắm nụ, chậu mai vàng rộ cánh và cây quất xum xuê đủ hoa, quả, lộc non.",
            taboo = "Tránh chọn cây héo úa hoặc cành gãy dập; chăm sóc giữ nước để hoa nở đúng khoảnh khắc giao thừa."
        ),

        // 7. Mâm ngũ quả
        TetCustomItem(
            id = "mam_ngu_qua",
            title = "Bày mâm ngũ quả ngày xuân theo 3 miền",
            category = TetCustomCategory.BEFORE_TET,
            description = "Mâm ngũ quả đại diện cho ngũ hành Kim - Mộc - Thủy - Hỏa - Thổ, thể hiện ước vọng sung túc, ấm no.",
            iconResId = R.drawable.ic_custom_blossom,
            meaning = "Miền Bắc chuộng Chuối, Bưởi, Đào, Hồng, Quýt; miền Nam chuộng 'Cầu - Vừa - Đủ - Xài' (Mãng cầu, Dừa, Đu đủ, Xoài).",
            ritual = "Rửa sạch lau khô từng quả, xếp nải chuối/dừa làm chân đế vững chắc, đặt quả màu sắc nổi bật ở giữa và cài thêm các trái nhỏ xung quanh.",
            taboo = "Miền Nam kiêng chuối (chúi nhủi), cam quýt (quýt làm cam chịu), lê (lê lết), táo (bom)."
        ),

        // 8. Cúng Tất niên
        TetCustomItem(
            id = "cung_tat_nien",
            title = "Cúng Tất niên chiều 30 Tết",
            category = TetCustomCategory.BEFORE_TET,
            description = "Nghi lễ tạ ơn thần linh và mời tiên tổ về chung vui cùng con cháu trong bữa cơm tất niên chiều cuối năm.",
            iconResId = R.drawable.ic_custom_lantern,
            meaning = "Kết thúc năm cũ, xua đi những muộn phiền âu lo, gắn kết các thành viên trong gia đình trong không khí ấm cúng trước thềm năm mới.",
            ritual = "Chuẩn bị mâm cỗ mặn đặt trên bàn thờ gia tiên, gia chủ thành tâm dâng hương báo cáo một năm đã qua và tạ ơn sự phù hộ độ trì.",
            taboo = "Cả gia đình dùng bữa trong hòa thuận, gác lại mọi bất đồng, nói lời chúc tụng chân thành."
        ),

        // 9. Cúng Giao thừa (Trừ Tịch)
        TetCustomItem(
            id = "cung_giao_thua",
            title = "Nghi thức cúng Giao thừa (Trừ Tịch)",
            category = TetCustomCategory.NEW_YEAR_EVE,
            description = "Khoảnh khắc chuyển giao giữa năm cũ và năm mới, tiễn vị Hành khiển cũ và nghênh đón vị Hành khiển mới.",
            iconResId = R.drawable.ic_custom_lantern,
            meaning = "Giao thừa nghĩa là cũ qua mới lại. Trừ Tịch nghĩa là trừ khử bóng tối, tà khí của năm cũ để đón ánh sáng hỷ lạc của năm mới.",
            ritual = "Thực hiện hai lễ cúng: cúng ngoài trời trước để tạ thần linh cai quản vũ trụ, sau đó cúng trong nhà để dâng hương kính cáo gia tiên.",
            taboo = "Thời điểm cúng chuẩn xác lúc 0h đêm giao thừa; giữ trang phục chỉn chu, thành kính thắp nén hương trầm."
        ),

        // 10. Xông đất đầu năm
        TetCustomItem(
            id = "xong_dat",
            title = "Phong tục Xông đất đầu năm",
            category = TetCustomCategory.NEW_YEAR_EVE,
            description = "Người đầu tiên bước vào cửa nhà sau giao thừa được xem là người mang lại vận mệnh, tài lộc cho cả năm của gia chủ.",
            iconResId = R.drawable.ic_custom_red_packet,
            meaning = "Người xưa tin rằng người xông đất hợp tuổi, tính tình phóng khoáng, đức độ và thành đạt sẽ mang lại phúc khí dồi dào cho gia đình suốt 365 ngày.",
            ritual = "Người được nhờ xông đất đến nhà ngay sau giao thừa hoặc sáng mùng 1, mang theo bao lì xì đỏ chúc phúc cho các thành viên.",
            taboo = "Người đang có tang hoặc gia cảnh gặp việc buồn không nên xông nhà người khác trong những ngày đầu xuân."
        ),

        // 11. Hái lộc đầu xuân
        TetCustomItem(
            id = "hai_loc_dau_xuan",
            title = "Hái lộc đầu xuân – Rước phúc vào nhà",
            category = TetCustomCategory.NEW_YEAR_EVE,
            description = "Nghi thức xin một cành lộc non hay nhành hoa tươi từ đền chùa mang về nhà để biểu trưng cho sự đâm chồi nảy lộc.",
            iconResId = R.drawable.ic_custom_blossom,
            meaning = "Rước lộc Trời Phật ban tặng, cầu chúc cho việc kinh doanh, học tập và sức khỏe của gia đạo luôn sinh sôi phát đạt.",
            ritual = "Sau khi lễ chùa đêm giao thừa, xin một bao diêm, một túi muối nhỏ hoặc cành lộc nhỏ đặt trang trọng lên bàn thờ gia đình.",
            taboo = "Không bẻ cành bẻ cây lớn làm hủy hoại cảnh quan môi trường; chỉ nên xin lộc tượng trưng thanh tịnh."
        ),

        // 12. Xuất hành đầu xuân
        TetCustomItem(
            id = "xuat_hanh",
            title = "Xuất hành đầu xuân chọn hướng cát tường",
            category = TetCustomCategory.TET_DAYS,
            description = "Bước chân ra khỏi nhà lần đầu tiên trong năm theo giờ hoàng đạo và hướng tốt để đón Hỷ Thần hoặc Tài Thần.",
            iconResId = R.drawable.ic_custom_temple,
            meaning = "Tạo bước khởi đầu hanh thông, cả năm đi đến nơi về đến chốn bình an, công việc trôi chảy thuận lợi.",
            ritual = "Xem lịch âm tra giờ hoàng đạo sáng mùng 1, chọn hướng Hỷ Thần (mang niềm vui) hoặc Tài Thần (mang tài lộc) để bước đi đầu tiên.",
            taboo = "Tránh xuất hành theo hướng Hạc Thần (hướng xấu); giữ tâm thái vui tươi, không to tiếng cãi vã khi ra đường."
        ),

        // 13. Hương khói sáng mùng 1
        TetCustomItem(
            id = "huong_khoi_mung_mot",
            title = "Hương khói tổ tiên sáng mùng 1 Tết",
            category = TetCustomCategory.TET_DAYS,
            description = "Nghi lễ trang trọng đầu tiên của ngày đầu năm, thắp tuần hương mới dâng nước thanh tịnh tạ ơn tiền nhân.",
            iconResId = R.drawable.ic_custom_temple,
            meaning = "Sự kết nối vô hình nhưng thiêng liêng giữa con cháu và tổ tiên nguồn cội, nhắc nhở lòng hiếu nghĩa muôn đời.",
            ritual = "Dâng trà ấm, nước thanh tịnh, thắp 3 nén nhang trầm thơm, các thế hệ cùng tề tựu cúi đầu bái lạy bàn thờ tổ tiên.",
            taboo = "Không để bàn thờ tối tăm hay tắt hương; nói năng nhỏ nhẹ, kính cẩn trong không gian thờ tự."
        ),

        // 14. Mừng tuổi lì xì
        TetCustomItem(
            id = "mung_tuoi_li_xi",
            title = "Mừng tuổi lì xì đầu năm may mắn",
            category = TetCustomCategory.TET_DAYS,
            description = "Trao những phong bao màu đỏ thắm chứa tiền may mắn kèm lời chúc sức khỏe, an khang, học giỏi.",
            iconResId = R.drawable.ic_custom_red_packet,
            meaning = "Màu đỏ tượng trưng cho cát tường, trừ tà và may mắn. Giá trị không nằm ở số tiền mà ở lời chúc và tình cảm thương yêu trao gửi.",
            ritual = "Trẻ em khoanh tay chúc thọ ông bà cha mẹ, người lớn trao bao lì xì kèm nụ cười rạng rỡ và những lời khuyên dạy ân cần.",
            taboo = "Không mở bao lì xì trước mặt người tặng; dùng tiền mới, phẳng phiu đặt trong bao thư chỉn chu."
        ),

        // 15. Mùng 1 Tết cha, Mùng 2 Tết mẹ, Mùng 3 Tết thầy
        TetCustomItem(
            id = "tuc_chuc_tet_3_ngay",
            title = "Mùng 1 Tết cha, Mùng 2 Tết mẹ, Mùng 3 Tết thầy",
            category = TetCustomCategory.TET_DAYS,
            description = "Lịch trình chúc Tết chuẩn mực truyền thống: tri ân họ nội, họ ngoại và người thầy khai tâm mở trí.",
            iconResId = R.drawable.ic_custom_blossom,
            meaning = "Đề cao giá trị gia đình và tinh thần 'Tôn sư trọng đạo'. Một trật tự tình cảm đầy nhân văn của người Việt Nam.",
            ritual = "Mùng 1 quây quần bên họ nội; mùng 2 sang thăm chúc thọ bên họ ngoại; mùng 3 cùng bạn hữu về thăm thầy cô giáo xưa.",
            taboo = "Không chúc Tết nhà có tang; tránh đến chơi quá sớm làm phiền giấc ngủ nghỉ ngày xuân của gia chủ."
        ),

        // 16. Đi lễ chùa đầu năm
        TetCustomItem(
            id = "di_le_chua",
            title = "Đi lễ chùa cầu quốc thái dân an",
            category = TetCustomCategory.TET_DAYS,
            description = "Đến chốn thiền môn thanh tịnh thắp nén hương thơm, gửi gắm ước nguyện bình an, sức khỏe và tâm hồn thanh thản.",
            iconResId = R.drawable.ic_custom_temple,
            meaning = "Gột rửa bụi trần của năm cũ, tìm về sự an lạc trong tâm hồn, hướng thiện và cầu bình an cho bản thân cùng chúng sinh.",
            ritual = "Ăn mặc trang phục kín đáo, lịch sự; dâng hương hoa chay tịnh; chắp tay thành kính niệm Phật và cầu phúc.",
            taboo = "Không mặc đồ ngắn, hở hang; không dâng cỗ mặn vào chính điện Tam Bảo; không chen lấn xô đẩy."
        ),

        // 17. Khai bút đầu xuân
        TetCustomItem(
            id = "khai_but_dau_xuan",
            title = "Khai bút đầu xuân – Rước chữ cầu đăng khoa",
            category = TetCustomCategory.TET_DAYS,
            description = "Phong tục đẹp của giới trí thức, học sinh sinh viên: đặt nét bút đầu tiên của năm viết nên những câu thơ, lời chúc tốt lành.",
            iconResId = R.drawable.ic_custom_blossom,
            meaning = "Tôn vinh sự học, cầu mong năm mới học hành tấn tới, thi cử đỗ đạt, sự nghiệp sáng lạn và trí tuệ hanh thông.",
            ritual = "Chọn ngày lành giờ đẹp (thường mùng 2 hoặc mùng 4), rửa tay sạch sẽ, nắn nót viết chữ đẹp hoặc xin chữ thầy đồ (Nhân, Tâm, Đức, Trí, Phúc).",
            taboo = "Tránh làm lem mực, rách giấy hoặc viết những từ ngữ tiêu cực trong lần khai bút đầu tiên."
        ),

        // 18. Mâm cơm Tết 3 miền
        TetCustomItem(
            id = "mam_com_tet",
            title = "Mâm cơm cúng gia tiên 3 ngày Tết",
            category = TetCustomCategory.TET_DAYS,
            description = "Mỗi miền mang một nét vị độc đáo: canh bóng, giò thủ (Bắc); thịt kho tàu, canh khổ qua (Nam); tré, nem chua (Trung).",
            iconResId = R.drawable.ic_custom_banh_chung,
            meaning = "Bữa cơm dâng lên cúng tổ tiên mỗi bữa trong 3 ngày Tết thể hiện lòng thành kính và sợi dây gắn kết gia đình bền chặt.",
            ritual = "Nấu nướng tươm tất dâng lên bàn thờ trước khi cả gia đình cùng ăn cơm sum họp.",
            taboo = "Thức ăn dâng cúng phải là món mới nấu, không được nếm trước khi cúng tế."
        ),

        // 19. Trò chơi dân gian
        TetCustomItem(
            id = "tro_choi_dan_gian",
            title = "Trò chơi dân gian ngày xuân náo nhiệt",
            category = TetCustomCategory.TET_DAYS,
            description = "Kéo co, đập niêu đất, cờ người, ném còn, đua thuyền rộn rã khắp các sân đình và làng quê Việt Nam.",
            iconResId = R.drawable.ic_custom_lantern,
            meaning = "Gắn kết tình làng nghĩa xóm, rèn luyện sức khỏe, mang lại tiếng cười sảng khoái và may mắn cho cộng đồng.",
            ritual = "Thanh niên trai tráng cùng bà con làng xóm tham gia thi đấu tại lễ hội đầu làng.",
            taboo = "Thi đấu với tinh thần thượng võ, vui vẻ giao lưu, không cay cú ăn thua hay cá cược tiền bạc."
        ),

        // 20. Kiêng kỵ đầu năm
        TetCustomItem(
            id = "kieng_ky_dau_nam",
            title = "Kiêng kỵ đầu năm theo quan niệm dân gian",
            category = TetCustomCategory.TET_DAYS,
            description = "Những kinh nghiệm dân gian gìn giữ vận may: kiêng quét rác, kiêng làm vỡ bát đĩa, kiêng mặc đồ trắng đen tang tóc.",
            iconResId = R.drawable.ic_custom_broom,
            meaning = "Quét nhà sáng mùng 1 sợ quét mất Thần Tài tài lộc ra khỏi cửa; bát đĩa vỡ sợ điềm chia lìa rạn nứt.",
            ritual = "Gom rác vào một góc nhà trong 3 ngày Tết, dọn nhẹ nhàng; cẩn thận khi sử dụng đồ sứ thủy tinh.",
            taboo = "Tránh vay mượn tiền bạc, tránh đòi nợ đầu năm; tránh tranh cãi, nói tục chửi bậy."
        ),

        // 21. Hóa vàng tiễn tổ tiên
        TetCustomItem(
            id = "hoa_vang",
            title = "Lễ Tạ năm mới – Hóa vàng tiễn tổ tiên",
            category = TetCustomCategory.TET_DAYS,
            description = "Thực hiện vào mùng 3 hoặc mùng 4 Tết để tạ ơn và tiễn đưa hương hồn ông bà tổ tiên về lại cõi âm sau những ngày ngự ăn Tết.",
            iconResId = R.drawable.ic_custom_stove,
            meaning = "Kết thúc kỳ nghỉ Tết chính thức, bày tỏ lòng biết ơn sự hiện diện ấm áp của tiền nhân trong những ngày Tết cổ truyền.",
            ritual = "Chuẩn bị mâm cơm cúng tạ, đốt vàng mã, khấn vái tạ ơn và cầu xin tổ tiên tiếp tục che chở gia đạo bình an suốt năm mới.",
            taboo = "Đốt vàng mã cẩn thận, chờ cháy hết mới tưới chén rượu lên tro theo đúng phong tục cổ truyền."
        ),

        // 22. Khai xuân mở hàng
        TetCustomItem(
            id = "khai_xuan_mo_hang",
            title = "Khai xuân mở hàng, đón lộc kinh doanh",
            category = TetCustomCategory.AFTER_TET,
            description = "Các cửa hàng, doanh nghiệp chọn ngày lành tháng tốt sau Tết để mở cửa khai trương, đón những vị khách đầu tiên.",
            iconResId = R.drawable.ic_custom_red_packet,
            meaning = "Cầu mong năm mới buôn may bán đắt, khách hàng tấp nập, tài lộc hanh thông và ký kết hợp đồng suôn sẻ.",
            ritual = "Làm lễ cúng mở hàng tại cửa hàng, chuẩn bị bao lì xì may mắn tặng cho khách hàng mở hàng đầu tiên.",
            taboo = "Chọn khách mở hàng xởi lởi, vui vẻ; tránh đổi trả hàng hóa gay gắt trong ngày mở hàng đầu năm."
        ),

        // 23. Du xuân trẩy hội
        TetCustomItem(
            id = "du_xuan_tray_hoi",
            title = "Du xuân trẩy hội mùa lễ hội đầu năm",
            category = TetCustomCategory.AFTER_TET,
            description = "Hành hương về các danh lam thắng cảnh, lễ hội chùa Hương, Yên Tử, hội đền Hùng, hội Lim quan họ.",
            iconResId = R.drawable.ic_custom_lantern,
            meaning = "Thưởng ngoạn cảnh sắc non sông gấm vóc lúc giao mùa, giữ gìn và phát huy bản sắc văn hóa dân tộc ngàn năm.",
            ritual = "Chuẩn bị tâm thế an nhiên, thưởng thức câu ca điệu hát truyền thống và hòa mình vào không gian văn hóa lễ hội.",
            taboo = "Tôn trọng các quy định tại di tích lịch sử; bảo vệ cảnh quan thiên nhiên và giữ gìn nếp sống văn minh."
        ),

        // 24. Lễ hội Tịch Điền
        TetCustomItem(
            id = "tich_dien",
            title = "Lễ hội Tịch Điền – Xuống đồng đầu năm",
            category = TetCustomCategory.AFTER_TET,
            description = "Bắt nguồn từ thời vua Lê Đại Hành cày ruộng đầu xuân, nhắc nhở người dân trọng nghề nông và chăm chỉ lao động.",
            iconResId = R.drawable.ic_custom_blossom,
            meaning = "Cầu mưa thuận gió hòa, mùa màng bội thu, khuyến nông khuyến học và tri ân những người lao động tạo ra hạt gạo nuôi đời.",
            ritual = "Lễ tế thần Nông, rước kiệu và nghi thức đường cày mở đầu vụ mùa mới của các vị bô lão uy tín.",
            taboo = "Lao động bằng sự hăng say, trung thực, tránh lười biếng hay buông xuôi sau kỳ nghỉ Tết kéo dài."
        ),

        // 25. Tết Nguyên Tiêu
        TetCustomItem(
            id = "nguyen_tieu",
            title = "Tết Nguyên Tiêu – Rằm tháng Giêng trọn vẹn",
            category = TetCustomCategory.AFTER_TET,
            description = "Đêm trăng tròn đầu tiên của năm âm lịch: 'Lễ Phật quanh năm không bằng ngày rằm tháng Giêng'.",
            iconResId = R.drawable.ic_custom_lantern,
            meaning = "Đánh dấu sự kết thúc trọn vẹn của tháng Tết khởi đầu năm mới, ước nguyện sự viên mãn, tròn đầy như ánh trăng rằm.",
            ritual = "Dâng hương lễ Phật cầu an, thả đèn hoa đăng lung linh trên dòng sông, quây quần ăn bánh trôi nước (thang viên).",
            taboo = "Giữ tâm thanh tịnh, hướng thiện, phóng sinh các loài thủy tộc để tích phúc lành cho gia đình."
        )
    )
}

