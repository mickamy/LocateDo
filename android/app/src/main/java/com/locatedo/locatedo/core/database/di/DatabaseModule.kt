package com.locatedo.locatedo.core.database.di

import android.content.Context
import androidx.room.Room
import com.locatedo.locatedo.core.database.CategoryDao
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.database.PlaceDao
import com.locatedo.locatedo.core.database.TodoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LocateDoDatabase =
        Room.databaseBuilder(context, LocateDoDatabase::class.java, "locatedo.db").build()

    @Provides
    fun placeDao(database: LocateDoDatabase): PlaceDao = database.placeDao()

    @Provides
    fun todoDao(database: LocateDoDatabase): TodoDao = database.todoDao()

    @Provides
    fun categoryDao(database: LocateDoDatabase): CategoryDao = database.categoryDao()
}
