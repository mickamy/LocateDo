import CoreLocation
import SwiftData
import Testing

@testable import LocateDo

struct NearbyTests {
    private let shibuya = Place(name: "Shibuya", latitude: 35.6580, longitude: 139.7016, sortOrder: 0)
    private let shinjuku = Place(name: "Shinjuku", latitude: 35.6896, longitude: 139.7006, sortOrder: 1)
    private let yokohama = Place(name: "Yokohama", latitude: 35.4437, longitude: 139.6380, sortOrder: 2)

    @Test func sortsByDistanceFromTheCurrentLocation() {
        let nearShinjuku = CLLocation(latitude: 35.6900, longitude: 139.7000)
        let nearby = Nearby.places([yokohama, shibuya, shinjuku], from: nearShinjuku)
        #expect(nearby.map(\.place.name) == ["Shinjuku", "Shibuya", "Yokohama"])
        #expect(nearby[0].distanceMeters.map { $0 < 200 } == true)
    }

    @Test func keepsSortOrderWithoutALocation() {
        let nearby = Nearby.places([shibuya, shinjuku, yokohama], from: nil)
        #expect(nearby.map(\.place.name) == ["Shibuya", "Shinjuku", "Yokohama"])
        #expect(nearby.allSatisfy { $0.distanceMeters == nil })
    }

    @Test func countsOpenTodos() throws {
        let context = ModelContext(try AppModelContainer.make(inMemory: true))
        context.insert(shibuya)
        context.insert(shinjuku)
        context.insert(Todo(title: "Milk", place: shibuya))
        let done = Todo(title: "Bread", place: shibuya)
        context.insert(done)
        done.complete()
        context.insert(Todo(title: "Stamps", place: shinjuku))
        try context.save()

        let nearby = Nearby.places([shibuya, shinjuku], from: nil)
        #expect(Nearby.openTodoCount(nearby) == 2)
    }
}
