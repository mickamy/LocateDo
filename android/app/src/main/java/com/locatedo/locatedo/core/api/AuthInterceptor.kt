package com.locatedo.locatedo.core.api

import com.connectrpc.Interceptor
import com.connectrpc.StreamFunction
import com.connectrpc.UnaryFunction
import com.connectrpc.http.clone

class AuthInterceptor(private val tokens: AccessTokenStore) : Interceptor {
    override fun unaryFunction(): UnaryFunction = UnaryFunction(
        requestFunction = { request ->
            val token = tokens.current
            if (token == null) {
                request
            } else {
                request.clone(headers = request.headers + ("authorization" to listOf("Bearer $token")))
            }
        },
    )

    override fun streamFunction(): StreamFunction = StreamFunction()
}
