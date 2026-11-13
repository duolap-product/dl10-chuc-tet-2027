package online.duolap.tetviet.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Quản lý lưu trữ trạng thái tài khoản VIP và các quyền lợi mở khóa tính năng
 * qua quảng cáo có thưởng (Rewarded Ads) hoặc mua qua Google Play Billing.
 */
@Singleton
class VipPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isVip = MutableStateFlow(prefs.getBoolean(KEY_IS_VIP, false))
    val isVip: StateFlow<Boolean> = _isVip.asStateFlow()

    private val _unlockedFeatures = MutableStateFlow(loadUnlockedFeatures())
    val unlockedFeatures: StateFlow<Set<String>> = _unlockedFeatures.asStateFlow()

    /**
     * Cập nhật trạng thái VIP vĩnh viễn hoặc theo kỳ hạn từ Google Play Billing.
     */
    fun setVip(vip: Boolean) {
        prefs.edit { putBoolean(KEY_IS_VIP, vip) }
        _isVip.value = vip
    }

    /**
     * Kiểm tra xem một đối tượng (sticker, background, tính năng) có đang được mở khóa hay không.
     * Nếu người dùng là VIP, mọi tính năng đều mặc định được mở khóa.
     */
    fun isFeatureUnlocked(featureId: String): Boolean {
        if (_isVip.value) return true
        val expiryTime = prefs.getLong(KEY_FEATURE_EXPIRY_PREFIX + featureId, 0L)
        return System.currentTimeMillis() < expiryTime
    }

    /**
     * Mở khóa tạm thời một đối tượng (sau khi xem xong video quảng cáo có thưởng).
     * Mặc định mở khóa trong 24 giờ.
     */
    fun unlockFeatureTemporarily(featureId: String, durationMillis: Long = DEFAULT_UNLOCK_DURATION_MS) {
        val expiryTime = System.currentTimeMillis() + durationMillis
        prefs.edit {
            putLong(KEY_FEATURE_EXPIRY_PREFIX + featureId, expiryTime)
        }
        _unlockedFeatures.value = loadUnlockedFeatures()
    }

    private fun loadUnlockedFeatures(): Set<String> {
        val allEntries = prefs.all
        val now = System.currentTimeMillis()
        val unlocked = mutableSetOf<String>()
        for ((key, value) in allEntries) {
            if (key.startsWith(KEY_FEATURE_EXPIRY_PREFIX) && value is Long) {
                if (value > now) {
                    unlocked.add(key.removePrefix(KEY_FEATURE_EXPIRY_PREFIX))
                }
            }
        }
        return unlocked
    }

    companion object {
        private const val PREFS_NAME = "tetviet_vip_preferences"
        private const val KEY_IS_VIP = "key_is_vip_account"
        private const val KEY_FEATURE_EXPIRY_PREFIX = "key_unlocked_feat_"
        private const val DEFAULT_UNLOCK_DURATION_MS = 24 * 60 * 60 * 1000L // 24 giờ
    }
}

