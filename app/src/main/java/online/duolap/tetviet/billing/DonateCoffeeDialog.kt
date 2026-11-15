package online.duolap.tetviet.billing

import android.app.Activity
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import com.google.android.material.bottomsheet.BottomSheetDialog
import online.duolap.tetviet.R
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.databinding.DialogDonateCoffeeBinding
import online.duolap.tetviet.databinding.DialogDonateThanksBinding
import online.duolap.tetviet.databinding.ItemDonateOptionBinding
import online.duolap.tetviet.views.setSafeOnClickListener

/**
 * "Mời cà phê": 3 mức ủng hộ tự nguyện qua Google Play (gói consumable [BillingManager.DONATION_PRODUCTS]).
 * Giá lấy từ Play Console (đã định dạng theo tiền tệ của người dùng); không cấp VIP hay tính năng nào.
 * Lời cảm ơn hiện khi [BillingManager.donationCompleted] phát ra (màn hình gọi [showThanks]).
 */
object DonateCoffeeDialog {

    fun show(activity: Activity, billing: BillingManager) {
        AppAnalytics.logFeatureClick("donate_open")
        billing.queryAvailableProducts()

        val dialog = BottomSheetDialog(activity, R.style.Theme_TetViet_BottomSheetDialog)
        val b = DialogDonateCoffeeBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(b.root)

        fun bind(
            option: ItemDonateOptionBinding,
            productId: String,
            @DrawableRes icon: Int,
            @StringRes title: Int,
            @StringRes desc: Int
        ) {
            option.ivDonateIcon.setImageResource(icon)
            option.tvDonateTitle.setText(title)
            option.tvDonateDesc.setText(desc)
            option.tvDonatePrice.text = billing.productsMap.value[productId]
                ?.oneTimePurchaseOfferDetails?.formattedPrice
                ?: activity.getString(R.string.donate_price_loading)
            option.donateOptionRoot.setSafeOnClickListener {
                if (billing.productsMap.value[productId] == null) {
                    billing.queryAvailableProducts()
                    Toast.makeText(activity, R.string.donate_unavailable, Toast.LENGTH_SHORT).show()
                    return@setSafeOnClickListener
                }
                AppAnalytics.logFeatureClick("donate_click_$productId")
                dialog.dismiss()
                billing.launchBillingFlow(activity, productId) { msg ->
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        bind(b.optionCoffee, BillingManager.PRODUCT_DONATE_COFFEE, R.drawable.ic_donate_coffee,
            R.string.donate_coffee_title, R.string.donate_coffee_desc)
        bind(b.optionMilkCoffee, BillingManager.PRODUCT_DONATE_MILK_COFFEE, R.drawable.ic_donate_milk_coffee,
            R.string.donate_milk_coffee_title, R.string.donate_milk_coffee_desc)
        bind(b.optionCoffeeCake, BillingManager.PRODUCT_DONATE_COFFEE_CAKE, R.drawable.ic_donate_coffee_cake,
            R.string.donate_coffee_cake_title, R.string.donate_coffee_cake_desc)

        // Giá có thể về sau khi dialog đã mở (vừa truy vấn lại Google Play)
        val priceViews = mapOf(
            BillingManager.PRODUCT_DONATE_COFFEE to b.optionCoffee.tvDonatePrice,
            BillingManager.PRODUCT_DONATE_MILK_COFFEE to b.optionMilkCoffee.tvDonatePrice,
            BillingManager.PRODUCT_DONATE_COFFEE_CAKE to b.optionCoffeeCake.tvDonatePrice
        )
        val refreshPrices = Runnable {
            priceViews.forEach { (id, tv) ->
                billing.productsMap.value[id]?.oneTimePurchaseOfferDetails?.formattedPrice?.let { tv.text = it }
            }
        }
        b.root.postDelayed(refreshPrices, 800)
        b.root.postDelayed(refreshPrices, 2000)
        dialog.setOnDismissListener {
            b.root.removeCallbacks(refreshPrices)
        }
        dialog.show()
    }

    fun showThanks(activity: Activity, productId: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        AppAnalytics.logFeatureClick("donate_success_$productId")
        val b = DialogDonateThanksBinding.inflate(LayoutInflater.from(activity))
        b.ivThanksIcon.setImageResource(
            when (productId) {
                BillingManager.PRODUCT_DONATE_COFFEE -> R.drawable.ic_donate_coffee
                BillingManager.PRODUCT_DONATE_COFFEE_CAKE -> R.drawable.ic_donate_coffee_cake
                else -> R.drawable.ic_donate_milk_coffee
            }
        )
        val dialog = AlertDialog.Builder(activity).setView(b.root).create()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        b.btnThanksClose.setSafeOnClickListener { dialog.dismiss() }
        dialog.show()
        b.ivThanksIcon.scaleX = 0.4f
        b.ivThanksIcon.scaleY = 0.4f
        b.ivThanksIcon.visibility = View.VISIBLE
        b.ivThanksIcon.animate().scaleX(1f).scaleY(1f).setDuration(450)
            .setInterpolator(OvershootInterpolator(2.5f)).start()
    }
}
