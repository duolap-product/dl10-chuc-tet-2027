package online.duolap.tetviet.data.model

import android.content.Context
import androidx.annotation.DrawableRes
import online.duolap.tetviet.R
import online.duolap.tetviet.data.local.CardColorEntity
import online.duolap.tetviet.data.local.CardMakerEntity

data class CardBackground(
    val id: String,
    @field:DrawableRes
    val resId: Int = 0,
    val ratio: String = "1024:1434",
    val isFree: Boolean = true,
    val isAds: Boolean = false,
    val url: String? = null
)

object CardBackgroundManager {
    val BACKGROUND = listOf(
        CardBackground(
            id = "bg_free_01",
            resId = R.drawable.bg_card_free_01,
            ratio = "1024:1434",
            isFree = true,
            url = "bg_card_free_01"
        ),
        CardBackground(
            id = "bg_free_02",
            resId = R.drawable.bg_card_free_02,
            ratio = "1024:731",
            isFree = true,
            url = "bg_card_free_02"
        ),
        CardBackground(
            id = "bg_free_03",
            resId = R.drawable.bg_card_free_03,
            ratio = "1024:1434",
            isFree = true,
            url = "bg_card_free_03"
        ),
        CardBackground(
            id = "bg_free_04",
            resId = R.drawable.bg_card_free_04,
            ratio = "1024:1434",
            isFree = true,
            url = "bg_card_free_04"
        ),
        CardBackground(
            id = "bg_free_05",
            resId = R.drawable.bg_card_free_05,
            ratio = "1024:1434",
            isFree = true,
            url = "bg_card_free_05"
        ),
        CardBackground(
            id = "bg_free_06",
            resId = R.drawable.bg_card_free_06,
            ratio = "1024:1434",
            isFree = true,
            url = "bg_card_free_06"
        ),
        CardBackground(
            id = "bg_free_07",
            resId = R.drawable.bg_card_free_07,
            ratio = "1024:1434",
            isFree = true,
            url = "bg_card_free_07"
        ),
        CardBackground(
            id = "bg_vip_01",
            resId = R.drawable.bg_card_vip_01,
            ratio = "1024:731",
            isFree = true,
            url = "bg_card_vip_01"
        ),
        CardBackground(
            id = "bg_vip_02",
            resId = R.drawable.bg_card_vip_02,
            ratio = "1024:731",
            isFree = true,
            url = "bg_card_vip_02"
        ),
        CardBackground(
            id = "bg_vip_03",
            resId = R.drawable.bg_card_vip_03,
            ratio = "1024:1434",
            isFree = true,
            url = "bg_card_vip_03"
        ),
        CardBackground(
            id = "bg_vip_04",
            resId = R.drawable.bg_card_vip_04,
            ratio = "1024:731",
            isFree = true,
            url = "bg_card_vip_04"
        ),
        CardBackground(
            id = "bg_vip_05",
            resId = R.drawable.bg_card_vip_05,
            ratio = "1024:731",
            isFree = true,
            url = "bg_card_vip_05"
        )
    )

    val currentBgId = BACKGROUND.firstOrNull()?.id ?: ""
}

data class CardStickerItem(
    val id: String,
    val name: String,
    @field:DrawableRes
    val resId: Int = 0,
    val url: String? = null,
    val isVip: Boolean = false
)

