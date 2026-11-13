package online.duolap.tetviet.ads

/**
 * Các vị trí hiển thị quảng cáo trong app, dùng để đo hiệu quả từng vị trí trên Firebase Analytics.
 */
enum class AdPlacement(val key: String) {
    // Rewarded
    UNLOCK_FEATURE("unlock_feature"),
    BAUCUA_DOUBLE_GIFT("baucua_double_gift"),
    BAUCUA_EXTRA_COINS("baucua_extra_coins"),

    // Interstitial
    CARD_SAVED("card_saved"),
    LUCKY_WHEEL_SPIN("lucky_wheel_spin"),

    // Banner (chỉ đặt ở màn hình đọc/lướt nội dung, không đặt ở game hay màn hình thao tác nhiều)
    BANNER_TET_CUSTOMS("banner_tet_customs"),
    BANNER_TET_PHOTOS("banner_tet_photos"),
    BANNER_MUSIC_SELECTION("banner_music_selection"),

    // App Open (khi người dùng quay lại app từ nền)
    APP_OPEN_RESUME("app_open_resume")
}
