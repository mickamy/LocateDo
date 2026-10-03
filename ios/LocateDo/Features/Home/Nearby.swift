import CoreLocation

struct NearbyPlace: Identifiable {
    let place: Place
    let distanceMeters: Double?

    var id: UUID { place.id }
}

enum Nearby {
    static func places(_ places: [Place], from location: CLLocation?) -> [NearbyPlace] {
        let nearby = places.map { place in
            NearbyPlace(place: place, distanceMeters: location?.distance(from: place.location))
        }
        guard location != nil else {
            return nearby
        }
        return nearby.sorted { lhs, rhs in
            (lhs.distanceMeters ?? .infinity) < (rhs.distanceMeters ?? .infinity)
        }
    }

    static func openTodoCount(_ places: [NearbyPlace]) -> Int {
        places.reduce(0) { $0 + $1.place.openTodos.count }
    }
}

extension Place {
    var location: CLLocation {
        CLLocation(latitude: latitude, longitude: longitude)
    }
}
