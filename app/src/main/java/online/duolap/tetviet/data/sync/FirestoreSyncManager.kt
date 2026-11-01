package online.duolap.tetviet.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.analytics.AppCrashlytics
import online.duolap.tetviet.data.local.AppDatabase
import online.duolap.tetviet.data.local.CardColorEntity
import online.duolap.tetviet.data.local.CardMakerEntity
import online.duolap.tetviet.data.model.CardMakerDefaults
import online.duolap.tetviet.ext.ifNullOrEmpty

data class SyncSummary(
    val itemsSynced: Int,
    val isIncremental: Boolean,
    val lastSyncTimestamp: Long,
    val isSuccess: Boolean,
    val message: String,
    val isSkipped: Boolean = false
)

class FirestoreSyncManager(context: Context) {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val appDatabase: AppDatabase = AppDatabase.getInstance(context)
    val syncPrefs: AppSyncPreferences = AppSyncPreferences(context)

    companion object {
        private const val TAG = "FirestoreSyncManager"

        const val COLLECTION_APP_ITEMS = "app_items"
        const val COLLECTION_CARD_MAKER = "tao_thiep_hinh_nen"
        const val COLLECTION_CARD_COLORS = "tao_thiep_mau_sac"
    }

    suspend fun syncData(
        collectionName: String = COLLECTION_APP_ITEMS,
        forceRefresh: Boolean = false
    ): Result<SyncSummary> = withContext(Dispatchers.IO) {
        if (collectionName == COLLECTION_CARD_COLORS) {
            return@withContext syncCardColors(forceRefresh)
        }

        if (!forceRefresh && !syncPrefs.shouldSync(collectionName, forceRefresh = false)) {
            val lastSync = syncPrefs.getLastSyncTimestamp(collectionName)
            val formattedDate = syncPrefs.getLastSyncDateFormatted(collectionName)
            val skipMessage =
                "Đã đồng bộ '$collectionName' trong ngày ($formattedDate). Bỏ qua đồng bộ để tiết kiệm mạng & quota Firestore (1 lần/ngày)."
            Log.i(TAG, skipMessage)
            AppCrashlytics.log("Bỏ qua đồng bộ Firestore: $skipMessage")

            return@withContext Result.success(
                SyncSummary(
                    itemsSynced = 0,
                    isIncremental = true,
                    lastSyncTimestamp = lastSync,
                    isSuccess = true,
                    message = skipMessage,
                    isSkipped = true
                )
            )
        }

        val lastDocUpdated = if (forceRefresh) 0L else syncPrefs.lastDocUpdatedAt
        val isIncremental = lastDocUpdated > 0L

        AppCrashlytics.log("Bắt đầu đồng bộ Firestore '$collectionName' (1 lần/ngày, incremental=$isIncremental, lastDocUpdated=$lastDocUpdated)")

        try {
            val collectionRef = firestore.collection(collectionName)

            val snapshot = collectionRef.get(Source.SERVER).await()
            val documents = snapshot.documents

            AppCrashlytics.log("Firestore trả về ${documents.size} documents mới/cập nhật")

            val entities = mutableListOf<CardMakerEntity>()

            for (doc in documents) {
                val id = doc.id

                val url = doc.getString("url").ifNullOrEmpty { "" }
                val description = doc.getString("description").ifNullOrEmpty { "" }

                val requiredAdViews = (doc.get("required_ad_views") as? Long)?.toInt() ?: 0
                val createAt = (doc.get("create_at") as? Timestamp)?.seconds ?: 0L
                val cmType = (doc.get("cm_type") as? Long)?.toInt() ?: 0
                val accessType = doc.getString("access_type").ifNullOrEmpty { "" }
                val ratio = doc.getString("ratio")

                entities.add(
                    CardMakerEntity(
                        id = id,
                        url = url,
                        description = description,
                        requiredAdViews = requiredAdViews,
                        createAt = createAt,
                        cmType = cmType,
                        accessType = accessType,
                        ratio = ratio
                    )
                )
            }

            // add thêm default vào nữa
            entities.addAll(CardMakerDefaults.DEFAULT_BACKGROUNDS)
            entities.addAll(CardMakerDefaults.DEFAULT_STICKERS)

            if (entities.isNotEmpty()) {
                appDatabase.cardMakerDao().deleteAll()
                appDatabase.cardMakerDao().upsertAll(entities)
            }

            val now = System.currentTimeMillis()
            syncPrefs.setLastSyncTimestamp(collectionName, now)
            if (lastDocUpdated > syncPrefs.lastDocUpdatedAt) {
                syncPrefs.lastDocUpdatedAt = lastDocUpdated
            }

            AppAnalytics.logSyncEvent(
                success = true, count = entities.size, isIncremental = isIncremental
            )
            AppCrashlytics.setCustomKey("last_sync_time", now)
            AppCrashlytics.setCustomKey("last_sync_count", entities.size.toLong())

            val summary = SyncSummary(
                itemsSynced = entities.size,
                isIncremental = isIncremental,
                lastSyncTimestamp = now,
                isSuccess = true,
                message = "Đồng bộ thành công ${entities.size} mục (${if (isIncremental) "Tăng dần/Mới" else "Toàn bộ"}).",
                isSkipped = false
            )
            Result.success(summary)

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đồng bộ Firestore", e)
            AppCrashlytics.recordException(e, "FirestoreSyncManager.syncData")
            AppAnalytics.logSyncEvent(
                success = false, count = 0, isIncremental = isIncremental, errorMsg = e.message
            )
            Result.failure(e)
        }
    }

