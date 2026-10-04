import SwiftData
import SwiftUI

@main
struct LocateDoApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    private let container: ModelContainer
    private let router = AppRouter()
    private let preferences = AppPreferences()
    private let locationProvider = LocationProvider()
    private let notifier: ArrivalNotifier
    private let geofence: GeofenceMonitor
    private let api: APIClient
    private let authenticator: Authenticator
    private let sync: SyncEngine
    private let account: AccountManager
    private let writes: LocalWrites
    private let network: NetworkMonitor
    private let devices: DeviceRegistration

    init() {
        container = Self.makeContainer()
        notifier = ArrivalNotifier(router: router)
        geofence = GeofenceMonitor(container: container, notifier: notifier, locationProvider: locationProvider)
        let tokens = AccessTokenStore()
        api = APIClient(environment: .current, tokens: tokens)
        authenticator = Self.makeAuthenticator(api: api, tokens: tokens, preferences: preferences)
        let sync = SyncEngine(
            places: api.place,
            todos: api.todo,
            categories: api.category,
            syncService: api.sync,
            authenticator: authenticator,
            context: container.mainContext
        ) { [geofence] in
            await geofence.sync()
        }
        self.sync = sync
        let devices = DeviceRegistration(devices: api.device, authenticator: authenticator)
        self.devices = devices
        account = AccountManager(
            account: api.account,
            household: api.household,
            authenticator: authenticator,
            context: container.mainContext
        ) { [preferences, geofence] in
            preferences.reset()
            await geofence.sync()
        } onHouseholdReady: { [geofence] in
            await devices.registerIfSignedIn()
            await sync.sync()
            await geofence.sync()
        } onSessionEnded: { [preferences, geofence] in
            preferences.hasPendingSessionEndedNotice = true
            await geofence.sync()
        } onSignedOut: { [geofence] in
            await geofence.sync()
        }
        writes = LocalWrites(context: container.mainContext) { [authenticator] in
            authenticator.isSignedIn
        } onQueued: {
            sync.scheduleDrain()
        }
        network = NetworkMonitor {
            Task {
                await sync.sync()
            }
        }
        connectServices()
    }

    private func connectServices() {
        geofence.onArrival = { [sync] in
            await sync.sync()
        }
        authenticator.onSessionEnded = { [account] in
            Task {
                await account.endSession()
            }
        }
        account.pushToken = { [devices] in
            devices.pushToken
        }
        AppDelegate.onDeviceToken = { [devices] token in
            await devices.received(deviceToken: token)
        }
        AppDelegate.onRemoteNotification = { [sync] in
            await sync.sync()
        }
        if !Self.isRunningTests {
            Analytics.configure()
            geofence.start()
            network.start()
        }
    }

    var body: some Scene {
        WindowGroup {
            if Self.isRunningTests {
                EmptyView()
            } else {
                RootView()
            }
        }
        .modelContainer(container)
        .environment(router)
        .environment(preferences)
        .environment(locationProvider)
        .environment(notifier)
        .environment(geofence)
        .environment(authenticator)
        .environment(sync)
        .environment(account)
        .environment(writes)
    }

    private static func makeAuthenticator(
        api: APIClient,
        tokens: AccessTokenStore,
        preferences: AppPreferences
    ) -> Authenticator {
        let store = KeychainSessionStore()
        ReinstallGuard.clearStaleSession(
            defaults: .standard,
            store: store,
            hasCompletedOnboarding: preferences.hasCompletedOnboarding
        )
        return Authenticator(store: store, account: api.account, tokens: tokens)
    }

    private static func makeContainer() -> ModelContainer {
        do {
            return try AppModelContainer.make(inMemory: isRunningTests)
        } catch {
            fatalError("Could not create ModelContainer: \(error)")
        }
    }

    // The unit test host must not trigger location prompts or start location updates.
    private static var isRunningTests: Bool {
        ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
            || ProcessInfo.processInfo.environment["XCTestBundlePath"] != nil
    }
}
