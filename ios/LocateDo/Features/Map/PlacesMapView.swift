import MapKit
import SwiftData
import SwiftUI

struct PlacesMapView: View {
    @Environment(AppRouter.self) private var router
    @Query(sort: \Place.sortOrder) private var places: [Place]
    @State private var position: MapCameraPosition = .userLocation(fallback: .automatic)
    @State private var selectedPlace: Place?
    let open: (Place) -> Void

    var body: some View {
        Map(position: $position, selection: $selectedPlace) {
            UserAnnotation()
            ForEach(places) { place in
                Marker(place.name, systemImage: place.categoryStyle.systemImage, coordinate: place.coordinate)
                    .tint(place.categoryStyle.tint)
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
        .onChange(of: selectedPlace) {
            if let selectedPlace {
                open(selectedPlace)
                self.selectedPlace = nil
            }
        }
        .trackScreen(.map, parameters: [.source: "home_preview"])
        .navigationTitle(Text(.tabMap))
        .maintenanceBanner()
        .navigationBarTitleDisplayMode(.inline)
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
