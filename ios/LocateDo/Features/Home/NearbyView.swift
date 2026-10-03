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
    @State private var isExplainingAlwaysLocation = false
    @State private var path = NavigationPath()

    private var nearbyPlaces: [NearbyPlace] {
        Nearby.places(places, from: locationProvider.location)
    }

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if places.isEmpty {
                    emptyState
                } else {
                    list
                }
            }
            .navigationTitle(Text(.tabHome))
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isAddingPlace = true
                    } label: {
                        Label(.homeAddPlace, systemImage: "plus")
                    }
                }
            }
            .sheet(isPresented: $isAddingPlace, onDismiss: offerAlwaysLocationIfNeeded) {
                PlaceEditorView(defaultRadiusMeters: preferences.defaultRadiusMeters) {
                    didSavePlace = true
                }
            }
            .sheet(isPresented: $isExplainingAlwaysLocation) {
                AlwaysLocationPromptView()
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
        }
    }

    private func offerAlwaysLocationIfNeeded() {
        guard didSavePlace else {
            return
        }
        didSavePlace = false
        guard !preferences.hasPromptedAlwaysLocation,
              locationProvider.authorizationStatus == .authorizedWhenInUse else {
            return
        }
        preferences.hasPromptedAlwaysLocation = true
        isExplainingAlwaysLocation = true
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

    private var permissionIssue: LocalizedStringResource? {
        switch locationProvider.authorizationStatus {
        case .authorizedWhenInUse, .denied, .restricted:
            return .homePermissionBannerLocation
        default:
            break
        }
        if notifier.authorizationStatus == .denied {
            return .homePermissionBannerNotifications
        }
        return nil
    }

    private var list: some View {
        List {
            if let permissionIssue {
                Section {
                    Button {
                        router.selectedTab = .settings
                    } label: {
                        Label {
                            Text(permissionIssue)
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
                .listRowInsets(EdgeInsets())
            } footer: {
                let count = Nearby.openTodoCount(nearbyPlaces)
                if count > 0 {
                    Text(.homeNearbySummary(count))
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
