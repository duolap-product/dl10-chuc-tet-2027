package online.duolap.tetviet.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import online.duolap.tetviet.R

enum class BauCuaItem(
    val id: Int,
    @field:StringRes val nameRes: Int,
    @field:DrawableRes val iconRes: Int,
    val defaultName: String
) {
    NAI(0, R.string.baucua_item_nai, R.drawable.icon_nai_baucua, "Nai"),
    BAU(1, R.string.baucua_item_bau, R.drawable.icon_bau_baucua, "Bầu"),
    GA(2, R.string.baucua_item_ga, R.drawable.icon_ga_baucua, "Gà"),
    CA(3, R.string.baucua_item_ca, R.drawable.icon_ca_baucua, "Cá"),
    CUA(4, R.string.baucua_item_cua, R.drawable.icon_cua_baucua, "Cua"),
    TOM(5, R.string.baucua_item_tom, R.drawable.icon_tom_baucua, "Tôm");

    companion object {
        fun fromId(id: Int): BauCuaItem = entries.firstOrNull { it.id == id } ?: BAU
        fun random(): BauCuaItem = entries.random()
    }
}

