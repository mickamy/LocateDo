package com.locatedo.locatedo.core.appstatus.di

import com.locatedo.locatedo.BuildConfig
import com.locatedo.locatedo.core.appstatus.AppStatusConfig
import com.locatedo.locatedo.core.appstatus.AppStatusFetcher
import com.locatedo.locatedo.core.appstatus.HttpAppStatusFetcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppStatusModule {
    @Binds
    abstract fun fetcher(fetcher: HttpAppStatusFetcher): AppStatusFetcher

    companion object {
        @Provides
        @Singleton
        fun config(): AppStatusConfig = AppStatusConfig(url = BuildConfig.APP_STATUS_URL, currentVersion = BuildConfig.VERSION_NAME)
    }
}
