import CoreLocation
import Testing

@testable import LocateDo

struct PlaceDuplicateTests {
    private let store = Place(name: "Store", latitude: 35.6800, longitude: 139.7000)

    @Test func aPickOnTheSameStoreFindsIt() {
        let picked = CLLocationCoordinate2D(latitude: 35.68003, longitude: 139.70002)

        #expect(PlaceDuplicate.nearest(to: picked, among: [store])?.name == "Store")
    }

    @Test func aPickAcrossTheStreetIsANewPlace() {
        let picked = CLLocationCoordinate2D(latitude: 35.6806, longitude: 139.7000)

        #expect(PlaceDuplicate.nearest(to: picked, among: [store]) == nil)
    }

    @Test func theClosestOfSeveralWins() {
        let next = Place(name: "Next door", latitude: 35.68025, longitude: 139.7000)
        let picked = CLLocationCoordinate2D(latitude: 35.68022, longitude: 139.7000)

        #expect(PlaceDuplicate.nearest(to: picked, among: [store, next])?.name == "Next door")
    }
}
