package online.duolap.tetviet.data.model

import android.content.Context
import android.content.SharedPreferences
import online.duolap.tetviet.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WheelSticker(
    val id: String,
    val name: String,
    val resId: Int
)

object WheelStickerManager {
    val STICKERS = listOf(
        WheelSticker("none", "Không dùng", 0),
        WheelSticker("sticker_gold", "Thỏi vàng", R.drawable.sticker_gold_ingot),
        WheelSticker("sticker_li_xi", "Bao lì xì", R.drawable.sticker_lucky_bag),
        WheelSticker("sticker_mai", "Hoa mai", R.drawable.sticker_apricot_flower),
        WheelSticker("sticker_lantern", "Đèn lồng", R.drawable.sticker_lantern),
        WheelSticker("sticker_firecracker", "Pháo hoa", R.drawable.sticker_firecracker),
        WheelSticker("sticker_fortune_cat", "Mèo thần tài", R.drawable.sticker_fortune_cat),
        WheelSticker("sticker_clover", "Cỏ may mắn", R.drawable.sticker_clover),
        WheelSticker("sticker_popper", "Pháo tiệc", R.drawable.sticker_party_popper)
    )

    fun getDrawableResId(id: String?): Int {
        if (id.isNullOrEmpty() || id == "none") return 0
        return STICKERS.firstOrNull { it.id == id }?.resId ?: 0
    }
}

data class WheelItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val colorHex: String,
    val frequency: Int = 50, // 1 -> 100: càng lớn càng dễ xuất hiện, càng nhỏ càng khó xuất hiện
    val bgImageName: String? = null // id in WheelStickerManager
)

data class SpinHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val prizeText: String
)

object LuckyWheelPrefHelper {

    private const val PREF_NAME = "tet_lucky_wheel_pref"
    private const val KEY_WHEEL_ITEMS = "key_wheel_items"
    private const val KEY_HISTORY = "key_spin_history"

    val DEFAULT_ITEMS = listOf(
        WheelItem(text = "10.000đ", colorHex = "#C62828", frequency = 80, bgImageName = "sticker_li_xi"),
        WheelItem(text = "20.000đ", colorHex = "#FFA000", frequency = 65, bgImageName = "sticker_gold"),
        WheelItem(text = "50.000đ", colorHex = "#D32F2F", frequency = 40, bgImageName = "sticker_lantern"),
        WheelItem(text = "100.000đ", colorHex = "#FFB300", frequency = 25, bgImageName = "sticker_fortune_cat"),
        WheelItem(text = "200.000đ", colorHex = "#B71C1C", frequency = 15, bgImageName = "sticker_firecracker"),
        WheelItem(text = "500.000đ", colorHex = "#FF8F00", frequency = 5, bgImageName = "sticker_popper"),
        WheelItem(text = "Chúc May Mắn", colorHex = "#D81B60", frequency = 70, bgImageName = "sticker_mai"),
        WheelItem(text = "Thêm Lượt", colorHex = "#E64A19", frequency = 50, bgImageName = "sticker_clover")
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getWheelItems(context: Context): MutableList<WheelItem> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_WHEEL_ITEMS, null)
        if (jsonStr.isNullOrEmpty()) {
            return DEFAULT_ITEMS.toMutableList()
        }

        return try {
            val list = mutableListOf<WheelItem>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    WheelItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        text = obj.getString("text"),
                        colorHex = obj.getString("colorHex"),
                        frequency = obj.optInt("frequency", 50).coerceIn(1, 100),
                        bgImageName = obj.optString("bgImageName", "").ifEmpty { null }
                    )
                )
            }
            if (list.size < 2) DEFAULT_ITEMS.toMutableList() else list
        } catch (e: Exception) {
            DEFAULT_ITEMS.toMutableList()
        }
    }

    fun saveWheelItems(context: Context, items: List<WheelItem>) {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                put("colorHex", item.colorHex)
                put("frequency", item.frequency)
                put("bgImageName", item.bgImageName ?: "")
            }
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_WHEEL_ITEMS, array.toString()).apply()
    }

    fun resetWheelItemsToDefault(context: Context): MutableList<WheelItem> {
        saveWheelItems(context, DEFAULT_ITEMS)
        return DEFAULT_ITEMS.toMutableList()
    }

    fun getSpinHistory(context: Context): MutableList<SpinHistoryItem> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return mutableListOf()

        return try {
            val list = mutableListOf<SpinHistoryItem>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SpinHistoryItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        prizeText = obj.getString("prizeText")
                    )
                )
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun addSpinHistory(context: Context, prizeText: String) {
        val list = getSpinHistory(context)
        list.add(0, SpinHistoryItem(prizeText = prizeText)) // Add to top
        // Keep up to 100 entries
        if (list.size > 100) {
            list.removeAt(list.size - 1)
        }

        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("prizeText", item.prizeText)
            }
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun clearSpinHistory(context: Context) {
        getPrefs(context).edit().remove(KEY_HISTORY).apply()
    }
}
