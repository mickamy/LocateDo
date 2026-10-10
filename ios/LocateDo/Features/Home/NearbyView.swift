import MapKit
import SwiftData
import SwiftUI

struct NearbyView: View {
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @Environment(AppPreferences.self) private var preferences
    @Environment(AppRouter.self) private var router
    @Query(sort: \Place.sortOrder) private var places: [Place]
    @State private var isAddingPlace = false
    @State private var reminderSetup: ReminderSetupRequest?
    @State private var path: [HomeRoute] = []
    @State private var isAddingTodo = false

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
            .navigationTitle(Text(.tabHome))
            .maintenanceBanner()
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        router.isSettingsPresented = true
                    } label: {
                        Label(.tabSettings, systemImage: "gearshape")
                    }
                    .accessibilityIdentifier("home.settings")
                }
                ToolbarItem(placement: .primaryAction) {
                    SharingButton(source: .home) {
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
            .onChange(of: router.isAddPlaceRequested, initial: true) {
                openRequestedAddPlace()
            }
            .onChange(of: router.isAllTodosRequested, initial: true) {
                openRequestedAllTodos()
            }
        }
        // Home offers both; the screens opened from it add to-dos, except the map.
        .floatingAddArea(isShown: path.last != .map) {
            if path.isEmpty {
                FloatingAddMenu {
                    Button(.todoEditorTitle, systemImage: "checklist") {
                        isAddingTodo = true
                    }
                    Button(.homeAddPlace, systemImage: "mappin.and.ellipse") {
                        isAddingPlace = true
                    }
                }
                .accessibilityIdentifier("home.add")
            } else {
                FloatingAddButton(.todoEditorTitle) {
                    isAddingTodo = true
                }
            }
        }
        .animation(.snappy, value: path.isEmpty)
        .sheet(isPresented: $isAddingTodo, onDismiss: offerReminderSetupIfNeeded) {
            TodoEditorView(place: placeForNewTodo) { added in
                path.append(.place(added))
            }
        }
        .sheet(isPresented: $isAddingPlace, onDismiss: offerReminderSetupIfNeeded) {
            PlaceEditorView(defaultRadiusMeters: preferences.defaultRadiusMeters)
        }
        .sheet(item: $reminderSetup) { request in
            ReminderSetupView(shownCount: request.shownCount, missing: request.missing)
        }
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
                isAddingTodo = true
            }
        }
    }

    private func openRequestedAllTodos() {
        guard router.isAllTodosRequested else {
            return
        }
        router.isAllTodosRequested = false
        path = [.allTodos]
    }

    private func openRequestedAddPlace() {
        guard router.isAddPlaceRequested else {
            return
        }
        router.isAddPlaceRequested = false
        isAddingPlace = true
    }

    private func offerReminderSetupIfNeeded() {
        guard router.didAddPlace else {
            return
        }
        router.didAddPlace = false
        Task {
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
                return
            }
            preferences.reminderSetupShownAt = now
            preferences.reminderSetupShownCount += 1
            reminderSetup = ReminderSetupRequest(shownCount: preferences.reminderSetupShownCount, missing: missing)
        }
    }

    private func openPendingPlace() {
        guard let placeID = router.pendingPlaceID,
              let place = places.first(where: { $0.id == placeID }) else {
            return
        }
        router.pendingPlaceID = nil
        path = [.place(place)]
    }

    private var emptyState: some View {
        ContentUnavailableView {
            Label(.homeEmptyTitle, systemImage: "mappin.and.ellipse")
        } description: {
            Text(.homeEmptyMessage)
        } actions: {
            Button(.homeAddPlace) {
                isAddingPlace = true
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
                        router.isSettingsPresented = true
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

private struct ReminderSetupRequest: Identifiable {
    let id = UUID()
    let shownCount: Int
    let missing: [ReminderSetup.Need]
}
