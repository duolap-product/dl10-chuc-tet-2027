package online.duolap.tetviet.game.dephi.ui

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import online.duolap.tetviet.R
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.data.local.BauCuaPreferences
import online.duolap.tetviet.databinding.DialogDePhiShopBinding
import online.duolap.tetviet.databinding.ItemDePhiSkinBinding
import online.duolap.tetviet.game.dephi.engine.DePhiPreferences
import online.duolap.tetviet.views.setSafeOnClickListener
import java.text.NumberFormat
import java.util.Locale

/**
 * Cửa hàng Dê: lưới trang phục [GoatSkin]. Chưa có thì mua bằng xu (ví chung, sau khi xác nhận),
 * đã có thì bấm để chọn. [onSkinChanged] báo màn chơi đổi hình chú dê.
 */
object DePhiShopDialog {

    fun show(
        activity: Activity,
        prefs: DePhiPreferences,
        coinPrefs: BauCuaPreferences,
        onSkinChanged: (GoatSkin) -> Unit
    ) {
        AppAnalytics.logFeatureClick("de_phi_shop_open")
        val dialog = BottomSheetDialog(activity, R.style.Theme_TetViet_BottomSheetDialog)
        val b = DialogDePhiShopBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(b.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        val format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))
        val cards = LinkedHashMap<GoatSkin, ItemDePhiSkinBinding>()

        fun owned(skin: GoatSkin) = skin.price == 0 || skin.id in prefs.ownedSkins
        fun selected() = GoatSkin.fromId(prefs.selectedSkin)

        fun refresh() {
            b.tvShopCoins.text = format.format(coinPrefs.getCoins())
            val current = selected()
            cards.forEach { (skin, c) ->
                val isOwned = owned(skin)
                val inUse = skin == current
                c.skinRoot.setBackgroundResource(if (inUse) R.drawable.bg_dp_skin_card_selected else R.drawable.bg_dp_skin_card)
                c.ivSkinCoin.visibility = if (isOwned) View.GONE else View.VISIBLE
                c.tvSkinAction.text = when {
                    inUse -> activity.getString(R.string.de_phi_skin_in_use)
                    isOwned -> activity.getString(R.string.de_phi_skin_select)
                    else -> format.format(skin.price)
                }
                c.btnSkinAction.setBackgroundResource(
                    if (inUse) R.drawable.bg_checkin_btn_waiting else R.drawable.bg_wishing_cta_button
                )
                c.tvSkinAction.setTextColor(if (inUse) 0xFF7A090D.toInt() else 0xFFFFFDF7.toInt())
                c.skinRoot.contentDescription = activity.getString(skin.nameRes) + ", " + c.tvSkinAction.text
            }
        }

        fun select(skin: GoatSkin, card: ItemDePhiSkinBinding) {
            prefs.selectedSkin = skin.id
            AppAnalytics.logFeatureClick("de_phi_skin_select_${skin.id}")
            onSkinChanged(skin)
            refresh()
            card.ivSkin.scaleX = 0.7f
            card.ivSkin.scaleY = 0.7f
            card.ivSkin.animate().scaleX(1f).scaleY(1f).setDuration(380)
                .setInterpolator(OvershootInterpolator(2.6f)).start()
        }

        fun buy(skin: GoatSkin, card: ItemDePhiSkinBinding) {
            val coins = coinPrefs.getCoins()
            val name = activity.getString(skin.nameRes)
            if (coins < skin.price) {
                Toast.makeText(
                    activity, activity.getString(R.string.de_phi_not_enough, skin.price - coins), Toast.LENGTH_LONG
                ).show()
                AppAnalytics.logFeatureClick("de_phi_skin_not_enough_${skin.id}")
                return
            }
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.de_phi_buy_title, name))
                .setMessage(activity.getString(R.string.de_phi_buy_msg, format.format(skin.price), name, format.format(coins)))
                .setPositiveButton(R.string.de_phi_buy) { _, _ ->
                    if (coinPrefs.deductCoins(skin.price)) {
                        prefs.ownedSkins = prefs.ownedSkins + skin.id
                        AppAnalytics.logFeatureClick("de_phi_skin_buy_${skin.id}")
                        Toast.makeText(activity, activity.getString(R.string.de_phi_bought, name), Toast.LENGTH_SHORT).show()
                        select(skin, card)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // Lưới 2 cột
        val grid = b.layoutSkinGrid
        var row: LinearLayout? = null
        val gap = (10 * activity.resources.displayMetrics.density).toInt()
        GoatSkin.entries.forEachIndexed { i, skin ->
            if (i % 2 == 0) {
                row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    grid.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        if (i > 0) topMargin = gap
                    })
                }
            }
            val card = ItemDePhiSkinBinding.inflate(LayoutInflater.from(activity), row, false)
            (card.root.layoutParams as LinearLayout.LayoutParams).apply {
                if (i % 2 == 0) marginEnd = gap / 2 else marginStart = gap / 2
            }
            row!!.addView(card.root)
            card.ivSkin.setImageResource(skin.run1)
            card.tvSkinName.setText(skin.nameRes)
            card.skinRoot.setSafeOnClickListener {
                when {
                    skin == selected() -> Unit
                    owned(skin) -> select(skin, card)
                    else -> buy(skin, card)
                }
            }
            cards[skin] = card
        }
        refresh()
        dialog.show()
    }
}
