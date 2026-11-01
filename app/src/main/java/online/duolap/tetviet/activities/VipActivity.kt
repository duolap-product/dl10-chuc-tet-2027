package online.duolap.tetviet.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import online.duolap.tetviet.R
import online.duolap.tetviet.billing.BillingManager
import online.duolap.tetviet.databinding.ActivityVipBinding
import online.duolap.tetviet.viewmodel.VipViewModel

@AndroidEntryPoint
class VipActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVipBinding
    private val viewModel: VipViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVipBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupListeners()
        observeViewModel()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutVipRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.btnClose.setPadding(10, 10, 10, 10)
            binding.layoutVipBottomAction.setPadding(
                binding.layoutVipBottomAction.paddingLeft,
                binding.layoutVipBottomAction.paddingTop,
                binding.layoutVipBottomAction.paddingRight,
                systemBars.bottom + 16
            )
            insets
        }
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener {
            finish()
        }

        binding.btnRestorePurchases.setOnClickListener {
            viewModel.restorePurchases()
        }

        binding.cardPlanYearly.setOnClickListener {
            viewModel.selectProduct(BillingManager.PRODUCT_VIP_YEARLY)
        }
        binding.rbPlanYearly.setOnClickListener {
            viewModel.selectProduct(BillingManager.PRODUCT_VIP_YEARLY)
        }

        binding.cardPlanLifetime.setOnClickListener {
            viewModel.selectProduct(BillingManager.PRODUCT_VIP_LIFETIME)
        }
        binding.rbPlanLifetime.setOnClickListener {
            viewModel.selectProduct(BillingManager.PRODUCT_VIP_LIFETIME)
        }

        binding.cardPlanMonthly.setOnClickListener {
            viewModel.selectProduct(BillingManager.PRODUCT_VIP_MONTHLY)
        }
        binding.rbPlanMonthly.setOnClickListener {
            viewModel.selectProduct(BillingManager.PRODUCT_VIP_MONTHLY)
        }

        binding.btnSubscribeVip.setOnClickListener {
            viewModel.buySelectedProduct(this)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedProductId.collect { selectedId ->
                        updatePlanSelectionUI(selectedId)
                    }
                }

                launch {
                    viewModel.isVip.collect { isVipActive ->
                        updateVipStatusUI(isVipActive)
                    }
                }

                launch {
                    viewModel.productsMap.collect { productsMap ->
                        updateDynamicPrices(productsMap)
                    }
                }

                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.layoutLoadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.messageEvent.collect { message ->
                        Toast.makeText(this@VipActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updatePlanSelectionUI(selectedId: String) {
        val isYearly = selectedId == BillingManager.PRODUCT_VIP_YEARLY
        val isLifetime = selectedId == BillingManager.PRODUCT_VIP_LIFETIME
        val isMonthly = selectedId == BillingManager.PRODUCT_VIP_MONTHLY

        binding.rbPlanYearly.isChecked = isYearly
        binding.cardPlanYearly.setBackgroundResource(
            if (isYearly) R.drawable.bg_vip_card_gold else R.drawable.bg_vip_card_unselected
        )

        binding.rbPlanLifetime.isChecked = isLifetime
        binding.cardPlanLifetime.setBackgroundResource(
            if (isLifetime) R.drawable.bg_vip_card_gold else R.drawable.bg_vip_card_unselected
        )

        binding.rbPlanMonthly.isChecked = isMonthly
        binding.cardPlanMonthly.setBackgroundResource(
            if (isMonthly) R.drawable.bg_vip_card_gold else R.drawable.bg_vip_card_unselected
        )
    }

    private fun updateVipStatusUI(isVip: Boolean) {
        if (isVip) {
            binding.btnSubscribeVip.text = getString(R.string.vip_active_badge)
            binding.btnSubscribeVip.isEnabled = false
            binding.tvVipSubtitle.text = getString(R.string.vip_already_active_desc)
        } else {
            binding.btnSubscribeVip.text = getString(R.string.vip_btn_upgrade_now)
            binding.btnSubscribeVip.isEnabled = true
        }
    }

    private fun updateDynamicPrices(productsMap: Map<String, com.android.billingclient.api.ProductDetails>) {
        // Cập nhật giá động cho gói Năm
        productsMap[BillingManager.PRODUCT_VIP_YEARLY]?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice?.let { price ->
            binding.tvPriceYearly.text = price
        }

        // Cập nhật giá động cho gói Trọn đời
        productsMap[BillingManager.PRODUCT_VIP_LIFETIME]?.oneTimePurchaseOfferDetails?.formattedPrice?.let { price ->
            binding.tvPriceLifetime.text = price
        }

        // Cập nhật giá động cho gói Tháng
        productsMap[BillingManager.PRODUCT_VIP_MONTHLY]?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice?.let { price ->
            binding.tvPriceMonthly.text = price
        }
    }
}

