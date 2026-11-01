package online.duolap.tetviet.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardMakerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(items: List<CardMakerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(item: CardMakerEntity)

    @Query("SELECT * FROM card_maker WHERE cm_type = :cmType ORDER BY create_at ASC")
    fun getByCmTypeFlow(cmType: Int): Flow<List<CardMakerEntity>>

    @Query("SELECT * FROM card_maker WHERE cm_type = :cmType ORDER BY create_at ASC")
    fun getByCmTypeList(cmType: Int): List<CardMakerEntity>

    @Query("SELECT * FROM card_maker ORDER BY create_at ASC")
    fun getAllFlow(): Flow<List<CardMakerEntity>>

    @Query("SELECT * FROM card_maker ORDER BY create_at ASC")
    fun getAllList(): List<CardMakerEntity>

    @Query("SELECT * FROM card_maker WHERE id = :id LIMIT 1")
    fun getById(id: String): CardMakerEntity?

    @Query("SELECT COUNT(*) FROM card_maker")
    fun getCount(): Int

    @Query("SELECT COUNT(*) FROM card_maker WHERE cm_type = :cmType")
    fun getCountByCmType(cmType: Int): Int

    @Query("DELETE FROM card_maker")
    fun deleteAll(): Int
}
