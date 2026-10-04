import MapKit
import SwiftData
import SwiftUI

struct MapTabView: View {
    @Environment(AppRouter.self) private var router
    @Query(sort: \Place.sortOrder) private var places: [Place]
    @State private var position: MapCameraPosition = .userLocation(fallback: .automatic)
    @State private var selectedPlace: Place?

    var body: some View {
        NavigationStack {
            Map(position: $position, selection: $selectedPlace) {
                UserAnnotation()
                ForEach(places) { place in
                    Marker(place.name, systemImage: place.category.systemImage, coordinate: place.coordinate)
                        .tint(place.category.tint)
                        .tag(place)
                }
            }
            .mapControls {
                MapUserLocationButton()
                MapCompass()
            }
            .safeAreaInset(edge: .bottom) {
                if places.isEmpty {
                    emptyCard
                }
            }
            .navigationTitle(Text(.tabMap))
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(item: $selectedPlace) { place in
                PlaceDetailView(place: place)
            }
        }
    }

    private var emptyCard: some View {
        VStack(spacing: 12) {
            Text(.homeEmptyTitle)
                .font(.headline)
            Text(.mapEmptyMessage)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button(.homeAddPlace) {
                router.requestAddPlace()
            }
            .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        .padding()
    }
}
