import MapKit
import SwiftData
import SwiftUI

// Every place on a map that can be moved around; a pin opens its place.
struct MapScreen: View {
    @Environment(Navigator.self) private var navigator
    @Query(sort: \Place.sortOrder) private var places: [Place]
    @State private var position: MapCameraPosition = .userLocation(fallback: .automatic)
    @State private var selectedPlace: Place?

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
        .onChange(of: selectedPlace) {
            if let selectedPlace {
                navigator.push(.place(selectedPlace))
                self.selectedPlace = nil
            }
        }
        .trackScreen(.map, parameters: [.source: "home_preview"])
        .navigationTitle(Text(.tabMap))
        .maintenanceBanner()
        .navigationBarTitleDisplayMode(.inline)
    }
}
