package online.duolap.tetviet.ads

/**
 * Các vị trí hiển thị quảng cáo trong app, dùng để đo hiệu quả từng vị trí trên Firebase Analytics.
 */
enum class AdPlacement(val key: String) {
    // Rewarded
    UNLOCK_FEATURE("unlock_feature"),
    BAUCUA_DOUBLE_GIFT("baucua_double_gift"),
    BAUCUA_EXTRA_COINS("baucua_extra_coins"),
    DAP_CHUOT_REVIVE("dap_chuot_revive"),
    DAP_CHUOT_DOUBLE_COINS("dap_chuot_double_coins"),
    OAN_QUAN_HINT("oan_quan_hint"),
    OAN_QUAN_UNDO("oan_quan_undo"),
    OAN_QUAN_DOUBLE_COINS("oan_quan_double_coins"),
    DAILY_CHECKIN_DOUBLE("daily_checkin_double"),
    DE_PHI_REVIVE("de_phi_revive"),
    WISHING_EXTRA_WISH("wishing_extra_wish"),
    DE_PHI_DOUBLE_COINS("de_phi_double_coins"),

    // Interstitial
    CARD_SAVED("card_saved"),
    LUCKY_WHEEL_SPIN("lucky_wheel_spin"),
    DAP_CHUOT_BREAK("dap_chuot_break"),
    OAN_QUAN_BREAK("oan_quan_break"),
    WISHING_LANTERN_BREAK("wishing_lantern_break"),
    DE_PHI_BREAK("de_phi_break"),

    // Banner (chỉ đặt ở màn hình đọc/lướt nội dung, không đặt ở game hay màn hình thao tác nhiều)
    BANNER_TET_CUSTOMS("banner_tet_customs"),
    BANNER_TET_PHOTOS("banner_tet_photos"),
    BANNER_MUSIC_SELECTION("banner_music_selection"),

    // App Open (khi người dùng quay lại app từ nền)
    APP_OPEN_RESUME("app_open_resume")
}
