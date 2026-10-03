import MapKit
import SwiftData
import SwiftUI

struct MapTabView: View {
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
            .navigationTitle(Text(.tabMap))
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(item: $selectedPlace) { place in
                PlaceDetailView(place: place)
            }
        }
    }
}
