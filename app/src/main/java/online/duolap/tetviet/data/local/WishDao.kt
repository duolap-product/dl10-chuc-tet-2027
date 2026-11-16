package online.duolap.tetviet.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WishDao {

    @Query("SELECT * FROM wishing_tree_wishes ORDER BY isUserCreated DESC, createdAt DESC")
    fun getAllWishes(): Flow<List<WishEntity>>

    @Query("SELECT * FROM wishing_tree_wishes ORDER BY isUserCreated DESC, createdAt DESC LIMIT :limit")
    fun getTreeWishes(limit: Int): Flow<List<WishEntity>>

    @Query("SELECT * FROM wishing_tree_wishes WHERE isUserCreated = 1 ORDER BY createdAt DESC")
    fun getUserWishes(): Flow<List<WishEntity>>

    @Query("SELECT COUNT(*) FROM wishing_tree_wishes WHERE isUserCreated = 1")
    fun getUserWishCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM wishing_tree_wishes")
    fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(wish: WishEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(wishes: List<WishEntity>)

    /** Điều ước người dùng treo trong ngày [dayKey] (ghim lên cây). */
    @Query("SELECT * FROM wishing_tree_wishes WHERE isUserCreated = 1 AND dayKey = :dayKey ORDER BY createdAt DESC")
    fun getUserWishesOfDay(dayKey: Long): Flow<List<WishEntity>>

    /** Lá của người khác: lá cộng đồng (có remoteId) mới nhất trước, thiếu thì bù bằng điều ước mẫu. */
    @Query("SELECT * FROM wishing_tree_wishes WHERE isUserCreated = 0 AND isHidden = 0 ORDER BY (remoteId IS NULL) ASC, createdAt DESC LIMIT :limit")
    fun getOtherTreeWishes(limit: Int): Flow<List<WishEntity>>

    @Query("SELECT * FROM wishing_tree_wishes WHERE isUserCreated = 1 AND dayKey = :dayKey ORDER BY createdAt ASC")
    fun getUserWishesOfDayOnce(dayKey: Long): List<WishEntity>

    /** Thay toàn bộ lá cộng đồng bằng danh sách mới tải về (một giao dịch, cây không bị nháy rỗng). */
    @Transaction
    fun replaceCommunityWishes(wishes: List<WishEntity>) {
        deleteCommunityWishes()
        insertAll(wishes)
    }

    @Query("SELECT * FROM wishing_tree_wishes WHERE isUserCreated = 1 AND syncState = 1")
    fun getPendingUploads(): List<WishEntity>

    @Query("SELECT * FROM wishing_tree_wishes WHERE isUserCreated = 1 AND remoteId IS NOT NULL ORDER BY createdAt DESC LIMIT :limit")
    fun getSyncedUserWishes(limit: Int): List<WishEntity>

    @Query("SELECT COUNT(*) FROM wishing_tree_wishes WHERE isUserCreated = 1 AND dayKey = :dayKey")
    fun countUserWishesOfDay(dayKey: Long): Int

    @Query("UPDATE wishing_tree_wishes SET remoteId = :remoteId, syncState = :state WHERE id = :id")
    fun updateSync(id: Long, remoteId: String?, state: Int): Int

    @Query("UPDATE wishing_tree_wishes SET blessingCount = :count WHERE id = :id")
    fun updateBlessingCount(id: Long, count: Int): Int

    @Query("UPDATE wishing_tree_wishes SET seenBlessingCount = blessingCount WHERE isUserCreated = 1")
    fun markAllBlessingsSeen(): Int

    @Query("UPDATE wishing_tree_wishes SET isHidden = 1 WHERE remoteId = :remoteId")
    fun hideByRemoteId(remoteId: String): Int

    /** Xoá lá cộng đồng cũ trước khi nạp danh sách mới (không đụng điều ước mẫu và điều ước của người dùng). */
    @Query("DELETE FROM wishing_tree_wishes WHERE isUserCreated = 0 AND remoteId IS NOT NULL AND isHidden = 0")
    fun deleteCommunityWishes(): Int

    @Query("SELECT remoteId FROM wishing_tree_wishes WHERE isUserCreated = 0 AND isHidden = 1 AND remoteId IS NOT NULL")
    fun getHiddenRemoteIds(): List<String>

    @Query("UPDATE wishing_tree_wishes SET blessingCount = blessingCount + 1 WHERE id = :wishId")
    fun incrementBlessing(wishId: Long): Int

    @Query("DELETE FROM wishing_tree_wishes WHERE id = :wishId")
    fun deleteWish(wishId: Long): Int
}
