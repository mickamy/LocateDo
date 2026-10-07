package com.locatedo.locatedo.core.api

import com.connectrpc.Code
import com.connectrpc.ConnectException
import com.connectrpc.Interceptor
import com.connectrpc.StreamFunction
import com.connectrpc.UnaryFunction
import com.locatedo.locatedo.core.appstatus.MaintenanceGate

// While the gate is closed no call reaches the server: it fails as UNAVAILABLE, which every caller treats as temporary.
class MaintenanceInterceptor(private val gate: MaintenanceGate) : Interceptor {
    override fun unaryFunction(): UnaryFunction = UnaryFunction(
        requestFunction = { request ->
            if (gate.isClosed()) {
                throw ConnectException(code = Code.UNAVAILABLE, message = "the server is under maintenance")
            }
            request
        },
    )

    override fun streamFunction(): StreamFunction = StreamFunction()
}