object CardStickerManager {
    val STICKERS = listOf(
        CardStickerItem("sticker_gold", "Thỏi Vàng", R.drawable.sticker_gold_ingot, url = "sticker_gold_ingot"),
        CardStickerItem("sticker_li_xi", "Bao Lì Xì", R.drawable.sticker_lucky_bag, url = "sticker_lucky_bag"),
        CardStickerItem("sticker_mai", "Hoa Mai", R.drawable.sticker_apricot_flower, url = "sticker_apricot_flower"),
        CardStickerItem("sticker_lantern", "Đèn Lồng", R.drawable.sticker_lantern, url = "sticker_lantern"),
        CardStickerItem("sticker_firecracker", "Pháo Hoa", R.drawable.sticker_firecracker, url = "sticker_firecracker"),
        CardStickerItem("sticker_fortune_cat", "Mèo Thần Tài", R.drawable.sticker_fortune_cat, url = "sticker_fortune_cat", isVip = true),
        CardStickerItem("sticker_clover", "Cỏ May Mắn", R.drawable.sticker_clover, url = "sticker_clover"),
        CardStickerItem("sticker_popper", "Pháo Tiệc", R.drawable.sticker_party_popper, url = "sticker_party_popper", isVip = true)
    )
}

data class CardColorItem(
    val name: String,
    val colorHex: String
)

object CardColorManager {
    val COLORS = listOf(
        CardColorItem("Đỏ Tết", "#7A090D"),
        CardColorItem("Vàng Kim", "#F5A623"),
        CardColorItem("Vàng Đồng", "#C59B27"),
        CardColorItem("Nâu Gỗ", "#5D4037"),
        CardColorItem("Xanh Ngọc", "#00796B"),
        CardColorItem("Đen Mun", "#212121"),
        CardColorItem("Trắng Bạc", "#FFFFFF"),
        CardColorItem("Cam Tết", "#E65100"),
        CardColorItem("Đỏ Tươi", "#D32F2F")
    )
}

object CardMakerDefaults {
    const val TYPE_BACKGROUND = 1
    const val TYPE_STICKER = 2

    val DEFAULT_COLORS: List<CardColorEntity> = CardColorManager.COLORS.mapIndexed { index, color ->
        CardColorEntity(
            id = color.colorHex,
            name = color.name,
            colorHex = color.colorHex,
            orderIndex = index
        )
    }

    val DEFAULT_BACKGROUNDS: List<CardMakerEntity> = CardBackgroundManager.BACKGROUND.mapIndexed { index, bg ->
        CardMakerEntity(
            id = bg.id,
            url = bg.url ?: bg.id,
            description = "Hình nền Tết ${bg.id}",
            requiredAdViews = if (bg.isAds) 1 else 0,
            createAt = 1000L * (index + 1),
            cmType = TYPE_BACKGROUND,
            accessType = when {
                bg.isAds -> "ads"
                bg.isFree -> "free"
                else -> "vip"
            },
            ratio = bg.ratio
        )
    }

    val DEFAULT_STICKERS: List<CardMakerEntity> = CardStickerManager.STICKERS.mapIndexed { index, sticker ->
        CardMakerEntity(
            id = sticker.id,
            url = sticker.url ?: sticker.id,
            description = sticker.name,
            requiredAdViews = if (sticker.isVip) 1 else 0,
            createAt = 2000L * (index + 1),
            cmType = TYPE_STICKER,
            accessType = if (sticker.isVip) "vip" else "free",
            ratio = null
        )
    }
}

fun CardMakerEntity.toCardBackground(context: Context): CardBackground {
    val resId = if (!url.startsWith("http://") && !url.startsWith("https://")) {
        context.resources.getIdentifier(url, "drawable", context.packageName)
    } else 0

    return CardBackground(
        id = id,
        resId = resId,
        ratio = ratio ?: "1024:1434",
        isFree = accessType.equals("free", ignoreCase = true),
        isAds = accessType.equals("ads", ignoreCase = true),
        url = url
    )
}

fun CardMakerEntity.toCardStickerItem(context: Context): CardStickerItem {
    val resId = if (!url.startsWith("http://") && !url.startsWith("https://")) {
        context.resources.getIdentifier(url, "drawable", context.packageName)
    } else 0

    return CardStickerItem(
        id = id,
        name = description ?: id,
        resId = resId,
        url = url,
        isVip = accessType.equals("vip", ignoreCase = true) || requiredAdViews > 0
    )
}
