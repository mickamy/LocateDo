package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.database.SyncStateDao
import com.locatedo.locatedo.core.model.Plan
import javax.inject.Inject

interface ProStatus {
    suspend fun isPro(): Boolean
}

// The household plan the last pull reported; billing adds the RevenueCat entitlement on top of it.
class SyncStateProStatus @Inject constructor(private val syncStateDao: SyncStateDao) : ProStatus {
    override suspend fun isPro(): Boolean = syncStateDao.get()?.asModel()?.plan == Plan.PRO
}
