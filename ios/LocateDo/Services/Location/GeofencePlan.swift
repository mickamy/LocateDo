import CoreLocation

struct GeofenceRegion: Equatable {
    let id: UUID
    let latitude: Double
    let longitude: Double
    let radiusMeters: Double

    var identifier: String { id.uuidString }

    var center: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    init(place: Place) {
        id = place.id
        latitude = place.latitude
        longitude = place.longitude
        radiusMeters = place.radiusMeters
    }

    init?(identifier: String, condition: CLCondition) {
        guard let id = UUID(uuidString: identifier),
              let circle = condition as? CLMonitor.CircularGeographicCondition else {
            return nil
        }
        self.id = id
        latitude = circle.center.latitude
        longitude = circle.center.longitude
        radiusMeters = circle.radius
    }
}

enum GeofencePlan {
    static let limit = 20

    static func regions(for places: [Place], near location: CLLocation?) -> [GeofenceRegion] {
        Nearby.places(places, from: location)
            .prefix(limit)
            .map { GeofenceRegion(place: $0.place) }
    }

    static func changes(
        from current: [String: GeofenceRegion],
        to desired: [GeofenceRegion]
    ) -> (add: [GeofenceRegion], remove: [String]) {
        let add = desired.filter { current[$0.identifier] != $0 }
        let desiredIdentifiers = Set(desired.map(\.identifier))
        let remove = current.keys.filter { !desiredIdentifiers.contains($0) }.sorted()
        return (add, remove)
    }
}
