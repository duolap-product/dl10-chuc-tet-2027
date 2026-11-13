package online.duolap.tetviet.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import online.duolap.tetviet.R

enum class WishCategory(
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
    val colorHex: Long,
    @param:DrawableRes val defaultTagBgRes: Int
) {
    HEALTH(
        titleRes = R.string.wish_cat_health,
        iconRes = R.drawable.ic_wish_health,
        colorHex = 0xFFE53935,
        defaultTagBgRes = R.drawable.bg_wishing_tag_cream
    ),
    CAREER(
        titleRes = R.string.wish_cat_career,
        iconRes = R.drawable.ic_wish_career,
        colorHex = 0xFF8D5B4C,
        defaultTagBgRes = R.drawable.bg_wishing_tag_cream
    ),
    LOVE(
        titleRes = R.string.wish_cat_love,
        iconRes = R.drawable.ic_wish_love,
        colorHex = 0xFFE91E63,
        defaultTagBgRes = R.drawable.bg_wishing_tag_cream
    ),
    STUDY(
        titleRes = R.string.wish_cat_study,
        iconRes = R.drawable.ic_wish_study,
        colorHex = 0xFF5D4037,
        defaultTagBgRes = R.drawable.bg_wishing_tag_cream
    ),
    FINANCE(
        titleRes = R.string.wish_cat_finance,
        iconRes = R.drawable.ic_wish_finance,
        colorHex = 0xFFF57F17,
        defaultTagBgRes = R.drawable.bg_wishing_tag_cream
    ),
    FAMILY(
        titleRes = R.string.wish_cat_family,
        iconRes = R.drawable.ic_wish_family,
        colorHex = 0xFF5D4037,
        defaultTagBgRes = R.drawable.bg_wishing_tag_cream
    ),
    OTHER(
        titleRes = R.string.wish_cat_other,
        iconRes = R.drawable.ic_wish_other,
        colorHex = 0xFFFFA000,
        defaultTagBgRes = R.drawable.bg_wishing_tag_cream
    );

    companion object {
        fun fromName(name: String?): WishCategory {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: HEALTH
        }
    }
}

