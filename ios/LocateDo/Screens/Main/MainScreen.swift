import OSLog
import SwiftData
import SwiftUI

// Home and the screens pushed over it, with what floats above them all: the add button, the Undo banner, and the
// sheets.
struct MainScreen: View {
    @Environment(Navigator.self) private var navigator
    @Environment(AppPreferences.self) private var preferences
    @Environment(AccountManager.self) private var account
    @Environment(SyncEngine.self) private var sync
    @Environment(AppStatusStore.self) private var appStatus
    @Environment(Entitlements.self) private var entitlements
    @Environment(Authenticator.self) private var authenticator
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @Environment(PromotionsConsent.self) private var promotionsConsent
    @Environment(WatchBridge.self) private var watch
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    @Query private var places: [Place]

    var body: some View {
        @Bindable var navigator = navigator
        NavigationStack(path: $navigator.path) {
            HomeScreen()
                .navigationDestination(for: Route.self, destination: destination)
        }
        .contentMargins(.bottom, AddButton.reservedHeight, for: .scrollContent)
        .overlay(alignment: .bottomTrailing) {
            AddButton()
        }
        .overlay(alignment: .bottom) {
            UndoBanner()
                .padding(.bottom, AddButton.reservedHeight + 4)
        }
        .presentsSheets(from: 0, onClosed: sheetsClosed)
        .launchNotices()
        .task {
            locationProvider.start()
            await notifier.refreshAuthorizationStatus()
        }
        .task(id: scenePhase) {
            await becameActive()
        }
        .onChange(of: navigator.pendingPlaceID, initial: true) {
            showPendingPlace()
        }
        .onChange(of: places.count) {
            showPendingPlace()
        }
    }

    @ViewBuilder
    private func destination(_ route: Route) -> some View {
        switch route {
        case .place(let place):
            PlaceScreen(place: place)
        case .map:
            MapScreen()
        case .allTodos:
            AllTodosScreen()
        }
    }

    private func showPendingPlace() {
        guard let placeID = navigator.pendingPlaceID,
              let place = places.first(where: { $0.id == placeID }) else {
            return
        }
        navigator.show(place)
    }

    // A new place first gets the reminder setup it may need; requests from outside wait for their turn.
    private func sheetsClosed() {
        Task {
            if let request = await reminderSetupRequest() {
                navigator.present(.reminderSetup(request))
                return
            }
            navigator.presentWaiting()
        }
    }

    private func reminderSetupRequest() async -> ReminderSetupRequest? {
        guard navigator.didAddPlace else {
            return nil
        }
        navigator.didAddPlace = false
        await notifier.refreshAuthorizationStatus()
        let permissions = ReminderSetup.Permissions(
            location: locationProvider.authorizationStatus,
            preciseLocation: locationProvider.hasPreciseLocation,
            notifications: notifier.authorizationStatus
        )
        let now = Date.now
        let missing = ReminderSetup.missing(permissions)
        guard ReminderSetup.isDue(permissions, shownAt: preferences.reminderSetupShownAt,
                                  never: preferences.reminderSetupNever, now: now),
              !missing.isEmpty else {
            return nil
        }
        preferences.reminderSetupShownAt = now
        preferences.reminderSetupShownCount += 1
        return ReminderSetupRequest(shownCount: preferences.reminderSetupShownCount, missing: missing)
    }

    private func becameActive() async {
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
        await DailyStateReporter.report(
            context: modelContext,
            entitlements: entitlements,
            authenticator: authenticator,
            locationProvider: locationProvider,
            notifier: notifier,
            promotionsConsent: promotionsConsent.isOn,
            watchAppInstalled: watch.hasWatchApp
        )
        requestPromotionsPromptIfDue()
    }

    private func requestPromotionsPromptIfDue() {
        let isDue = promotionsConsent.shouldPrompt(
            notificationAuth: notifier.authorizationStatus,
            lastArrivalOpenedAt: notifier.lastOpenedAt,
            now: .now
        )
        guard isDue, !isShowingSomethingElse else {
            return
        }
        navigator.request(.promotions)
    }

    private var isShowingSomethingElse: Bool {
        navigator.isPresenting
            || navigator.hasWaiting
            || appStatus.pendingNotice != nil
            || preferences.hasPendingSessionEndedNotice
            || preferences.hasPendingRemovedNotice
    }
}
