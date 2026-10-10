import CoreLocation

// A saved place close enough to a new pick to be the same one: the same store's label lands within a few meters, and
// a neighboring store is usually farther than this.
nonisolated enum PlaceDuplicate {
    static let distance: CLLocationDistance = 50

    static func nearest(to coordinate: CLLocationCoordinate2D, among places: [Place]) -> Place? {
        let picked = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        var nearest: Place?
        var nearestDistance = distance
        for place in places {
            let saved = CLLocation(latitude: place.latitude, longitude: place.longitude)
            let gap = saved.distance(from: picked)
            if gap <= nearestDistance {
                nearest = place
                nearestDistance = gap
            }
        }
        return nearest
    }
}

nonisolated enum PlaceDuplicateChoice: String {
    case open
    case add
    case cancel
}
