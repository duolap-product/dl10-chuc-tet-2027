package online.duolap.tetviet.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import online.duolap.tetviet.data.local.VipPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trình quản lý thanh toán in-app billing qua Google Play Billing Library 7.x.
 * Hỗ trợ các gói VIP: Gói tháng, Gói năm và Gói trọn đời.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vipPreferences: VipPreferences
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

    private val _productsMap = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productsMap: StateFlow<Map<String, ProductDetails>> = _productsMap.asStateFlow()

    private var billingClient: BillingClient? = null

    init {
        initializeBillingClient()
    }

    /**
     * Khởi tạo kết nối với Google Play Billing.
     */
    fun initializeBillingClient() {
        if (billingClient != null && billingClient?.isReady == true) return

        _connectionState.value = BillingConnectionState.CONNECTING

        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(pendingPurchasesParams)
            .build()

        startConnection()
    }

    private fun startConnection() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Google Play Billing setup successful.")
                    _connectionState.value = BillingConnectionState.CONNECTED
                    queryAvailableProducts()
                    restorePurchases()
                } else {
                    Log.w(TAG, "Google Play Billing setup failed: ${billingResult.debugMessage}")
                    _connectionState.value = BillingConnectionState.ERROR
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Google Play Billing service disconnected. Reconnecting...")
                _connectionState.value = BillingConnectionState.DISCONNECTED
            }
        })
    }

    /**
     * Truy vấn thông tin chi tiết các gói sản phẩm VIP từ Google Play.
     */
    fun queryAvailableProducts() {
        val client = billingClient ?: return
        if (!client.isReady) return

        // 1. Gói Subscriptions (Tháng, Năm)
        val subsList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_VIP_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_VIP_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subsList)
            .build()

        client.queryProductDetailsAsync(subsParams) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val currentMap = _productsMap.value.toMutableMap()
                detailsList.productDetailsList.forEach { currentMap[it.productId] = it }
                _productsMap.value = currentMap
                Log.i(TAG, "Loaded ${detailsList.productDetailsList} subscription products.")
            }
        }

        // 2. Gói In-App Purchase (Trọn đời)
        val inAppList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_VIP_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(inAppList)
            .build()

        client.queryProductDetailsAsync(inAppParams) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val currentMap = _productsMap.value.toMutableMap()
                detailsList.productDetailsList.forEach { currentMap[it.productId] = it }
                _productsMap.value = currentMap
                Log.i(TAG, "Loaded ${detailsList.productDetailsList} in-app products.")
            }
        }
    }

    /**
     * Mở luồng thanh toán Google Play cho gói được chọn.
     */
    fun launchBillingFlow(
        activity: Activity,
        productId: String,
        onError: (String) -> Unit = {}
    ) {
        val client = billingClient
        if (client == null || !client.isReady) {
            onError("Chưa kết nối được tới Google Play Store.")
            initializeBillingClient()
            return
        }

        val productDetails = _productsMap.value[productId]
        if (productDetails == null) {
            onError("Không tìm thấy thông tin gói thanh toán.")
            return
        }

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        // Đối với subscriptions, cần lấy offerToken hợp lệ
        if (productDetails.productType == BillingClient.ProductType.SUBS) {
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken != null) {
                productDetailsParamsBuilder.setOfferToken(offerToken)
            }
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        val responseCode = client.launchBillingFlow(activity, billingFlowParams).responseCode
        if (responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "Launch billing flow failed with code: $responseCode")
            onError("Không thể khởi tạo giao dịch (Mã lỗi: $responseCode)")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "User canceled billing flow.")
        } else {
            Log.w(TAG, "Purchases update returned error: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            scope.launch {
                vipPreferences.setVip(true)

                if (!purchase.isAcknowledged) {
                    val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()

                    billingClient?.acknowledgePurchase(acknowledgeParams) { result ->
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.i(TAG, "Purchase acknowledged successfully: ${purchase.orderId}")
                        } else {
                            Log.e(TAG, "Failed to acknowledge purchase: ${result.debugMessage}")
                        }
                    }
                }
            }
        }
    }

    /**
     * Khôi phục giao dịch đã mua (Restore Purchases).
     */
    fun restorePurchases(onComplete: ((isVip: Boolean, message: String) -> Unit)? = null) {
        val client = billingClient
        if (client == null || !client.isReady) {
            onComplete?.invoke(vipPreferences.isVip.value, "Google Play chưa sẵn sàng.")
            return
        }

        var foundVip = false

        // Kiểm tra In-App purchase (Gói trọn đời)
        val inAppParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(inAppParams) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        foundVip = true
                        handlePurchase(purchase)
                    }
                }
            }

            // Kiểm tra Subscriptions (Tháng, Năm)
            val subsParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            client.queryPurchasesAsync(subsParams) { subsResult, subsPurchases ->
                if (subsResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    subsPurchases.forEach { purchase ->
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            foundVip = true
                            handlePurchase(purchase)
                        }
                    }
                }

                if (foundVip) {
                    vipPreferences.setVip(true)
                    onComplete?.invoke(true, "Khôi phục gói VIP thành công!")
                } else {
                    onComplete?.invoke(vipPreferences.isVip.value, "Không tìm thấy giao dịch VIP trước đó.")
                }
            }
        }
    }

    companion object {
        private const val TAG = "BillingManager"

        const val PRODUCT_VIP_MONTHLY = "tetviet_vip_monthly"
        const val PRODUCT_VIP_YEARLY = "tetviet_vip_yearly"
        const val PRODUCT_VIP_LIFETIME = "tetviet_vip_lifetime"
    }
}

enum class BillingConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

