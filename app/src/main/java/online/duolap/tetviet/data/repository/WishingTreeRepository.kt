package online.duolap.tetviet.data.repository

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import online.duolap.tetviet.BuildConfig
import online.duolap.tetviet.data.local.BauCuaPreferences
import online.duolap.tetviet.data.local.WishDao
import online.duolap.tetviet.data.local.WishEntity
import online.duolap.tetviet.data.model.WishCategory
import online.duolap.tetviet.data.wishing.WishContentFilter
import online.duolap.tetviet.widget.TetCountdown
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cây Điều Ước cộng đồng.
 *
 * - Điều ước được lưu vào Room trước (hiện ngay, kể cả khi mất mạng) rồi tải lên Firestore
 *   (`wishing_tree_wishes`). Người dùng đăng nhập ẩn danh (Firebase Anonymous Auth) để Rules giới hạn
 *   mỗi người 1 điều ước miễn phí + 1 điều ước thưởng (xem video) mỗi ngày giờ VN (`wishing_tree_daily`),
 *   và chặn chúc / báo cáo trùng.
 * - Lá cộng đồng: 40 điều ước mới nhất, bỏ lá bị ẩn và lá của chính mình, lưu tạm trong Room,
 *   tải lại tối đa mỗi [REFRESH_INTERVAL_MS].
 * - Kiểm duyệt mức A: [WishContentFilter] lọc trước khi treo (và lọc lại khi hiển thị lá người khác);
 *   lá bị báo cáo đủ [REPORTS_TO_HIDE] lần thì tự ẩn.
 * Rules mẫu: firebase/wishing_tree.rules (cần dán vào Firestore Rules trên console).
 */
