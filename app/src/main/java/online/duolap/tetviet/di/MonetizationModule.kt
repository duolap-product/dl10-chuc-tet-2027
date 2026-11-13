package online.duolap.tetviet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import online.duolap.tetviet.ads.AdConsentManager
import online.duolap.tetviet.ads.AdManager
import online.duolap.tetviet.ads.AdPolicy
import online.duolap.tetviet.billing.BillingManager
import online.duolap.tetviet.data.local.VipPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MonetizationModule {

    @Provides
    @Singleton
    fun provideVipPreferences(@ApplicationContext context: Context): VipPreferences {
        return VipPreferences(context)
    }

    @Provides
    @Singleton
    fun provideBillingManager(
        @ApplicationContext context: Context,
        vipPreferences: VipPreferences
    ): BillingManager {
        return BillingManager(context, vipPreferences)
    }

    @Provides
    @Singleton
    fun provideAdPolicy(@ApplicationContext context: Context): AdPolicy {
        val prefs = context.getSharedPreferences(AdPolicy.PREFS_NAME, Context.MODE_PRIVATE)
        return AdPolicy(AdPolicy.SharedPrefsStore(prefs))
    }

    @Provides
    @Singleton
    fun provideAdManager(
        @ApplicationContext context: Context,
        vipPreferences: VipPreferences,
        adPolicy: AdPolicy,
        consentManager: AdConsentManager
    ): AdManager {
        return AdManager(context, vipPreferences, adPolicy, consentManager)
    }
}

