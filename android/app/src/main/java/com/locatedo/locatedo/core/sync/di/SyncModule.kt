package com.locatedo.locatedo.core.sync.di

import com.locatedo.locatedo.core.sync.DefaultSyncEngine
import com.locatedo.locatedo.core.sync.SyncEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun syncEngine(engine: DefaultSyncEngine): SyncEngine
}
