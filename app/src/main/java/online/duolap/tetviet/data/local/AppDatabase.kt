package online.duolap.tetviet.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CardMakerEntity::class, CardColorEntity::class, WishEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cardMakerDao(): CardMakerDao
    abstract fun cardColorDao(): CardColorDao
    abstract fun wishDao(): WishDao

    companion object {
        private const val DATABASE_NAME = "tet_viet_local.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).fallbackToDestructiveMigration().build().also {
                    instance = it
                }
            }
        }
    }
}
