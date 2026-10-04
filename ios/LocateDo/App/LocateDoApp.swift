import SwiftData
import SwiftUI

@main
struct LocateDoApp: App {
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

    init() {
        do {
            container = try AppModelContainer.make(inMemory: Self.isRunningTests)
        } catch {
            fatalError("Could not create ModelContainer: \(error)")
        }
        notifier = ArrivalNotifier(router: router)
        geofence = GeofenceMonitor(container: container, notifier: notifier, locationProvider: locationProvider)
        let tokens = AccessTokenStore()
        api = APIClient(environment: .current, tokens: tokens)
        authenticator = Authenticator(store: KeychainSessionStore(), account: api.account, tokens: tokens)
        let sync = SyncEngine(
            places: api.place,
            todos: api.todo,
            categories: api.category,
            authenticator: authenticator,
            context: container.mainContext
        )
        self.sync = sync
        account = AccountManager(
            account: api.account,
            household: api.household,
            authenticator: authenticator,
            context: container.mainContext
        ) { [preferences, geofence] in
            preferences.reset()
            await geofence.sync()
        } onHouseholdReady: {
            await sync.drain()
        }
        writes = LocalWrites(context: container.mainContext) { [authenticator] in
            authenticator.isSignedIn
        } onQueued: {
            sync.scheduleDrain()
        }
        if !Self.isRunningTests {
            Analytics.configure()
            geofence.start()
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

    // The unit test host must not trigger location prompts or start location updates.
    private static var isRunningTests: Bool {
        ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
            || ProcessInfo.processInfo.environment["XCTestBundlePath"] != nil
    }
}
