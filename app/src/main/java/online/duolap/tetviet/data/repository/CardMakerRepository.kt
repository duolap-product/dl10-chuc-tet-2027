package online.duolap.tetviet.data.repository

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import online.duolap.tetviet.data.local.CardColorDao
import online.duolap.tetviet.data.local.CardMakerDao
import online.duolap.tetviet.data.model.CardBackground
import online.duolap.tetviet.data.model.CardMakerDefaults
import online.duolap.tetviet.data.model.CardStickerItem
import online.duolap.tetviet.data.model.toCardBackground
import online.duolap.tetviet.data.model.toCardStickerItem
import online.duolap.tetviet.data.sync.FirestoreSyncManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardMakerRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cardMakerDao: CardMakerDao,
    private val cardColorDao: CardColorDao,
    private val syncManager: FirestoreSyncManager
) {
    companion object {
        private const val TAG = "CardMakerRepository"
    }

    val backgroundsFlow: Flow<List<CardBackground>> = cardMakerDao
        .getByCmTypeFlow(CardMakerDefaults.TYPE_BACKGROUND)
        .map { entities ->
            if (entities.isEmpty()) {
                CardMakerDefaults.DEFAULT_BACKGROUNDS.map { it.toCardBackground(context) }
            } else {
                entities.map { it.toCardBackground(context) }
            }
        }

    val stickersFlow: Flow<List<CardStickerItem>> = cardMakerDao
        .getByCmTypeFlow(CardMakerDefaults.TYPE_STICKER)
        .map { entities ->
            if (entities.isEmpty()) {
                CardMakerDefaults.DEFAULT_STICKERS.map { it.toCardStickerItem(context) }
            } else {
                entities.map { it.toCardStickerItem(context) }
            }
        }

    val colorsFlow: Flow<List<Pair<String, String>>> = cardColorDao
        .getAllFlow()
        .map { entities ->
            if (entities.isEmpty()) {
                CardMakerDefaults.DEFAULT_COLORS.map { Pair(it.name, it.colorHex) }
            } else {
                entities.map { Pair(it.name, it.colorHex) }
            }
        }

    suspend fun initializeData(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        val count = cardMakerDao.getCount()
        if (count == 0) {
            val initialEntities = CardMakerDefaults.DEFAULT_BACKGROUNDS + CardMakerDefaults.DEFAULT_STICKERS
            cardMakerDao.upsertAll(initialEntities)
            Log.i(TAG, "Lần đầu chạy app: Đã chèn ${initialEntities.size} backgrounds & stickers mặc định vào Room Database")
        }

        val colorCount = cardColorDao.getCount()
        if (colorCount == 0) {
            cardColorDao.upsertAll(CardMakerDefaults.DEFAULT_COLORS)
            Log.i(TAG, "Lần đầu chạy app: Đã chèn ${CardMakerDefaults.DEFAULT_COLORS.size} màu mặc định vào Room Database")
        }

        try {
            syncManager.syncData(
                collectionName = FirestoreSyncManager.COLLECTION_CARD_MAKER,
                forceRefresh = forceRefresh
            )
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi khi đồng bộ Firestore backgrounds/stickers: ${e.message}")
        }

        try {
            syncManager.syncData(
                collectionName = FirestoreSyncManager.COLLECTION_CARD_COLORS,
                forceRefresh = forceRefresh
            )
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi khi đồng bộ Firestore màu sắc: ${e.message}")
        }
    }

    suspend fun getBackgrounds(): List<CardBackground> = withContext(Dispatchers.IO) {
        val entities = cardMakerDao.getByCmTypeList(CardMakerDefaults.TYPE_BACKGROUND)
        if (entities.isEmpty()) {
            CardMakerDefaults.DEFAULT_BACKGROUNDS.map { it.toCardBackground(context) }
        } else {
            entities.map { it.toCardBackground(context) }
        }
    }

    suspend fun getStickers(): List<CardStickerItem> = withContext(Dispatchers.IO) {
        val entities = cardMakerDao.getByCmTypeList(CardMakerDefaults.TYPE_STICKER)
        if (entities.isEmpty()) {
            CardMakerDefaults.DEFAULT_STICKERS.map { it.toCardStickerItem(context) }
        } else {
            entities.map { it.toCardStickerItem(context) }
        }
    }

    suspend fun getColors(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val entities = cardColorDao.getAllList()
        if (entities.isEmpty()) {
            CardMakerDefaults.DEFAULT_COLORS.map { Pair(it.name, it.colorHex) }
        } else {
            entities.map { Pair(it.name, it.colorHex) }
        }
    }
}
