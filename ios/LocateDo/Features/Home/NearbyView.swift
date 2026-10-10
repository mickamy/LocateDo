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
    @State private var didSavePlace = false
    @State private var reminderSetup: ReminderSetupRequest?
    @State private var path = NavigationPath()

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
                    SharingButton(source: .home) {
                        Label(.sharingTitle, systemImage: "person.2")
                    }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isAddingPlace = true
                    } label: {
                        Label(.homeAddPlace, systemImage: "plus")
                    }
                }
            }
            .sheet(isPresented: $isAddingPlace, onDismiss: offerReminderSetupIfNeeded) {
                PlaceEditorView(defaultRadiusMeters: preferences.defaultRadiusMeters) {
                    didSavePlace = true
                }
            }
            .sheet(item: $reminderSetup) { request in
                ReminderSetupView(shownCount: request.shownCount, missing: request.missing)
            }
            .navigationDestination(for: Place.self) { place in
                PlaceDetailView(place: place)
            }
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
        }
    }

    private func openRequestedAddPlace() {
        guard router.isAddPlaceRequested else {
            return
        }
        router.isAddPlaceRequested = false
        isAddingPlace = true
    }

    private func offerReminderSetupIfNeeded() {
        guard didSavePlace else {
            return
        }
        didSavePlace = false
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
        path = NavigationPath([place])
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
                        router.selectedTab = .settings
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
                Map(initialPosition: .userLocation(fallback: .automatic)) {
                    UserAnnotation()
                }
                .frame(height: 140)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
                .listRowInsets(EdgeInsets())
            } footer: {
                let count = Nearby.openTodoCount(nearbyPlaces)
                if count > 0 {
                    Text(.homeOpenSummary(count))
                }
            }
            Section {
                ForEach(nearbyPlaces) { nearby in
                    NavigationLink(value: nearby.place) {
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
