import Foundation
import SwiftData
import Testing

@testable import LocateDo

struct PendingWriteTests {
    @Test func roundTripsEveryKind() throws {
        let category = PlaceCategory(name: "Gym", icon: "dumbbell", color: "teal", sortOrder: 4)
        let place = Place(name: "Store", latitude: 35.5, longitude: 139.25, category: category)
        let todo = Todo(title: "Milk", place: place)
        todo.complete(at: Date(timeIntervalSince1970: 1_000))
        let writes: [Write] = [
            .put(place),
            .delete(place),
            try #require(.put(todo)),
            .completion(of: todo),
            .delete(todo),
            .put(category),
            .delete(category)
        ]

        for write in writes {
            let pending = try PendingWrite(write: write, sequence: 1)
            #expect(pending.kind == write.kind)
            #expect(try pending.decoded() == write)
        }
    }

    @Test func completionOfAnOpenTodoClearsTheTimestamp() {
        let todo = Todo(title: "Milk", place: Place(name: "Store", latitude: 35.0, longitude: 139.0))
        guard case .setTodoCompletion(let request) = Write.completion(of: todo) else {
            Issue.record("Expected setTodoCompletion")
            return
        }
        #expect(request.id == todo.id.uuidString.lowercased())
        #expect(request.hasCompletedAt == false)
    }

    @Test func todoWithoutPlaceIsNotWritten() {
        let todo = Todo(title: "Milk", place: Place(name: "Store", latitude: 35.0, longitude: 139.0))
        todo.place = nil
        #expect(Write.put(todo) == nil)
    }

    @Test func enqueueNumbersWritesInOrder() throws {
        let context = try makeContext()
        let first = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        let second = Place(name: "Office", latitude: 35.1, longitude: 139.1)

        try PendingWrite.enqueue(.put(first), in: context)
        try PendingWrite.enqueue(.put(second), in: context)
        try PendingWrite.enqueue(.delete(first), in: context)
        try context.save()

        let queued = try context.fetch(FetchDescriptor<PendingWrite>(sortBy: [SortDescriptor(\.sequence)]))
        #expect(queued.map(\.sequence) == [1, 2, 3])
        #expect(queued.map(\.kind) == [.putPlace, .putPlace, .deletePlace])
        #expect(try PendingWrite.head(in: context)?.decoded() == .put(first))
    }

    @Test func sequenceContinuesAfterTheHeadIsRemoved() throws {
        let context = try makeContext()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        try PendingWrite.enqueue(.put(place), in: context)
        try PendingWrite.enqueue(.put(place), in: context)
        try context.save()

        context.delete(try #require(try PendingWrite.head(in: context)))
        try PendingWrite.enqueue(.delete(place), in: context)
        try context.save()

        let queued = try context.fetch(FetchDescriptor<PendingWrite>(sortBy: [SortDescriptor(\.sequence)]))
        #expect(queued.map(\.sequence) == [2, 3])
    }

    @Test func headIsNilWhenEmpty() throws {
        #expect(try PendingWrite.head(in: makeContext()) == nil)
    }

    private func makeContext() throws -> ModelContext {
        ModelContext(try AppModelContainer.make(inMemory: true))
    }
}
