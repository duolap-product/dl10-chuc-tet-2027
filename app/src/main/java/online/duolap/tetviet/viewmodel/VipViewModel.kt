package online.duolap.tetviet.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import online.duolap.tetviet.billing.BillingManager
import online.duolap.tetviet.data.local.VipPreferences
import javax.inject.Inject

@HiltViewModel
class VipViewModel @Inject constructor(
    private val billingManager: BillingManager,
    private val vipPreferences: VipPreferences
) : ViewModel() {

    val isVip: StateFlow<Boolean> = vipPreferences.isVip

    val productsMap: StateFlow<Map<String, ProductDetails>> = billingManager.productsMap

    private val _selectedProductId = MutableStateFlow(BillingManager.PRODUCT_VIP_YEARLY)
    val selectedProductId: StateFlow<String> = _selectedProductId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    fun selectProduct(productId: String) {
        _selectedProductId.value = productId
    }

    fun buySelectedProduct(activity: Activity) {
        if (isVip.value) {
            emitMessage("Bạn đã sở hữu tài khoản VIP đặc quyền!")
            return
        }

        _isLoading.value = true
        billingManager.launchBillingFlow(
            activity = activity,
            productId = _selectedProductId.value,
            onError = { errorMessage ->
                _isLoading.value = false
                emitMessage(errorMessage)
            }
        )
        // Lưu ý: sau khi dialog billing của Google hiển thị, có thể tắt loading
        _isLoading.value = false
    }

    fun restorePurchases() {
        _isLoading.value = true
        billingManager.restorePurchases { _, message ->
            _isLoading.value = false
            emitMessage(message)
        }
    }

    private fun emitMessage(msg: String) {
        viewModelScope.launch {
            _messageEvent.emit(msg)
        }
    }
}

