import CoreLocation
import Foundation
import SwiftData
import Testing

@testable import LocateDo

struct ModelTests {
    @Test func placeDefaults() {
        let now = Date(timeIntervalSince1970: 1_000)
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0, now: now)
        #expect(place.radiusMeters == 100)
        #expect(place.category == .other)
        #expect(place.createdAt == now)
        #expect(place.updatedAt == now)
        #expect(place.todos.isEmpty)
        #expect(place.coordinate.latitude == 35.0)
        #expect(place.coordinate.longitude == 139.0)
    }

    @Test func savesPlaceWithTodos() throws {
        let context = try makeContext()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        context.insert(place)
        context.insert(Todo(title: "Milk", place: place))
        try context.save()

        let places = try context.fetch(FetchDescriptor<Place>())
        #expect(places.count == 1)
        #expect(places.first?.todos.map(\.title) == ["Milk"])
        #expect(places.first?.openTodos.map(\.title) == ["Milk"])
    }

    @Test func completingTodoRemovesItFromOpenTodos() throws {
        let context = try makeContext()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        context.insert(place)
        let milk = Todo(title: "Milk", place: place, now: Date(timeIntervalSince1970: 1))
        let bread = Todo(title: "Bread", place: place, now: Date(timeIntervalSince1970: 2))
        context.insert(milk)
        context.insert(bread)
        try context.save()

        let completedAt = Date(timeIntervalSince1970: 10)
        milk.complete(at: completedAt)
        #expect(milk.isCompleted)
        #expect(milk.updatedAt == completedAt)
        #expect(place.openTodos.map(\.title) == ["Bread"])

        milk.reopen(at: Date(timeIntervalSince1970: 20))
        #expect(!milk.isCompleted)
        #expect(place.openTodos.map(\.title) == ["Milk", "Bread"])
    }

    @Test func deletingPlaceCascadesToTodos() throws {
        let context = try makeContext()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        context.insert(place)
        context.insert(Todo(title: "Milk", place: place))
        try context.save()

        context.delete(place)
        try context.save()

        #expect(try context.fetch(FetchDescriptor<Place>()).isEmpty)
        #expect(try context.fetch(FetchDescriptor<Todo>()).isEmpty)
    }

    private func makeContext() throws -> ModelContext {
        ModelContext(try AppModelContainer.make(inMemory: true))
    }
}
