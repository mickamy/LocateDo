import OSLog
import SwiftData
import SwiftUI

struct RootView: View {
    @Environment(AppRouter.self) private var router
    @Environment(AppPreferences.self) private var preferences
    @Environment(AccountManager.self) private var account
    @Environment(SyncEngine.self) private var sync
    @Environment(AppStatusStore.self) private var appStatus
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if appStatus.requiresUpdate {
                UpdateRequiredView()
                    .task(id: scenePhase) {
                        await refreshAppStatus()
                    }
            } else if preferences.hasCompletedOnboarding {
                main
            } else {
                OnboardingView()
                    .task(id: scenePhase) {
                        await refreshAppStatus()
                    }
            }
        }
        .onOpenURL { url in
            if let token = InviteLink.token(from: url.absoluteString) {
                router.pendingInvite = PendingInvite(token: token)
            }
        }
    }

    private var main: some View {
        @Bindable var router = router
        return tabs
                .safeAreaInset(edge: .top, spacing: 0) {
                    MaintenanceBanner()
                }
                .alert(
                    Text(.announcementTitle),
                    isPresented: announcement,
                    presenting: appStatus.pendingNotice
                ) { notice in
                    Button(.commonOk) {
                        appStatus.markNoticeShown(notice)
                    }
                } message: { notice in
                    Text(notice.message.text(for: Bundle.main.preferredLocalizations.first) ?? "")
                }
                .alert(Text(.sessionEndedTitle), isPresented: sessionEndedNotice) {
                    Button(.commonOk) {}
                } message: {
                    Text(.sessionEndedMessage)
                }
                .alert(Text(.removedTitle), isPresented: removedNotice) {
                    Button(.commonOk) {}
                } message: {
                    Text(.removedMessage)
                }
                .sheet(item: $router.pendingInvite) { invite in
                    NavigationStack {
                        AcceptInviteView(token: invite.token)
                    }
                }
                .sheet(item: $router.pendingPaywall) { trigger in
                    PaywallView(trigger: trigger)
                }
    }

    private func refreshAppStatus() async {
        guard scenePhase == .active else {
            return
        }
        await appStatus.refresh()
    }

    private var announcement: Binding<Bool> {
        Binding {
            appStatus.pendingNotice != nil
        } set: { _ in }
    }

    private var removedNotice: Binding<Bool> {
        Binding {
            preferences.hasPendingRemovedNotice
        } set: { isPresented in
            preferences.hasPendingRemovedNotice = isPresented
        }
    }

    private var sessionEndedNotice: Binding<Bool> {
        Binding {
            preferences.hasPendingSessionEndedNotice
        } set: { isPresented in
            preferences.hasPendingSessionEndedNotice = isPresented
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
            await appStatus.refresh()
            guard !appStatus.requiresUpdate else {
                return
            }
            do {
                try await account.uploadLocalDataIfNeeded()
            } catch {
                Logger(subsystem: "com.locatedo.LocateDo", category: "account")
                    .error("Initial upload failed: \(error, privacy: .public)")
            }
            await sync.sync()
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
    let api = APIClient(
        environment: APIEnvironment(baseURL: URL(string: "http://localhost:8080")!),
        tokens: tokens,
        gate: MaintenanceGate()
    )
    let authenticator = Authenticator(
        store: KeychainSessionStore(service: "preview"),
        account: api.account,
        tokens: tokens
    )
    let sync = SyncEngine(
        places: api.place,
        todos: api.todo,
        categories: api.category,
        syncService: api.sync,
        authenticator: authenticator,
        context: container.mainContext
    )
    let account = AccountManager(
        account: api.account,
        household: api.household,
        authenticator: authenticator,
        context: container.mainContext
    )
    RootView()
        .modelContainer(container)
        .environment(router)
        .environment(AppPreferences(defaults: UserDefaults(suiteName: "preview")!))
        .environment(locationProvider)
        .environment(notifier)
        .environment(GeofenceMonitor(container: container, notifier: notifier, locationProvider: locationProvider))
        .environment(authenticator)
        .environment(sync)
        .environment(account)
        .environment(Entitlements(source: nil))
        .environment(HouseholdManager(
            household: api.household,
            authenticator: authenticator,
            context: container.mainContext,
            account: account,
            sync: sync
        ))
        .environment(LocalWrites(context: container.mainContext) { authenticator.isSignedIn })
        .environment(AppStatusStore(url: nil, currentVersion: "1.0", gate: MaintenanceGate()))
}
