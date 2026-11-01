package online.duolap.tetviet.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardColorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(items: List<CardColorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(item: CardColorEntity)

    @Query("SELECT * FROM card_color ORDER BY order_index ASC")
    fun getAllFlow(): Flow<List<CardColorEntity>>

    @Query("SELECT * FROM card_color ORDER BY order_index ASC")
    fun getAllList(): List<CardColorEntity>

    @Query("SELECT COUNT(*) FROM card_color")
    fun getCount(): Int

    @Query("DELETE FROM card_color")
    fun deleteAll(): Int
}

