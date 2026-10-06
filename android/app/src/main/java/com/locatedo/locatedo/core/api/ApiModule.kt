package com.locatedo.locatedo.core.api

import com.connectrpc.ProtocolClientConfig
import com.connectrpc.ProtocolClientInterface
import com.connectrpc.ResponseMessage
import com.connectrpc.extensions.GoogleJavaLiteProtobufStrategy
import com.connectrpc.impl.ProtocolClient
import com.connectrpc.okhttp.ConnectOkHttpClient
import com.connectrpc.protocols.NetworkProtocol
import com.locatedo.account.v1.AccountServiceClient
import com.locatedo.account.v1.AccountServiceClientInterface
import com.locatedo.category.v1.CategoryServiceClient
import com.locatedo.category.v1.CategoryServiceClientInterface
import com.locatedo.device.v1.DeviceServiceClient
import com.locatedo.device.v1.DeviceServiceClientInterface
import com.locatedo.household.v1.HouseholdServiceClient
import com.locatedo.household.v1.HouseholdServiceClientInterface
import com.locatedo.locatedo.BuildConfig
import com.locatedo.place.v1.PlaceServiceClient
import com.locatedo.place.v1.PlaceServiceClientInterface
import com.locatedo.sync.v1.SyncServiceClient
import com.locatedo.sync.v1.SyncServiceClientInterface
import com.locatedo.todo.v1.TodoServiceClient
import com.locatedo.todo.v1.TodoServiceClientInterface
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

// The access token the auth interceptor attaches; updated whenever the session changes.
@Singleton
class AccessTokenStore @Inject constructor() {
    @Volatile
    var current: String? = null
}

fun <T> ResponseMessage<T>.getOrThrow(): T = when (this) {
    is ResponseMessage.Success -> message
    is ResponseMessage.Failure -> throw cause
}

// One Connect client over OkHttp; every service client is a thin view of it.
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {
    @Provides
    @Singleton
    fun protocolClient(tokens: AccessTokenStore): ProtocolClientInterface = ProtocolClient(
        ConnectOkHttpClient(),
        ProtocolClientConfig(
            host = BuildConfig.API_BASE_URL,
            serializationStrategy = GoogleJavaLiteProtobufStrategy(),
            networkProtocol = NetworkProtocol.CONNECT,
            interceptors = listOf { AuthInterceptor(tokens) },
            ioCoroutineContext = Dispatchers.IO,
        ),
    )

    @Provides
    @Singleton
    fun accountService(client: ProtocolClientInterface): AccountServiceClientInterface = AccountServiceClient(client)

    @Provides
    @Singleton
    fun householdService(client: ProtocolClientInterface): HouseholdServiceClientInterface = HouseholdServiceClient(client)

    @Provides
    @Singleton
    fun deviceService(client: ProtocolClientInterface): DeviceServiceClientInterface = DeviceServiceClient(client)

    @Provides
    @Singleton
    fun placeService(client: ProtocolClientInterface): PlaceServiceClientInterface = PlaceServiceClient(client)

    @Provides
    @Singleton
    fun todoService(client: ProtocolClientInterface): TodoServiceClientInterface = TodoServiceClient(client)

    @Provides
    @Singleton
    fun categoryService(client: ProtocolClientInterface): CategoryServiceClientInterface = CategoryServiceClient(client)

    @Provides
    @Singleton
    fun syncService(client: ProtocolClientInterface): SyncServiceClientInterface = SyncServiceClient(client)
}
