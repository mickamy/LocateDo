import OSLog
import SwiftData
import SwiftUI

struct RootView: View {
    @Environment(AppRouter.self) private var router
    @Environment(AppPreferences.self) private var preferences
    @Environment(AccountManager.self) private var account
    @Environment(SyncEngine.self) private var sync
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        if preferences.hasCompletedOnboarding {
            tabs
        } else {
            OnboardingView()
        }
    }

    private var tabs: some View {
        @Bindable var router = router
        return TabView(selection: $router.selectedTab) {
            Tab(.tabHome, systemImage: "house", value: AppTab.home) {
                NearbyView()
            }
            Tab(.tabMap, systemImage: "map", value: AppTab.map) {
                MapTabView()
            }
            Tab(.tabTodos, systemImage: "checklist", value: AppTab.todos) {
                TodoListView()
            }
            Tab(.tabSettings, systemImage: "gearshape", value: AppTab.settings) {
                SettingsView()
            }
        }
        .task(id: scenePhase) {
            guard scenePhase == .active else {
                return
            }
            do {
                try await account.uploadLocalDataIfNeeded()
            } catch {
                Logger(subsystem: "com.locatedo.LocateDo", category: "account")
                    .error("Initial upload failed: \(error, privacy: .public)")
            }
            await sync.drain()
        }
    }
}

#Preview {
    // swiftlint:disable:next force_try
    let container = try! AppModelContainer.make(inMemory: true)
    let router = AppRouter()
    let locationProvider = LocationProvider()
    let notifier = ArrivalNotifier(router: router)
    let tokens = AccessTokenStore()
    let api = APIClient(environment: APIEnvironment(baseURL: URL(string: "http://localhost:8080")!), tokens: tokens)
    let authenticator = Authenticator(
        store: KeychainSessionStore(service: "preview"),
        account: api.account,
        tokens: tokens
    )
    RootView()
        .modelContainer(container)
        .environment(router)
        .environment(AppPreferences(defaults: UserDefaults(suiteName: "preview")!))
        .environment(locationProvider)
        .environment(notifier)
        .environment(GeofenceMonitor(container: container, notifier: notifier, locationProvider: locationProvider))
        .environment(authenticator)
        .environment(SyncEngine(
            places: api.place,
            todos: api.todo,
            categories: api.category,
            syncService: api.sync,
            authenticator: authenticator,
            context: container.mainContext
        ))
        .environment(AccountManager(
            account: api.account,
            household: api.household,
            authenticator: authenticator,
            context: container.mainContext
        ))
        .environment(LocalWrites(context: container.mainContext) { authenticator.isSignedIn })
}
