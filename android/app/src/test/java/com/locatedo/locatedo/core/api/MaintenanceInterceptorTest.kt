package com.locatedo.locatedo.core.api

import com.connectrpc.Code
import com.connectrpc.ConnectException
import com.connectrpc.ProtocolClientConfig
import com.connectrpc.ResponseMessage
import com.connectrpc.extensions.GoogleJavaLiteProtobufStrategy
import com.connectrpc.impl.ProtocolClient
import com.connectrpc.okhttp.ConnectOkHttpClient
import com.connectrpc.protocols.NetworkProtocol
import com.locatedo.locatedo.core.appstatus.AppStatusDocument
import com.locatedo.locatedo.core.appstatus.MaintenanceGate
import com.locatedo.locatedo.testing.SettableClock
import com.locatedo.sync.v1.SyncServiceClient
import com.locatedo.sync.v1.pullRequest
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceInterceptorTest {
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val gate = MaintenanceGate(SettableClock(now))

    @Test
    fun failsEveryCallAsUnavailableDuringMaintenance() = runTest {
        gate.update(AppStatusDocument.Maintenance(startsAt = now.minusSeconds(60), endsAt = now.plusSeconds(3600)))

        val failure = call()

        assertEquals(Code.UNAVAILABLE, failure.code)
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("maintenance"))
    }

    @Test
    fun letsCallsThroughOutsideMaintenance() = runTest {
        gate.update(AppStatusDocument.Maintenance(startsAt = now.plusSeconds(60), endsAt = now.plusSeconds(3600)))

        val failure = call()

        // Nothing listens on the port, so the request fails further down, past the interceptor.
        assertTrue(failure.message.orEmpty(), !failure.message.orEmpty().contains("maintenance"))
    }

    // The client points at a closed local port; what matters is whether the interceptor stops the call first.
    private suspend fun call(): ConnectException {
        val client = ProtocolClient(
            ConnectOkHttpClient(),
            ProtocolClientConfig(
                host = "http://127.0.0.1:9",
                serializationStrategy = GoogleJavaLiteProtobufStrategy(),
                networkProtocol = NetworkProtocol.CONNECT,
                interceptors = listOf { MaintenanceInterceptor(gate) },
            ),
        )
        val response = try {
            SyncServiceClient(client).pull(pullRequest {})
        } catch (e: ConnectException) {
            return e
        }
        return when (response) {
            is ResponseMessage.Failure -> response.cause
            is ResponseMessage.Success -> error("the call succeeded against a closed port")
        }
    }
}
