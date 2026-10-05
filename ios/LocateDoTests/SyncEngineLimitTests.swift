import Connect
import Foundation
import SwiftData
import Testing

@testable import LocateDo

struct SyncEngineLimitTests {
    @Test func aWriteOverTheFreeLimitIsDroppedAndUndoneWithoutPro() async throws {
        let fixture = try SyncEngineFixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.context.insert(place)
        fixture.context.insert(Todo(title: "Milk", place: place))
        let other = Place(name: "Office", latitude: 35.1, longitude: 139.1)
        fixture.context.insert(other)
        try fixture.enqueue([.put(place), .put(other)])
        fixture.services.fail(with: [.failedPrecondition])
        var limits: [FreeLimit] = []
        fixture.engine.onLimitRejected = { limits.append($0) }

        await fixture.engine.drain()

        #expect(fixture.services.sent == ["putPlace", "putPlace"])
        #expect(try fixture.context.fetch(FetchDescriptor<Place>()).map(\.name) == ["Office"])
        #expect(try fixture.context.fetchCount(FetchDescriptor<Todo>()) == 0)
        #expect(try fixture.queueCount() == 0)
        #expect(limits == [.places])
        #expect(fixture.placeChanges.value == 1)
    }

    @Test func aRejectedReopenIsCompletedAgain() async throws {
        let fixture = try SyncEngineFixture()
        let todo = Todo(title: "Milk", place: Place(name: "Store", latitude: 35.0, longitude: 139.0))
        fixture.context.insert(todo)
        try fixture.enqueue([.completion(of: todo)])
        fixture.services.fail(with: [.failedPrecondition])

        await fixture.engine.drain()

        #expect(todo.isCompleted)
        #expect(try fixture.queueCount() == 0)
    }

    @Test func aWriteOverTheFreeLimitWaitsForTheWebhookWithPro() async throws {
        let fixture = try SyncEngineFixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.context.insert(place)
        try fixture.enqueue([.put(place)])
        fixture.services.fail(with: [.failedPrecondition])
        fixture.engine.isPro = { true }

        await fixture.engine.drain()

        #expect(try fixture.queueCount() == 1)
        #expect(try fixture.context.fetchCount(FetchDescriptor<Place>()) == 1)
    }
}
