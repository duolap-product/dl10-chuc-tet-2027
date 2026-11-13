package online.duolap.tetviet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import online.duolap.tetviet.data.local.AppDatabase
import online.duolap.tetviet.data.local.CardColorDao
import online.duolap.tetviet.data.local.CardMakerDao
import online.duolap.tetviet.data.local.WishDao
import online.duolap.tetviet.data.sync.FirestoreSyncManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideCardMakerDao(database: AppDatabase): CardMakerDao {
        return database.cardMakerDao()
    }

    @Provides
    @Singleton
    fun provideCardColorDao(database: AppDatabase): CardColorDao {
        return database.cardColorDao()
    }

    @Provides
    @Singleton
    fun provideWishDao(database: AppDatabase): WishDao {
        return database.wishDao()
    }

    @Provides
    @Singleton
    fun provideFirestoreSyncManager(@ApplicationContext context: Context): FirestoreSyncManager {
        return FirestoreSyncManager(context)
    }
}

