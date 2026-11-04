package online.duolap.tetviet.di

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import online.duolap.tetviet.update.PlayUpdateManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideAppUpdateManager(@ApplicationContext context: Context): AppUpdateManager {
        return AppUpdateManagerFactory.create(context)
    }

    @Provides
    @Singleton
    fun providePlayUpdateManager(
        appUpdateManager: AppUpdateManager
    ): PlayUpdateManager {
        return PlayUpdateManager(appUpdateManager)
    }
}

