import MapKit
import SwiftData
import SwiftUI

struct NearbyView: View {
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @Environment(AppPreferences.self) private var preferences
    @Environment(AppRouter.self) private var router
    @Environment(PromotionsConsent.self) private var promotionsConsent
    @Query(sort: \Place.sortOrder) private var places: [Place]
    @State private var path: [HomeRoute] = []
    @State private var sheet: HomeSheet?
    @State private var didAddPlace = false

    private var placeForNewTodo: Place? {
        if let last = path.last {
            return last.place
        }
        return nearbyPlaces.first?.place
    }

    private var nearbyPlaces: [NearbyPlace] {
        Nearby.places(places, from: locationProvider.location)
    }

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if places.isEmpty {
                    emptyState
                        .syncRefreshableEmptyState()
                } else {
                    list
                        .syncRefreshable()
                }
            }
            .trackScreen(.home)
            .navigationBarTitleDisplayMode(.inline)
            .maintenanceBanner()
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        sheet = .settings
                    } label: {
                        Label(.tabSettings, systemImage: "gearshape")
                    }
                    .accessibilityIdentifier("home.settings")
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        Analytics.log(.shareTapped, parameters: [.source: SharingSource.home.rawValue])
                        sheet = .sharing
                    } label: {
                        Label(.sharingTitle, systemImage: "person.2")
                    }
                }
            }
            .navigationDestination(for: HomeRoute.self, destination: destination)
            .task {
                locationProvider.start()
                await notifier.refreshAuthorizationStatus()
            }
            .onChange(of: router.pendingPlaceID, initial: true) {
                openPendingPlace()
            }
            .onChange(of: places.count) {
                openPendingPlace()
            }
            .onChange(of: router.isAllTodosRequested, initial: true) {
                openRequestedAllTodos()
            }
            .onChange(of: router.pendingPaywall, initial: true) {
                presentWaitingRequest()
            }
            .onChange(of: router.pendingInvite?.id, initial: true) {
                presentWaitingRequest()
            }
            .onChange(of: router.isPromotionsPromptRequested, initial: true) {
                presentWaitingRequest()
            }
            .onChange(of: router.presentationsInsideHome) {
                // Waits out the closing animation, which a new sheet cannot be presented over.
                Task {
                    try? await Task.sleep(for: .milliseconds(600))
                    presentWaitingRequest()
                }
            }
        }
        // Home offers both; the screens opened from it add to-dos, except the map.
        .floatingAddArea(isShown: path.last != .map) {
            if path.isEmpty {
                FloatingAddMenu {
                    Button(.todoEditorTitle, systemImage: "checklist") {
                        sheet = .addTodo
                    }
                    Button(.homeAddPlace, systemImage: "mappin.and.ellipse") {
                        sheet = .addPlace
                    }
                }
                .accessibilityIdentifier("home.add")
            } else {
                FloatingAddButton(.todoEditorTitle) {
                    sheet = .addTodo
                }
            }
        }
        .animation(.snappy, value: path.isEmpty)
        .sheet(item: $sheet, onDismiss: sheetClosed, content: sheetContent)
    }

    @ViewBuilder
    private func destination(_ route: HomeRoute) -> some View {
        switch route {
        case .place(let place):
            PlaceDetailView(place: place)
        case .map:
            PlacesMapView { place in
                path.append(.place(place))
            }
        case .allTodos:
            TodoListView {
                sheet = .addTodo
            }
        }
    }

    private func openRequestedAllTodos() {
        guard router.isAllTodosRequested else {
            return
        }
        router.isAllTodosRequested = false
        closeSettings()
        path = [.allTodos]
    }

    private func openPendingPlace() {
        guard let placeID = router.pendingPlaceID,
              let place = places.first(where: { $0.id == placeID }) else {
            return
        }
        router.pendingPlaceID = nil
        closeSettings()
        path = [.place(place)]
    }

    private var emptyState: some View {
        ContentUnavailableView {
            Label(.homeEmptyTitle, systemImage: "mappin.and.ellipse")
        } description: {
            Text(.homeEmptyMessage)
        } actions: {
            Button(.homeAddPlace) {
                sheet = .addPlace
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
    }

    private var permissionIssue: PermissionBanner? {
        PermissionBanner(
            location: locationProvider.authorizationStatus,
            preciseLocation: locationProvider.hasPreciseLocation,
            notifications: notifier.authorizationStatus
        )
    }

    private var list: some View {
        List {
            if let permissionIssue {
                Section {
                    Button {
                        Analytics.log(.permissionBannerTapped, parameters: [.kind: permissionIssue.rawValue])
                        sheet = .settings
                    } label: {
                        Label {
                            Text(permissionIssue.message)
                        } icon: {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(.orange)
                        }
                    }
                    .listRowBackground(Color.orange.opacity(0.12))
                }
            }
            Section {
                // A glance at where you are; the full map is where it can be moved around.
                Button {
                    path.append(.map)
                } label: {
                    Map(initialPosition: .userLocation(fallback: .automatic)) {
                        UserAnnotation()
                    }
                    .frame(height: 140)
                    .allowsHitTesting(false)
                    // The map ignores touches so it cannot be dragged here; this layer takes the tap instead.
                    .overlay {
                        Color.clear.contentShape(Rectangle())
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(.tabMap))
                .accessibilityIdentifier("home.map")
                .listRowInsets(EdgeInsets())
                NavigationLink(value: HomeRoute.allTodos) {
                    LabeledContent {
                        Text(Nearby.openTodoCount(nearbyPlaces), format: .number)
                    } label: {
                        Label(.homeAllTodos, systemImage: "checklist")
                    }
                }
                .accessibilityIdentifier("home.allTodos")
            }
            Section {
                ForEach(nearbyPlaces) { nearby in
                    NavigationLink(value: HomeRoute.place(nearby.place)) {
                        PlaceRow(nearby: nearby)
                    }
                }
            }
        }
    }
}

extension NearbyView {
    @ViewBuilder
    private func sheetContent(_ sheet: HomeSheet) -> some View {
        switch sheet {
        case .addTodo:
            TodoEditorView(
                place: placeForNewTodo,
                onPlaceAdded: { _ in didAddPlace = true },
                onAddedAtNewPlace: { added in path.append(.place(added)) }
            )
        case .addPlace:
            PlaceEditorView(
                defaultRadiusMeters: preferences.defaultRadiusMeters,
                onPickExisting: { existing in path = [.place(existing)] },
                onSave: { _ in didAddPlace = true }
            )
        case .settings:
            SettingsView()
        case .sharing:
            SharingView()
        case .reminderSetup(let request):
            ReminderSetupView(shownCount: request.shownCount, missing: request.missing)
        case .paywall(let trigger):
            PaywallView(trigger: trigger)
        case .invite(let invite):
            NavigationStack {
                AcceptInviteView(token: invite.token)
            }
        case .promotions:
            PromotionsConsentSheet()
        }
    }

    // A new place first gets the reminder setup it may need; requests from outside wait for their turn.
    private func sheetClosed() {
        Task {
            if let request = await reminderSetupRequest() {
                sheet = .reminderSetup(request)
                return
            }
            presentWaitingRequest()
        }
    }

    private func presentWaitingRequest() {
        guard sheet == nil, router.presentationsInsideHome == 0 else {
            return
        }
        if let trigger = router.pendingPaywall {
            router.pendingPaywall = nil
            sheet = .paywall(trigger)
        } else if let invite = router.pendingInvite {
            router.pendingInvite = nil
            sheet = .invite(invite)
        } else if router.isPromotionsPromptRequested {
            router.isPromotionsPromptRequested = false
            promotionsConsent.promptShown(
                daysSinceInstall: InstallDate.daysSinceInstall(defaults: .standard, now: .now),
                notificationAuth: notifier.authorizationStatus
            )
            sheet = .promotions
        }
    }

    private func closeSettings() {
        if case .settings = sheet {
            sheet = nil
        }
    }

    private func reminderSetupRequest() async -> ReminderSetupRequest? {
        guard didAddPlace else {
            return nil
        }
        didAddPlace = false
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
}
