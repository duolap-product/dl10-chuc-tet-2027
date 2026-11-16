package online.duolap.tetviet.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CardMakerEntity::class, CardColorEntity::class, WishEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cardMakerDao(): CardMakerDao
    abstract fun cardColorDao(): CardColorDao
    abstract fun wishDao(): WishDao

    companion object {
        private const val DATABASE_NAME = "tet_viet_local.db"

        /**
         * 5 → 6: Cây Điều Ước lên cộng đồng (Firestore). Dùng migration thật thay vì xoá dữ liệu, vì điều ước
         * người dùng tự viết chỉ có trong máy, không đồng bộ lại được.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wishing_tree_wishes ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE wishing_tree_wishes ADD COLUMN syncState INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE wishing_tree_wishes ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE wishing_tree_wishes ADD COLUMN dayKey INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE wishing_tree_wishes ADD COLUMN seenBlessingCount INTEGER NOT NULL DEFAULT 0")
                // Lượt chúc cũ (đếm trong máy) coi như đã thấy, để không báo "lời chúc mới" giả
                db.execSQL("UPDATE wishing_tree_wishes SET seenBlessingCount = blessingCount")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build().also {
                    instance = it
                }
            }
        }
    }
}
