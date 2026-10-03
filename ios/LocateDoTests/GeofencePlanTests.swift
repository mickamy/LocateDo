import CoreLocation
import Foundation
import Testing

@testable import LocateDo

struct GeofencePlanTests {
    @Test func keepsTheNearestTwentyPlaces() {
        let places = (0..<25).map { index in
            Place(name: "Place \(index)", latitude: 35.0 + Double(index) * 0.01, longitude: 139.0, sortOrder: index)
        }
        let regions = GeofencePlan.regions(for: places, near: CLLocation(latitude: 35.0, longitude: 139.0))
        #expect(regions.count == 20)
        #expect(regions.first?.id == places[0].id)
        #expect(regions.last?.id == places[19].id)
    }

    @Test func diffsAgainstTheCurrentRegistrations() {
        let unchanged = Place(name: "Unchanged", latitude: 35.0, longitude: 139.0)
        let resized = Place(name: "Resized", latitude: 36.0, longitude: 139.0, radiusMeters: 100)
        let added = Place(name: "Added", latitude: 37.0, longitude: 139.0)
        let staleID = UUID()

        var current: [String: GeofenceRegion] = [:]
        current[unchanged.id.uuidString] = GeofenceRegion(place: unchanged)
        current[resized.id.uuidString] = GeofenceRegion(place: resized)
        let stale = Place(id: staleID, name: "Stale", latitude: 0, longitude: 0)
        current[staleID.uuidString] = GeofenceRegion(place: stale)
        resized.radiusMeters = 200

        let desired = GeofencePlan.regions(for: [unchanged, resized, added], near: nil)
        let changes = GeofencePlan.changes(from: current, to: desired)

        #expect(changes.add.map(\.id) == [resized.id, added.id])
        #expect(changes.remove == [staleID.uuidString])
    }

    @Test func regionRoundTripsThroughACondition() throws {
        let place = Place(name: "Store", latitude: 35.5, longitude: 139.5, radiusMeters: 150)
        let region = GeofenceRegion(place: place)
        let condition = CLMonitor.CircularGeographicCondition(center: region.center, radius: region.radiusMeters)
        let restored = try #require(GeofenceRegion(identifier: region.identifier, condition: condition))
        #expect(restored == region)
        #expect(GeofenceRegion(identifier: "not-a-uuid", condition: condition) == nil)
    }
}
