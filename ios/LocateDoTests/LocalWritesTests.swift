import Foundation
import SwiftData
import SwiftProtobuf
import Testing

@testable import LocateDo

struct LocalWritesTests {
    @Test func signedOutWritesAreSavedWithoutQueueing() throws {
        let fixture = try Fixture(signedIn: false)
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)

        fixture.writes.add(place)
        fixture.writes.add(Todo(title: "Milk", place: place))

        #expect(try fixture.context.fetchCount(FetchDescriptor<Place>()) == 1)
        #expect(try fixture.context.fetchCount(FetchDescriptor<Todo>()) == 1)
        #expect(try fixture.queue() == [])
        #expect(fixture.probe.queued == 0)
    }

    @Test func addingAndUpdatingAPlaceQueuesPuts() throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)

        fixture.writes.add(place)
        place.name = "Supermarket"
        fixture.writes.update(place, now: Date(timeIntervalSince1970: 1_000))

        #expect(place.updatedAt == Date(timeIntervalSince1970: 1_000))
        let queue = try fixture.queue()
        #expect(queue.map(\.kind) == [.putPlace, .putPlace])
        guard case .putPlace(let input) = queue[1] else {
            Issue.record("Expected putPlace")
            return
        }
        #expect(input.name == "Supermarket")
        #expect(fixture.probe.queued == 2)
    }

    @Test func deletingAPlaceQueuesOnlyThePlaceDelete() throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        fixture.writes.add(Todo(title: "Milk", place: place))

        fixture.writes.delete(place)

        #expect(try fixture.context.fetchCount(FetchDescriptor<Todo>()) == 0)
        #expect(try fixture.queue().map(\.kind) == [.putPlace, .putTodo, .deletePlace])
    }

    @Test func addingATodoAttachesItToThePlace() throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let todo = Todo(title: "Milk", place: place)

        fixture.writes.add(todo)

        #expect(place.todos.map(\.id) == [todo.id])
        #expect(try fixture.queue().last == .put(todo))
    }

    @Test func togglingCompletionQueuesTheCompletionEachTime() throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let todo = Todo(title: "Milk", place: place)
        fixture.writes.add(todo)
        let completedAt = Date(timeIntervalSince1970: 1_000)

        fixture.writes.toggleCompletion(todo, now: completedAt)
        #expect(todo.completedAt == completedAt)
        fixture.writes.toggleCompletion(todo)
        #expect(todo.completedAt == nil)

        let completions = try fixture.queue().compactMap { write -> Locatedo_Todo_V1_SetTodoCompletionRequest? in
            guard case .setTodoCompletion(let request) = write else {
                return nil
            }
            return request
        }
        #expect(completions.map(\.hasCompletedAt) == [true, false])
        #expect(completions[0].completedAt.date == completedAt)
    }

    @Test func changingTheAssigneeQueuesAPut() throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let todo = Todo(title: "Milk", place: place)
        fixture.writes.add(todo)
        let assignee = UUID()

        fixture.writes.setAssignee(assignee, of: todo)

        #expect(todo.assigneeID == assignee)
        guard case .putTodo(let input) = try fixture.queue().last else {
            Issue.record("Expected putTodo")
            return
        }
        #expect(input.assigneeID == ProtoInput.id(assignee))
    }

    @Test func theFourthPlaceHitsTheFreeLimit() throws {
        let fixture = try Fixture()
        for index in 0..<FreeLimit.maxPlaces {
            #expect(fixture.writes.add(Place(name: "Place \(index)", latitude: 35.0, longitude: 139.0)) == nil)
        }

        let limit = fixture.writes.add(Place(name: "One too many", latitude: 35.0, longitude: 139.0))

        #expect(limit == .places)
        #expect(try fixture.context.fetchCount(FetchDescriptor<Place>()) == FreeLimit.maxPlaces)
    }

    @Test func proHasNoPlaceLimit() throws {
        let fixture = try Fixture()
        fixture.probe.isPro = true
        for index in 0...FreeLimit.maxPlaces {
            #expect(fixture.writes.add(Place(name: "Place \(index)", latitude: 35.0, longitude: 139.0)) == nil)
        }
        #expect(try fixture.context.fetchCount(FetchDescriptor<Place>()) == FreeLimit.maxPlaces + 1)
    }

    @Test func openTodosAndReopeningHitTheFreeLimit() throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let done = Todo(title: "Done", place: place)
        fixture.writes.add(done)
        fixture.writes.toggleCompletion(done)
        for index in 0..<FreeLimit.maxOpenTodos {
            #expect(fixture.writes.add(Todo(title: "Todo \(index)", place: place)) == nil)
        }

        #expect(fixture.writes.add(Todo(title: "One too many", place: place)) == .openTodos)
        #expect(fixture.writes.toggleCompletion(done) == .openTodos)
        #expect(done.isCompleted)
    }

    @Test func deletingSeveralTodosQueuesADeleteForEach() throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let milk = Todo(title: "Milk", place: place)
        let bread = Todo(title: "Bread", place: place)
        fixture.writes.add(milk)
        fixture.writes.add(bread)

        fixture.writes.delete([milk, bread])

        #expect(try fixture.context.fetchCount(FetchDescriptor<Todo>()) == 0)
        let queue = try fixture.queue()
        #expect(queue.suffix(2) == [.delete(milk), .delete(bread)])
        let sequences = try fixture.context
            .fetch(FetchDescriptor<PendingWrite>(sortBy: [SortDescriptor(\.sequence)]))
            .map(\.sequence)
        #expect(sequences == Array(1...Int64(sequences.count)))
    }

    private struct Fixture {
        let container: ModelContainer
        let context: ModelContext
        let probe = Probe()
        let writes: LocalWrites

        init(signedIn: Bool = true) throws {
            container = try AppModelContainer.make(inMemory: true)
            context = container.mainContext
            let probe = probe
            probe.signedIn = signedIn
            writes = LocalWrites(context: context) {
                probe.signedIn
            } onQueued: {
                probe.queued += 1
            }
            writes.isPro = { probe.isPro }
        }

        func queue() throws -> [Write] {
            try context
                .fetch(FetchDescriptor<PendingWrite>(sortBy: [SortDescriptor(\.sequence)]))
                .map { try $0.decoded() }
        }
    }

    private final class Probe {
        var signedIn = true
        var queued = 0
        var isPro = false
    }
}
