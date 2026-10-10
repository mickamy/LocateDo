package com.locatedo.locatedo.core.analytics

import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.GetBillingConfigParams
import com.android.billingclient.api.PendingPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

interface StoreCountrySource {
    // ISO 3166-1 alpha-2, or null when the store cannot tell.
    suspend fun country(): String?
}

// The Google Play account's country, read straight from Play Billing like iOS reads StoreKit's storefront.
@Singleton
class PlayStoreCountry @Inject constructor(@param:ApplicationContext private val context: Context) : StoreCountrySource {
    override suspend fun country(): String? = withTimeoutOrNull(TIMEOUT) {
        val client = BillingClient.newBuilder(context)
            .setListener { _, _ -> }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        try {
            if (!connect(client)) {
                return@withTimeoutOrNull null
            }
            readCountry(client)
        } finally {
            client.endConnection()
        }
    }

    private suspend fun connect(client: BillingClient): Boolean = suspendCancellableCoroutine { continuation ->
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) {
                        continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            },
        )
    }

    private suspend fun readCountry(client: BillingClient): String? = suspendCancellableCoroutine { continuation ->
        client.getBillingConfigAsync(GetBillingConfigParams.newBuilder().build()) { result, config ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Could not read the store country: ${result.debugMessage}")
            }
            continuation.resume(config?.countryCode?.ifEmpty { null })
        }
    }

    private companion object {
        const val TAG = "StoreCountry"
        val TIMEOUT = 5.seconds
    }
}
