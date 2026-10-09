import SwiftData
import SwiftUI
import TipKit
import UIKit

@main
struct LocateDoApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    private let container: ModelContainer
    private let router = AppRouter()
    private let preferences = AppPreferences()
    private let promotionsConsent: PromotionsConsent
    private let completionNotices: CompletionNotices
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
    private let watch = WatchBridge()
    private let undo = TodoUndo()
    private let entitlements = LocateDoApp.makeEntitlements()

    init() {
        container = Self.makeContainer()
        promotionsConsent = PromotionsConsent(preferences: preferences)
        completionNotices = CompletionNotices(preferences: preferences)
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
        devices = Self.makeDevices(api: api, authenticator: authenticator, preferences: preferences)
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
        connectNotifications()
        connectWatch()
        let isPro = { [entitlements, container] in
            let plan = try? container.mainContext.fetch(FetchDescriptor<SyncState>()).first?.plan
            return Entitlements.isPro(hasEntitlement: entitlements.hasEntitlement, plan: plan)
        }
        writes.isPro = isPro
        writes.currentUserID = { [authenticator] in
            authenticator.session?.userID
        }
        sync.isPro = isPro
        sync.onLimitRejected = { [router, writes] limit in
            writes.logLimitReached(limit)
            router.pendingPaywall = limit.trigger
        }
        authenticator.onSessionEnded = { [account] in
            Task {
                await account.endSession()
            }
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
            startServices()
        }
    }

    private func connectNotifications() {
        notifier.onOpened = { [writes] placeID in
            writes.arrivalOpened(placeID: placeID)
            CheckOffTip.openedFromArrival = true
        }
        notifier.onCheckOff = { [writes, sync] todoIDs in
            await Self.checkOff(todoIDs, via: .action, writes: writes, sync: sync)
        }
        notifier.onCampaignLink = { url in
            UIApplication.shared.open(url)
        }
        geofence.onArrival = { [sync] in
            await sync.sync()
        }
        geofence.onNotified = { [promotionsConsent] in
            promotionsConsent.arrivalNotified()
        }
        geofence.currentUserID = { [authenticator] in
            authenticator.session?.userID
        }
        promotionsConsent.onChanged = { [devices] in
            await devices.promotionsConsentChanged()
        }
        completionNotices.onChanged = { [devices] in
            await devices.register()
        }
        AppDelegate.onDeviceToken = { [devices] token in
            await devices.received(deviceToken: token)
        }
        AppDelegate.onRemoteNotification = { [sync] in
            await sync.sync()
        }
    }

    private func startServices() {
        InstallDate.record(defaults: .standard, now: .now)
        try? Tips.configure()
        GoogleSignInSetup.configure()
        geofence.start()
        network.start()
        entitlements.start()
        watch.start()
        Task { [account, entitlements] in
            await account.linkPurchases(entitlements)
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
        .environment(promotionsConsent)
        .environment(completionNotices)
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
        .environment(watch)
        .environment(undo)
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
        account.onHouseholdReady = { [weak account, devices, sync, geofence, entitlements] in
            Task {
                await account?.linkPurchases(entitlements)
                await devices.register()
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

    // Firebase starts first so RevenueCat can be handed the Analytics instance ID as it is configured.
    private static func makeEntitlements() -> Entitlements {
        if !isRunningTests {
            Analytics.configure()
        }
        return Entitlements(source: makeEntitlementSource())
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

extension LocateDoApp {
    private static func makeAppStatus(gate: MaintenanceGate) -> AppStatusStore {
        var url: URL?
        if !isRunningTests, let raw = Bundle.main.object(forInfoDictionaryKey: "LocateDoAppStatusURL") as? String {
            url = URL(string: raw)
        }
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        return AppStatusStore(url: url, currentVersion: version, gate: gate)
    }

    private static func makeDevices(
        api: APIClient,
        authenticator: Authenticator,
        preferences: AppPreferences
    ) -> DeviceRegistration {
        DeviceRegistration(
            devices: api.device,
            authenticator: authenticator,
            promotionsConsent: { [preferences] in preferences.promotionsConsent },
            completionNotices: { [preferences] in preferences.completionNotices }
        )
    }

    private func connectWatch() {
        watch.onCheckOff = { [writes, sync] checkOff in
            await Self.checkOff(checkOff.todoIDs, via: CompletionVia(checkOff.source), writes: writes, sync: sync)
        }
        watch.snapshot = { [container, locationProvider, authenticator] in
            let descriptor = FetchDescriptor<Place>(sortBy: [SortDescriptor(\.sortOrder), SortDescriptor(\.name)])
            let places = (try? container.mainContext.fetch(descriptor)) ?? []
            return WatchSnapshot(
                places: Nearby.places(places, from: locationProvider.location).map(\.place),
                userID: authenticator.session?.userID
            )
        }
    }

    // Woken in the background by a notification action or the Watch, so it asks for time to send the writes.
    private static func checkOff(_ todoIDs: [UUID], via: CompletionVia, writes: LocalWrites, sync: SyncEngine) async {
        let task = UIApplication.shared.beginBackgroundTask(withName: "check-off")
        writes.checkOff(todoIDs, via: via)
        if via == .action {
            CheckOffTip().invalidate(reason: .actionPerformed)
        }
        await sync.drain()
        UIApplication.shared.endBackgroundTask(task)
    }
}
