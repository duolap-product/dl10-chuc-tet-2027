package online.duolap.tetviet.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("UPDATE wishing_tree_wishes SET blessingCount = blessingCount + 1 WHERE id = :wishId")
    fun incrementBlessing(wishId: Long): Int

    @Query("DELETE FROM wishing_tree_wishes WHERE id = :wishId")
    fun deleteWish(wishId: Long): Int
}
