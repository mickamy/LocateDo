package com.locatedo.locatedo.core.geofence

import android.content.Context
import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.notifications.ArrivalHandler
import com.locatedo.locatedo.core.sync.SyncEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

// Receivers cannot be constructor-injected, so they reach the graph through this.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GeofenceEntryPoint {
    fun arrivalHandler(): ArrivalHandler
    fun geofenceSync(): GeofenceSync
    fun syncEngine(): SyncEngine

    @ApplicationScope
    fun applicationScope(): CoroutineScope

    companion object {
        fun from(context: Context): GeofenceEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, GeofenceEntryPoint::class.java)
    }
}
