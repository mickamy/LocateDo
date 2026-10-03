import CoreLocation
import MapKit

struct GeocodedPlace {
    let name: String?
    let address: String?
}

nonisolated enum Geocoding {
    static func lookUp(_ coordinate: CLLocationCoordinate2D) async -> GeocodedPlace? {
        let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        guard let request = MKReverseGeocodingRequest(location: location),
              let item = try? await request.mapItems.first else {
            return nil
        }
        return GeocodedPlace(name: item.name, address: item.address?.shortAddress ?? item.address?.fullAddress)
    }
}
