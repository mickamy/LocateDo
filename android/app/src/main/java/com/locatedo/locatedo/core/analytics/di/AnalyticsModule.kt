package com.locatedo.locatedo.core.analytics.di

import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsCollection
import com.locatedo.locatedo.core.analytics.FirebaseAnalyticsCollection
import com.locatedo.locatedo.core.analytics.FirebaseAnalyticsSink
import com.locatedo.locatedo.core.analytics.PlayStoreCountry
import com.locatedo.locatedo.core.analytics.StoreCountrySource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds
    abstract fun analytics(sink: FirebaseAnalyticsSink): Analytics

    @Binds
    abstract fun collection(collection: FirebaseAnalyticsCollection): AnalyticsCollection

    @Binds
    abstract fun storeCountry(source: PlayStoreCountry): StoreCountrySource
}
