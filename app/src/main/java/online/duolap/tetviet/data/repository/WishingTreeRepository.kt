package online.duolap.tetviet.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import online.duolap.tetviet.data.local.WishDao
import online.duolap.tetviet.data.local.WishEntity
import online.duolap.tetviet.data.model.WishCategory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WishingTreeRepository @Inject constructor(
    private val wishDao: WishDao
) {

    companion object {
        // Base community count for festive atmosphere
        private const val BASE_COMMUNITY_COUNT = 12846
    }

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

    fun getTreeWishes(): Flow<List<WishEntity>> {
        return wishDao.getTreeWishes(limit = 8)
    }

    fun getUserWishes(): Flow<List<WishEntity>> {
        return wishDao.getUserWishes()
    }

    fun getTotalWishCount(): Flow<Int> {
        return wishDao.getUserWishCount().map { userCount ->
            BASE_COMMUNITY_COUNT + userCount
        }
    }

    suspend fun createWish(
        content: String,
        category: WishCategory,
        authorName: String = "Người gửi ẩn danh",
        branchIndex: Int = 0,
        tagColorType: Int = 0
    ): WishEntity = withContext(Dispatchers.IO) {
        val wish = WishEntity(
            content = content.trim(),
            category = category.name,
            authorName = authorName.ifBlank { "Người gửi ẩn danh" },
            createdAt = System.currentTimeMillis(),
            isUserCreated = true,
            branchIndex = branchIndex,
            tagColorType = tagColorType,
            blessingCount = 1
        )
        val id = wishDao.insert(wish)
        wish.copy(id = id)
    }

    suspend fun blessWish(wishId: Long) = withContext(Dispatchers.IO) {
        wishDao.incrementBlessing(wishId)
    }
}

