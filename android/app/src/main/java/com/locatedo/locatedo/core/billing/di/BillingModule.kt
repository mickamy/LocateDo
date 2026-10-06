package com.locatedo.locatedo.core.billing.di

import android.content.Context
import com.locatedo.locatedo.BuildConfig
import com.locatedo.locatedo.core.billing.EntitlementSource
import com.locatedo.locatedo.core.billing.RevenueCatEntitlementSource
import com.locatedo.locatedo.core.billing.UnavailableEntitlementSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Staging and release carry no key until the Play app is wired into RevenueCat; those builds simply have no store.
@Module
@InstallIn(SingletonComponent::class)
object BillingModule {
    @Provides
    @Singleton
    fun entitlementSource(@ApplicationContext context: Context): EntitlementSource {
        if (BuildConfig.REVENUECAT_API_KEY.isEmpty()) {
            return UnavailableEntitlementSource
        }
        return RevenueCatEntitlementSource(context, BuildConfig.REVENUECAT_API_KEY)
    }
}
