import CoreLocation

// A point picked on the map, with what is known about it so far.
nonisolated struct PlacePick {
    let coordinate: CLLocationCoordinate2D
    let source: PlaceSource
    var name: String?
    var address: String?
    var suggestion: BuiltinCategory?

    func isAt(_ other: CLLocationCoordinate2D) -> Bool {
        coordinate.latitude == other.latitude && coordinate.longitude == other.longitude
    }
}
