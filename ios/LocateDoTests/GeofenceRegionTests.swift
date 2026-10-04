import CoreLocation
import Foundation
import Testing

@testable import LocateDo

struct GeofenceRegionTests {
    @Test func circularConditionRoundTripsThroughCLMonitor() async {
        let place = Place(name: "Round trip", latitude: 35.6812, longitude: 139.7671, radiusMeters: 150)
        let region = GeofenceRegion(place: place)
        let monitor = await CLMonitor("GeofenceRegionTests")
        await monitor.remove(region.identifier)

        let condition = CLMonitor.CircularGeographicCondition(center: region.center, radius: region.radiusMeters)
        await monitor.add(condition, identifier: region.identifier, assuming: .unsatisfied)
        let record = await monitor.record(for: region.identifier)
        await monitor.remove(region.identifier)

        guard let record,
              let restored = GeofenceRegion(identifier: region.identifier, condition: record.condition) else {
            Issue.record("the stored condition could not be read back as a GeofenceRegion")
            return
        }
        #expect(restored == region)
        #expect(GeofencePlan.changes(from: [region.identifier: restored], to: [region]).add.isEmpty)
    }
}
