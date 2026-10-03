import MapKit
import SwiftData
import SwiftUI

struct NearbyView: View {
    @Environment(LocationProvider.self) private var locationProvider
    @Query(sort: \Place.sortOrder) private var places: [Place]
    @State private var isAddingPlace = false

    private var nearbyPlaces: [NearbyPlace] {
        Nearby.places(places, from: locationProvider.location)
    }

    var body: some View {
        NavigationStack {
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
            .sheet(isPresented: $isAddingPlace) {
                PlaceEditorView()
            }
            .navigationDestination(for: Place.self) { place in
                PlaceDetailView(place: place)
            }
            .task {
                locationProvider.start()
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
                isAddingPlace = true
            }
            .buttonStyle(.borderedProminent)
        }
    }

    private var list: some View {
        List {
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
