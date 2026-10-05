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
    private let households: HouseholdManager
    private let appStatus: AppStatusStore
    private let entitlements = Entitlements(source: LocateDoApp.makeEntitlementSource())

    init() {
        container = Self.makeContainer()
        #if DEBUG
        ScreenshotSeed.replaceIfRequested(in: container.mainContext)
        #endif
        notifier = ArrivalNotifier(router: router)
        geofence = GeofenceMonitor(container: container, notifier: notifier, locationProvider: locationProvider)
        let tokens = AccessTokenStore()
        let gate = MaintenanceGate()
        appStatus = Self.makeAppStatus(gate: gate)
        api = APIClient(environment: .current, tokens: tokens, gate: gate)
        authenticator = Self.makeAuthenticator(api: api, tokens: tokens, preferences: preferences)
        let sync = SyncEngine(
            places: api.place,
            todos: api.todo,
            categories: api.category,
            syncService: api.sync,
            authenticator: authenticator,
            context: container.mainContext,
            gate: gate
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
        )
        households = HouseholdManager(
            household: api.household,
            authenticator: authenticator,
            context: container.mainContext,
            account: account,
            sync: sync
        )
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
        connectAccount()
        let isPro = { [entitlements, container] in
            let plan = try? container.mainContext.fetch(FetchDescriptor<SyncState>()).first?.plan
            return Entitlements.isPro(hasEntitlement: entitlements.hasEntitlement, plan: plan)
        }
        writes.isPro = isPro
        sync.isPro = isPro
        sync.onLimitRejected = { [router] limit in
            router.pendingPaywall = limit.trigger
        }
        geofence.onArrival = { [sync] in
            await sync.sync()
        }
        geofence.currentUserID = { [authenticator] in
            authenticator.session?.userID
        }
        authenticator.onSessionEnded = { [account] in
            Task {
                await account.endSession()
            }
        }
        AppDelegate.onDeviceToken = { [devices] token in
            await devices.received(deviceToken: token)
        }
        AppDelegate.onRemoteNotification = { [sync] in
            await sync.sync()
        }
        appStatus.onMaintenanceEnded = { [sync] in
            Task {
                await sync.sync()
            }
        }
        sync.onRemoved = { [households] in
            await households.handleRemoval()
        }
        households.onRemoved = { [preferences] in
            preferences.hasPendingRemovedNotice = true
        }
        if !Self.isRunningTests {
            Analytics.configure()
            geofence.start()
            network.start()
            entitlements.start()
            if let userID = authenticator.session?.userID {
                Task { [entitlements] in
                    await entitlements.logIn(userID: userID)
                }
            }
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
        .environment(households)
        .environment(entitlements)
        .environment(writes)
        .environment(appStatus)
    }

    private func connectAccount() {
        account.pushToken = { [devices] in
            devices.pushToken
        }
        account.onLocalDataReset = { [preferences, geofence, entitlements] in
            preferences.reset()
            await entitlements.logOut()
            await geofence.sync()
        }
        account.onHouseholdReady = { [authenticator, devices, sync, geofence, entitlements] in
            Task {
                if let userID = authenticator.session?.userID {
                    await entitlements.logIn(userID: userID)
                }
                await devices.registerIfSignedIn()
                await sync.sync()
                await geofence.sync()
            }
        }
        account.onSessionEnded = { [preferences, geofence, entitlements] in
            preferences.hasPendingSessionEndedNotice = true
            await entitlements.logOut()
            await geofence.sync()
        }
        account.onSignedOut = { [geofence, entitlements] in
            await entitlements.logOut()
            await geofence.sync()
        }
    }

    private static func makeAppStatus(gate: MaintenanceGate) -> AppStatusStore {
        var url: URL?
        if !isRunningTests, let raw = Bundle.main.object(forInfoDictionaryKey: "LocateDoAppStatusURL") as? String {
            url = URL(string: raw)
        }
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        return AppStatusStore(url: url, currentVersion: version, gate: gate)
    }

    private static func makeEntitlementSource() -> (any EntitlementSource)? {
        guard !isRunningTests,
              let apiKey = Bundle.main.object(forInfoDictionaryKey: "LocateDoRevenueCatAPIKey") as? String,
              !apiKey.isEmpty else {
            return nil
        }
        return RevenueCatEntitlementSource(apiKey: apiKey)
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
