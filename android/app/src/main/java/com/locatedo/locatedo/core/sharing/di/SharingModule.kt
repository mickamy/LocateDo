package com.locatedo.locatedo.core.sharing.di

import com.locatedo.locatedo.core.sharing.DefaultHouseholdManager
import com.locatedo.locatedo.core.sharing.HouseholdManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SharingModule {
    @Binds
    abstract fun householdManager(manager: DefaultHouseholdManager): HouseholdManager
}
