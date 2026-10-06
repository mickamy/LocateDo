package com.locatedo.locatedo.core.analytics.di

import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.FirebaseAnalyticsSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds
    abstract fun analytics(sink: FirebaseAnalyticsSink): Analytics
}
