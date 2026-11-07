package online.duolap.tetviet.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BauCuaPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _coinsFlow = MutableStateFlow(getCoins())
    val coinsFlow: StateFlow<Int> = _coinsFlow.asStateFlow()

    fun getCoins(): Int {
        if (!prefs.contains(KEY_COINS)) {
            prefs.edit { putInt(KEY_COINS, INITIAL_COINS) }
            return INITIAL_COINS
        }
        return prefs.getInt(KEY_COINS, INITIAL_COINS)
    }

    fun addCoins(amount: Int) {
        val current = getCoins()
        val newAmount = (current + amount).coerceAtLeast(0)
        prefs.edit { putInt(KEY_COINS, newAmount) }
        _coinsFlow.value = newAmount
    }

    fun deductCoins(amount: Int): Boolean {
        val current = getCoins()
        if (current < amount) return false
        val newAmount = current - amount
        prefs.edit { putInt(KEY_COINS, newAmount) }
        _coinsFlow.value = newAmount
        return true
    }

    fun canClaimFreeRelief(): Boolean {
        return getCoins() < 10
    }

    fun claimReliefCoins(amount: Int = 100) {
        addCoins(amount)
    }

    fun hasClaimedDailyGiftToday(): Boolean {
        val lastClaim = prefs.getLong(KEY_LAST_DAILY_CLAIM, 0L)
        if (lastClaim <= 0) return false

        val tz = TimeZone.getTimeZone("GMT+7")
        val nowCal = Calendar.getInstance(tz)
        val claimCal = Calendar.getInstance(tz).apply { timeInMillis = lastClaim }

        return nowCal.get(Calendar.YEAR) == claimCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) == claimCal.get(Calendar.DAY_OF_YEAR)
    }

    fun claimDailyGift(amount: Int = 100): Boolean {
        if (hasClaimedDailyGiftToday()) return false
        addCoins(amount)
        prefs.edit { putLong(KEY_LAST_DAILY_CLAIM, System.currentTimeMillis()) }
        return true
    }

    companion object {
        private const val PREFS_NAME = "baucua_game_prefs"
        private const val KEY_COINS = "user_baucua_coins"
        private const val KEY_LAST_DAILY_CLAIM = "baucua_last_daily_claim"
        const val INITIAL_COINS = 200
    }
}

