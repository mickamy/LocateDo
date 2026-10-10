import MapKit
import SwiftData
import SwiftUI

// The root: a glance at the map, every open to-do, then the places, nearest first.
struct HomeScreen: View {
    @Environment(Navigator.self) private var navigator
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @Query(sort: \Place.sortOrder) private var places: [Place]

    private var nearbyPlaces: [NearbyPlace] {
        Nearby.places(places, from: locationProvider.location)
    }

    var body: some View {
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
                    navigator.present(.settings(.home))
                } label: {
                    Label(.tabSettings, systemImage: "gearshape")
                }
                .accessibilityIdentifier("home.settings")
            }
            ToolbarItem(placement: .primaryAction) {
                Button {
                    Analytics.log(.shareTapped, parameters: [.source: SharingSource.home.rawValue])
                    navigator.present(.sharing)
                } label: {
                    Label(.sharingTitle, systemImage: "person.2")
                }
            }
        }
    }

    private var emptyState: some View {
        ContentUnavailableView {
            Label(.homeEmptyTitle, systemImage: "mappin.and.ellipse")
        } description: {
            Text(.homeEmptyMessage)
        } actions: {
            Button(.homeAddPlace) {
                navigator.present(.addPlace(AddPlace(entry: .homeEmpty)))
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
    }

    private var list: some View {
        List {
            if let issue = PermissionBanner(
                location: locationProvider.authorizationStatus,
                preciseLocation: locationProvider.hasPreciseLocation,
                notifications: notifier.authorizationStatus
            ) {
                Section {
                    permissionBanner(issue)
                }
            }
            Section {
                mapPreview
                NavigationLink(value: Route.allTodos(entry: .home)) {
                    LabeledContent {
                        Text(Nearby.openTodoCount(nearbyPlaces), format: .number)
                    } label: {
                        Label(.homeAllTodos, systemImage: "checklist")
                    }
                }
                .accessibilityIdentifier("home.allTodos")
            }
            Section {
                ForEach(Array(nearbyPlaces.enumerated()), id: \.element.id) { index, nearby in
                    NavigationLink(value: Route.place(nearby.place, entry: .homeList, rank: index + 1)) {
                        NearbyPlaceRow(nearby: nearby)
                    }
                }
            }
        }
    }

    private func permissionBanner(_ issue: PermissionBanner) -> some View {
        Button {
            Analytics.log(.permissionBannerTapped, parameters: [.kind: issue.rawValue])
            navigator.present(.settings(.permissionBanner))
        } label: {
            Label {
                Text(issue.message)
            } icon: {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(.orange)
            }
        }
        .listRowBackground(Color.orange.opacity(0.12))
    }

    // A glance at where you are; the full map is where it can be moved around.
    private var mapPreview: some View {
        Button {
            navigator.push(.map)
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
    }
}
