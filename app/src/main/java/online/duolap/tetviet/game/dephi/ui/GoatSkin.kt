package online.duolap.tetviet.game.dephi.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import online.duolap.tetviet.R

/**
 * Trang phục của chú dê, mua bằng xu trong Cửa hàng Dê. Chỉ thay hình, không ảnh hưởng luật chơi.
 * [id] được lưu trong [online.duolap.tetviet.game.dephi.engine.DePhiPreferences]: không đổi id đã phát hành.
 */
enum class GoatSkin(
    val id: String,
    @param:StringRes val nameRes: Int,
    val price: Int,
    @param:DrawableRes val run1: Int,
    @param:DrawableRes val run2: Int,
    @param:DrawableRes val jump: Int
) {
    TRANG("trang", R.string.de_phi_skin_trang, 0,
        R.drawable.ic_dp_goat_run1, R.drawable.ic_dp_goat_run2, R.drawable.ic_dp_goat_jump),
    HOA_DAO("hoa_dao", R.string.de_phi_skin_hoa_dao, 300,
        R.drawable.ic_dp_goat_hoa_dao_run1, R.drawable.ic_dp_goat_hoa_dao_run2, R.drawable.ic_dp_goat_hoa_dao_jump),
    AO_DAI("ao_dai", R.string.de_phi_skin_ao_dai, 500,
        R.drawable.ic_dp_goat_ao_dai_run1, R.drawable.ic_dp_goat_ao_dai_run2, R.drawable.ic_dp_goat_ao_dai_jump),
    VANG("vang", R.string.de_phi_skin_vang, 800,
        R.drawable.ic_dp_goat_vang_run1, R.drawable.ic_dp_goat_vang_run2, R.drawable.ic_dp_goat_vang_jump),
    THAN_TAI("than_tai", R.string.de_phi_skin_than_tai, 1200,
        R.drawable.ic_dp_goat_than_tai_run1, R.drawable.ic_dp_goat_than_tai_run2, R.drawable.ic_dp_goat_than_tai_jump),
    DEN_NGAU("den_ngau", R.string.de_phi_skin_den_ngau, 1500,
        R.drawable.ic_dp_goat_den_ngau_run1, R.drawable.ic_dp_goat_den_ngau_run2, R.drawable.ic_dp_goat_den_ngau_jump);

    companion object {
        fun fromId(id: String?): GoatSkin = entries.firstOrNull { it.id == id } ?: TRANG
    }
}