    /**
     * Đồng bộ bảng màu sắc (tetColors) từ collection `tao_thiep_mau_sac` vào Room Database.
     * Cơ chế:
     * - Chỉ đồng bộ 1 lần / 1 ngày.
     * - Parse cấu trúc tài liệu Firestore linh hoạt (hỗ trợ cả danh sách array map trong document lẫn từng document riêng lẻ).
     * - Xóa toàn bộ dữ liệu màu cũ trong bảng `card_color` trước khi upsert.
     */
    suspend fun syncCardColors(forceRefresh: Boolean = false): Result<SyncSummary> = withContext(Dispatchers.IO) {
        val collectionName = COLLECTION_CARD_COLORS

        if (!forceRefresh && !syncPrefs.shouldSync(collectionName, forceRefresh = false)) {
            val lastSync = syncPrefs.getLastSyncTimestamp(collectionName)
            val formattedDate = syncPrefs.getLastSyncDateFormatted(collectionName)
            val skipMessage =
                "Đã đồng bộ màu sắc '$collectionName' trong ngày ($formattedDate). Bỏ qua đồng bộ (1 lần/ngày)."
            Log.i(TAG, skipMessage)
            AppCrashlytics.log("Bỏ qua đồng bộ màu sắc Firestore: $skipMessage")

            return@withContext Result.success(
                SyncSummary(
                    itemsSynced = 0,
                    isIncremental = true,
                    lastSyncTimestamp = lastSync,
                    isSuccess = true,
                    message = skipMessage,
                    isSkipped = true
                )
            )
        }

        AppCrashlytics.log("Bắt đầu đồng bộ màu sắc từ Firestore '$collectionName' (1 lần/ngày)")

        try {
            val collectionRef = firestore.collection(collectionName)
            val snapshot = collectionRef.get(Source.SERVER).await()
            val documents = snapshot.documents

            AppCrashlytics.log("Firestore '$collectionName' trả về ${documents.size} documents")

            val colorEntities = mutableListOf<CardColorEntity>()
            var orderIndex = 0

            for (doc in documents) {
                // Trường hợp 1: Document chính là 1 màu riêng lẻ (có "name" và "color_hex")
                val docName = doc.getString("name")
                val docHex = doc.getString("color_hex") ?: doc.getString("colorHex")
                if (!docName.isNullOrEmpty() && !docHex.isNullOrEmpty()) {
                    colorEntities.add(
                        CardColorEntity(
                            id = docHex,
                            name = docName,
                            colorHex = docHex,
                            orderIndex = orderIndex++
                        )
                    )
                }

                // Trường hợp 2: Document chứa 1 mảng JSON các màu (như /tao_thiep_mau_sac/WpWimPW16dIrb5tH0Q2G/ [ {...}, {...} ])
                val dataMap = doc.data
                if (dataMap != null) {
                    for ((_, value) in dataMap) {
                        if (value is List<*>) {
                            for (item in value) {
                                if (item is Map<*, *>) {
                                    val name = item["name"]?.toString() ?: ""
                                    val hex = (item["color_hex"] ?: item["colorHex"])?.toString() ?: ""
                                    if (name.isNotEmpty() && hex.isNotEmpty()) {
                                        colorEntities.add(
                                            CardColorEntity(
                                                id = hex,
                                                name = name,
                                                colorHex = hex,
                                                orderIndex = orderIndex++
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Nếu Firestore trả về rỗng, fallback sang màu mặc định trong app
            if (colorEntities.isEmpty()) {
                colorEntities.addAll(CardMakerDefaults.DEFAULT_COLORS)
            }

            if (colorEntities.isNotEmpty()) {
                appDatabase.cardColorDao().deleteAll()
                appDatabase.cardColorDao().upsertAll(colorEntities)
            }

            val now = System.currentTimeMillis()
            syncPrefs.setLastSyncTimestamp(collectionName, now)

            AppAnalytics.logSyncEvent(
                success = true, count = colorEntities.size, isIncremental = false
            )

            val summary = SyncSummary(
                itemsSynced = colorEntities.size,
                isIncremental = false,
                lastSyncTimestamp = now,
                isSuccess = true,
                message = "Đồng bộ thành công ${colorEntities.size} màu sắc từ '$collectionName'.",
                isSkipped = false
            )
            Result.success(summary)

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đồng bộ màu sắc từ Firestore", e)
            AppCrashlytics.recordException(e, "FirestoreSyncManager.syncCardColors")
            AppAnalytics.logSyncEvent(
                success = false, count = 0, isIncremental = false, errorMsg = e.message
            )
            Result.failure(e)
        }
    }

    fun resetSyncState() {
        syncPrefs.clear()
    }
}
