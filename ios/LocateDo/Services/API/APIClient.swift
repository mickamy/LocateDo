import Connect
import Foundation

final class APIClient {
    let environment: APIEnvironment
    let account: any Locatedo_Account_V1_AccountServiceClientInterface
    let household: any Locatedo_Household_V1_HouseholdServiceClientInterface
    let category: any Locatedo_Category_V1_CategoryServiceClientInterface
    let place: any Locatedo_Place_V1_PlaceServiceClientInterface
    let todo: any Locatedo_Todo_V1_TodoServiceClientInterface
    let device: any Locatedo_Device_V1_DeviceServiceClientInterface
    let sync: any Locatedo_Sync_V1_SyncServiceClientInterface

    init(environment: APIEnvironment, tokens: AccessTokenStore, gate: MaintenanceGate) {
        self.environment = environment
        let client = ProtocolClient(
            httpClient: URLSessionHTTPClient(),
            config: ProtocolClientConfig(
                host: environment.baseURL.absoluteString,
                networkProtocol: .connect,
                codec: ProtoCodec(),
                interceptors: [
                    InterceptorFactory { _ in MaintenanceInterceptor(gate: gate) },
                    InterceptorFactory { _ in AuthInterceptor(tokens: tokens) }
                ]
            )
        )
        account = Locatedo_Account_V1_AccountServiceClient(client: client)
        household = Locatedo_Household_V1_HouseholdServiceClient(client: client)
        category = Locatedo_Category_V1_CategoryServiceClient(client: client)
        place = Locatedo_Place_V1_PlaceServiceClient(client: client)
        todo = Locatedo_Todo_V1_TodoServiceClient(client: client)
        device = Locatedo_Device_V1_DeviceServiceClient(client: client)
        sync = Locatedo_Sync_V1_SyncServiceClient(client: client)
    }
}
