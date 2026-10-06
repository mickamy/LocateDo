package com.locatedo.locatedo.core.data.di

import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.FreeProStatus
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.data.ProStatus
import com.locatedo.locatedo.core.data.RoomCategoryRepository
import com.locatedo.locatedo.core.data.RoomPlaceRepository
import com.locatedo.locatedo.core.data.RoomSyncStateRepository
import com.locatedo.locatedo.core.data.SyncStateRepository
import com.locatedo.locatedo.core.data.RoomTodoRepository
import com.locatedo.locatedo.core.data.TodoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun placeRepository(repository: RoomPlaceRepository): PlaceRepository

    @Binds
    abstract fun todoRepository(repository: RoomTodoRepository): TodoRepository

    @Binds
    abstract fun categoryRepository(repository: RoomCategoryRepository): CategoryRepository

    @Binds
    abstract fun syncStateRepository(repository: RoomSyncStateRepository): SyncStateRepository

    @Binds
    abstract fun proStatus(status: FreeProStatus): ProStatus
}