@Singleton
class WishingTreeRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val wishDao: WishDao,
    private val coinPrefs: BauCuaPreferences
) {

    enum class WriteQuota { FREE, EXTRA_AVAILABLE, EXTRA_GRANTED, NONE }

    data class Stats(val total: Long, val today: Long)

    data class CreateResult(
        val wish: WishEntity? = null,
        val rejection: WishContentFilter.Rejection? = null,
        val quotaExceeded: Boolean = false,
        val coinsAwarded: Int = 0
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val auth: FirebaseAuth? by lazy { runCatching { FirebaseAuth.getInstance() }.getOrNull() }
    private val firestore: FirebaseFirestore? by lazy { runCatching { FirebaseFirestore.getInstance() }.getOrNull() }

    /** Mở cây và treo lá đều gọi [syncPending]: khoá để một điều ước không bị tải lên hai lần. */
    private val syncMutex = Mutex()

    /** Việc nền sống theo app (không theo màn hình): tải lên khi đăng nhập xong muộn. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Yêu cầu đăng nhập ẩn danh đang chạy: dùng chung, để không tạo nhiều tài khoản ẩn danh cho một máy. */
    @Volatile
    private var signInTask: Task<AuthResult>? = null

    init {
        // Mạng chậm (vd. IPv6 hỏng) có thể làm đăng nhập xong sau vài phút: khi đó tự tải lên lá đang chờ
        auth?.addAuthStateListener { a ->
            if (a.currentUser != null) appScope.launch { syncPending() }
        }
    }

    private val _stats = MutableStateFlow<Stats?>(null)
    /** Số điều ước thật trên cả cộng đồng; null khi chưa tải được. */
    val stats: StateFlow<Stats?> = _stats.asStateFlow()

    private fun today() = TetCountdown.dayIndex()

    suspend fun ensureInitialWishesSeeded() = withContext(Dispatchers.IO) {
        if (wishDao.countAll() == 0) {
            val initialWishes = listOf(
                WishEntity(
                    content = "Sức khỏe bình an, vạn dặm an khang",
                    category = WishCategory.HEALTH.name,
                    authorName = "Xuân Giáp Thìn",
                    createdAt = System.currentTimeMillis() - 86400000 * 3,
                    isUserCreated = false,
                    branchIndex = 0,
                    tagColorType = 0, // Cream
                    blessingCount = 388
                ),
                WishEntity(
                    content = "Vạn sự như ý, tỷ sự như mơ",
                    category = WishCategory.CAREER.name,
                    authorName = "An Nhiên",
                    createdAt = System.currentTimeMillis() - 86400000 * 2,
                    isUserCreated = false,
                    branchIndex = 1,
                    tagColorType = 1, // Red
                    blessingCount = 520
                ),
                WishEntity(
                    content = "Gia đình hạnh phúc, sum vầy ấm êm",
                    category = WishCategory.FAMILY.name,
                    authorName = "Minh Tâm",
                    createdAt = System.currentTimeMillis() - 86400000,
                    isUserCreated = false,
                    branchIndex = 2,
                    tagColorType = 0, // Cream
                    blessingCount = 412
                ),
                WishEntity(
                    content = "Thành công rực rỡ, đỗ đạt công danh",
                    category = WishCategory.STUDY.name,
                    authorName = "Tuệ Mẫn",
                    createdAt = System.currentTimeMillis() - 43200000,
                    isUserCreated = false,
                    branchIndex = 3,
                    tagColorType = 0, // Cream
                    blessingCount = 299
                ),
                WishEntity(
                    content = "Phát tài phát lộc, tiền tài tấn tới",
                    category = WishCategory.FINANCE.name,
                    authorName = "Tài Lộc",
                    createdAt = System.currentTimeMillis() - 21600000,
                    isUserCreated = false,
                    branchIndex = 4,
                    tagColorType = 2, // Gold
                    blessingCount = 668
                ),
                WishEntity(
                    content = "Tình duyên viên mãn, sớm tìm thấy tri kỷ",
                    category = WishCategory.LOVE.name,
                    authorName = "Hạ Vy",
                    createdAt = System.currentTimeMillis() - 10800000,
                    isUserCreated = false,
                    branchIndex = 5,
                    tagColorType = 1, // Red
                    blessingCount = 188
                ),
                WishEntity(
                    content = "Tâm an vạn sự an, nụ cười luôn nở",
                    category = WishCategory.OTHER.name,
                    authorName = "Bình An",
                    createdAt = System.currentTimeMillis() - 3600000,
                    isUserCreated = false,
                    branchIndex = 6,
                    tagColorType = 0, // Cream
                    blessingCount = 215
                )
            )
            wishDao.insertAll(initialWishes)
        }
    }

    // --- Lá trên cây ----------------------------------------------------------------------------

    /** Lá của mình treo hôm nay (ghim, tối đa 2) + lá cộng đồng mới nhất, đủ [TREE_SIZE] lá. */
    fun getTreeWishes(): Flow<List<WishEntity>> =
        combine(wishDao.getUserWishesOfDay(today()), wishDao.getOtherTreeWishes(TREE_SIZE)) { mine, others ->
            val pinned = mine.take(2)
            pinned + others.take(TREE_SIZE - pinned.size)
        }

    fun getUserWishes(): Flow<List<WishEntity>> = wishDao.getUserWishes()

    fun getUserWishCount(): Flow<Int> = wishDao.getUserWishCount()

    // --- Viết điều ước -------------------------------------------------------------------------

    private fun usedToday(): Int =
        if (prefs.getLong(KEY_USED_DAY, -1) == today()) prefs.getInt(KEY_USED_COUNT, 0) else 0

    fun writeQuota(): WriteQuota {
        val used = usedToday()
        val extraGranted = prefs.getLong(KEY_EXTRA_DAY, -1) == today()
        return when {
            used == 0 -> WriteQuota.FREE
            used == 1 && extraGranted -> WriteQuota.EXTRA_GRANTED
            used == 1 -> WriteQuota.EXTRA_AVAILABLE
            else -> WriteQuota.NONE
        }
    }

    /** Đã xem video "treo thêm 1 lá" hôm nay (giữ quyền tới hết ngày dù chưa viết ngay). */
    fun grantExtraWish() = prefs.edit { putLong(KEY_EXTRA_DAY, today()) }

    suspend fun createWish(
        content: String,
        category: WishCategory,
        branchIndex: Int,
        tagColorType: Int
    ): CreateResult = withContext(Dispatchers.IO) {
        WishContentFilter.check(content)?.let { return@withContext CreateResult(rejection = it) }
        val quota = writeQuota()
        if (quota == WriteQuota.NONE || quota == WriteQuota.EXTRA_AVAILABLE) {
            return@withContext CreateResult(quotaExceeded = true)
        }
        val day = today()
        val slot = usedToday() + 1
        val wish = WishEntity(
            content = content.trim(),
            category = category.name,
            authorName = DEFAULT_AUTHOR,
            createdAt = System.currentTimeMillis(),
            isUserCreated = true,
            branchIndex = branchIndex,
            tagColorType = tagColorType,
            blessingCount = 0,
            syncState = WishEntity.SYNC_PENDING,
            dayKey = day
        )
        val id = wishDao.insert(wish)
        prefs.edit {
            putLong(KEY_USED_DAY, day)
            putInt(KEY_USED_COUNT, slot)
        }
        // Lá đầu tiên trong ngày được thưởng xu (ví chung với các game)
        val coins = if (slot == 1) FIRST_WISH_COINS.also { coinPrefs.addCoins(it) } else 0
        CreateResult(wish = wish.copy(id = id), coinsAwarded = coins)
    }

    // --- Đồng bộ Firestore ---------------------------------------------------------------------

    /**
     * uid ẩn danh. Hết thời gian chờ thì trả null nhưng KHÔNG huỷ yêu cầu đăng nhập: nó chạy tiếp trong nền,
     * xong thì listener ở [init] tự tải lên các điều ước đang chờ.
     */
    private suspend fun ensureUid(): String? {
        val a = auth ?: return null
        a.currentUser?.uid?.let { return it }
        val task = synchronized(this) {
            signInTask?.takeIf { !it.isComplete } ?: a.signInAnonymously().also { signInTask = it }
        }
        return withTimeoutOrNull(SIGN_IN_TIMEOUT_MS) {
            runCatching { task.await().user?.uid }
                .onFailure { Log.w(TAG, "Anonymous sign-in failed", it) }
                .getOrNull()
        }.also { if (it == null) Log.w(TAG, "Anonymous sign-in still pending; will upload when it completes") }
    }

    /** Gọi khi mở cây: bắt đầu đăng nhập ẩn danh sớm để lúc treo lá đã có sẵn uid. */
    fun warmUp() {
        appScope.launch { ensureUid() }
    }

    /** Tải lên các điều ước đang chờ. Gọi sau khi treo, và mỗi lần mở cây. */
    suspend fun syncPending() = withContext(Dispatchers.IO) { syncMutex.withLock { syncPendingLocked() } }

    private suspend fun syncPendingLocked() {
        val pending = wishDao.getPendingUploads()
        if (pending.isEmpty()) return
        val db = firestore ?: return
        val uid = ensureUid() ?: return
        for (wish in pending) {
            if (wish.dayKey != today()) {
                wishDao.updateSync(wish.id, wish.remoteId, WishEntity.SYNC_FAILED)
                continue
            }
            val slot = wishDao.getUserWishesOfDayOnce(wish.dayKey).sortedBy { it.createdAt }
                .indexOfFirst { it.id == wish.id } + 1
            if (slot !in 1..2) {
                wishDao.updateSync(wish.id, wish.remoteId, WishEntity.SYNC_FAILED)
                continue
            }
            val ref = wish.remoteId?.let { db.collection(COL_WISHES).document(it) } ?: db.collection(COL_WISHES).document()
            if (wish.remoteId == null) wishDao.updateSync(wish.id, ref.id, WishEntity.SYNC_PENDING)

            val result = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                runCatching {
                    if (wish.remoteId != null && ref.get().await().exists()) return@runCatching
                    db.batch().apply {
                        set(
                            db.collection(COL_DAILY).document("${uid}_${wish.dayKey}_$slot"),
                            mapOf(
                                "uid" to uid, "dayKey" to wish.dayKey, "slot" to slot,
                                "wishId" to ref.id, "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                        set(
                            ref,
                            mapOf(
                                "content" to wish.content,
                                "category" to wish.category,
                                "tagColorType" to wish.tagColorType,
                                "authorId" to uid,
                                "authorName" to wish.authorName,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "dayKey" to wish.dayKey,
                                "slot" to slot,
                                "blessingCount" to 0,
                                "reportCount" to 0,
                                "hidden" to false,
                                "appVersion" to BuildConfig.VERSION_NAME
                            )
                        )
                        set(
                            db.collection(COL_STATS).document(DOC_STATS),
                            mapOf("total" to FieldValue.increment(1), "day_${wish.dayKey}" to FieldValue.increment(1)),
                            SetOptions.merge()
                        )
                    }.commit().await()
                }
            }
            when {
                result == null -> Unit // hết thời gian chờ (mạng yếu): thử lại lần sau
                result.isSuccess -> wishDao.updateSync(wish.id, ref.id, WishEntity.SYNC_DONE)
                (result.exceptionOrNull() as? FirebaseFirestoreException)?.code ==
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    Log.w(TAG, "Wish upload rejected by rules", result.exceptionOrNull())
                    wishDao.updateSync(wish.id, ref.id, WishEntity.SYNC_FAILED)
                }
                else -> Log.w(TAG, "Wish upload failed, will retry", result.exceptionOrNull())
            }
        }
    }

    /**
     * Tải lá cộng đồng, số liệu và số lời chúc mới cho điều ước của mình (tối đa mỗi [REFRESH_INTERVAL_MS],
     * trừ khi [force]). Trả về số lời chúc mới người dùng chưa thấy.
     */
    suspend fun refreshCommunity(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext 0
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_REFRESH, 0) < REFRESH_INTERVAL_MS) {
            if (_stats.value == null) loadStats(db)
            return@withContext 0
        }
        val uid = ensureUid()
        loadStats(db)

        val snapshot = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            runCatching {
                db.collection(COL_WISHES).orderBy("createdAt", Query.Direction.DESCENDING).limit(FETCH_LIMIT).get().await()
            }.getOrNull()
        } ?: return@withContext 0

        val hiddenLocally = wishDao.getHiddenRemoteIds().toSet()
        val community = snapshot.documents.mapNotNull { doc ->
            val content = doc.getString("content") ?: return@mapNotNull null
            if (doc.getBoolean("hidden") == true) return@mapNotNull null
            if (doc.getString("authorId") == uid) return@mapNotNull null
            if (doc.id in hiddenLocally) return@mapNotNull null
            if (WishContentFilter.check(content) != null) return@mapNotNull null
            WishEntity(
                content = content,
                category = doc.getString("category") ?: WishCategory.OTHER.name,
                authorName = doc.getString("authorName") ?: DEFAULT_AUTHOR,
                createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: now,
                isUserCreated = false,
                branchIndex = (doc.id.hashCode() and 0x7fffffff) % 8,
                tagColorType = (doc.getLong("tagColorType") ?: 0L).toInt().coerceIn(0, 2),
                blessingCount = (doc.getLong("blessingCount") ?: 0L).toInt(),
                remoteId = doc.id,
                syncState = WishEntity.SYNC_DONE,
                dayKey = doc.getLong("dayKey") ?: 0L
            )
        }.take(KEEP_COMMUNITY)
        wishDao.replaceCommunityWishes(community)
        prefs.edit { putLong(KEY_LAST_REFRESH, now) }

        // Lời chúc mới cho điều ước của mình
        val mine = wishDao.getSyncedUserWishes(10)
        if (mine.isEmpty()) return@withContext 0
        val counts = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            runCatching {
                db.collection(COL_WISHES).whereIn(FieldPath.documentId(), mine.mapNotNull { it.remoteId }).get().await()
                    .documents.associate { it.id to (it.getLong("blessingCount") ?: 0L).toInt() }
            }.getOrNull()
        } ?: return@withContext 0
        var fresh = 0
        for (w in mine) {
            val c = counts[w.remoteId] ?: continue
            if (c != w.blessingCount) wishDao.updateBlessingCount(w.id, c)
            fresh += (c - w.seenBlessingCount).coerceAtLeast(0)
        }
        fresh
    }

    suspend fun markBlessingsSeen() = withContext(Dispatchers.IO) { wishDao.markAllBlessingsSeen() }

    private suspend fun loadStats(db: FirebaseFirestore) {
        val doc = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            runCatching { db.collection(COL_STATS).document(DOC_STATS).get().await() }.getOrNull()
        } ?: return
        _stats.value = Stats(
            total = doc.getLong("total") ?: 0L,
            today = doc.getLong("day_${today()}") ?: 0L
        )
    }

    // --- Chúc phúc / báo cáo ---------------------------------------------------------------------

    private fun idSet(key: String): Set<String> = prefs.getStringSet(key, emptySet()).orEmpty()

    private fun addId(key: String, id: String) {
        val list = (idSet(key) + id).toList().takeLast(MAX_REMEMBERED_IDS)
        prefs.edit { putStringSet(key, list.toSet()) }
    }

    fun hasBlessed(wish: WishEntity): Boolean = wish.remoteId != null && wish.remoteId in idSet(KEY_BLESSED)

    /** Chúc phúc lá của người khác: mỗi người 1 lần mỗi lá (Rules chặn trùng bằng wishing_tree_blessings). */
    suspend fun blessWish(wish: WishEntity) = withContext(Dispatchers.IO) {
        if (wish.isUserCreated) return@withContext
        wishDao.incrementBlessing(wish.id)
        val remoteId = wish.remoteId ?: return@withContext
        if (remoteId in idSet(KEY_BLESSED)) return@withContext
        addId(KEY_BLESSED, remoteId)
        val db = firestore ?: return@withContext
        val uid = ensureUid() ?: return@withContext
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            runCatching {
                db.batch().apply {
                    set(
                        db.collection(COL_BLESSINGS).document("${remoteId}_$uid"),
                        mapOf("uid" to uid, "wishId" to remoteId, "createdAt" to FieldValue.serverTimestamp())
                    )
                    update(db.collection(COL_WISHES).document(remoteId), "blessingCount", FieldValue.increment(1))
                }.commit().await()
            }.onFailure { Log.w(TAG, "Bless failed", it) }
        }
    }

    /**
     * Báo cáo lá không phù hợp: ẩn ngay trong máy người báo cáo; trên server tăng reportCount và tự ẩn với
     * mọi người khi đủ [REPORTS_TO_HIDE] lượt.
     */
    suspend fun reportWish(wish: WishEntity) = withContext(Dispatchers.IO) {
        val remoteId = wish.remoteId ?: return@withContext
        wishDao.hideByRemoteId(remoteId)
        if (remoteId in idSet(KEY_REPORTED)) return@withContext
        addId(KEY_REPORTED, remoteId)
        val db = firestore ?: return@withContext
        val uid = ensureUid() ?: return@withContext
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            runCatching {
                val wishRef = db.collection(COL_WISHES).document(remoteId)
                db.runTransaction { tx ->
                    val current = tx.get(wishRef).getLong("reportCount") ?: 0L
                    val next = current + 1
                    tx.set(
                        db.collection(COL_REPORTS).document("${remoteId}_$uid"),
                        mapOf("uid" to uid, "wishId" to remoteId, "createdAt" to FieldValue.serverTimestamp())
                    )
                    tx.update(wishRef, mapOf("reportCount" to next, "hidden" to (next >= REPORTS_TO_HIDE)))
                }.await()
            }.onFailure { Log.w(TAG, "Report failed", it) }
        }
    }

    companion object {
        private const val TAG = "WishingTreeRepo"
        private const val PREFS_NAME = "wishing_tree_prefs"
        private const val KEY_USED_DAY = "used_day"
        private const val KEY_USED_COUNT = "used_count"
        private const val KEY_EXTRA_DAY = "extra_day"
        private const val KEY_LAST_REFRESH = "last_refresh"
        private const val KEY_BLESSED = "blessed_ids"
        private const val KEY_REPORTED = "reported_ids"

        const val COL_WISHES = "wishing_tree_wishes"
        const val COL_DAILY = "wishing_tree_daily"
        const val COL_BLESSINGS = "wishing_tree_blessings"
        const val COL_REPORTS = "wishing_tree_reports"
        const val COL_STATS = "wishing_tree_stats"
        const val DOC_STATS = "global"

        const val TREE_SIZE = 8
        const val FIRST_WISH_COINS = 10
        const val REPORTS_TO_HIDE = 3
        const val DEFAULT_AUTHOR = "Người gửi ẩn danh"
        private const val FETCH_LIMIT = 40L
        private const val KEEP_COMMUNITY = 20
        private const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
        private const val NETWORK_TIMEOUT_MS = 10_000L
        private const val SIGN_IN_TIMEOUT_MS = 20_000L
        private const val MAX_REMEMBERED_IDS = 300
    }
}
