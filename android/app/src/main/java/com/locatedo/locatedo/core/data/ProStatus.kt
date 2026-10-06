package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.billing.Entitlements
import com.locatedo.locatedo.core.database.SyncStateDao
import javax.inject.Inject

interface ProStatus {
    suspend fun isPro(): Boolean
}

// Pro is the store's word for this user or the household plan the last pull reported; the server still decides
// what gets written, this only decides when the paywall shows.
class EntitlementProStatus @Inject constructor(
    private val entitlements: Entitlements,
    private val syncStateDao: SyncStateDao,
) : ProStatus {
    override suspend fun isPro(): Boolean = Entitlements.isPro(entitlements.hasEntitlement, syncStateDao.get()?.asModel()?.plan)
}
