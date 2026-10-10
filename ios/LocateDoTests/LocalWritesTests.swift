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

    @Test func checkingOffRecordsTheUserAndReopeningClearsIt() throws {
        let fixture = try Fixture()
        let me = UUID.v7()
        fixture.writes.currentUserID = { me }
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let todo = Todo(title: "Milk", place: place)
        fixture.writes.add(todo)

        fixture.writes.toggleCompletion(todo)
        #expect(todo.completerID == me)
        fixture.writes.toggleCompletion(todo)
        #expect(todo.completerID == nil)
    }

    @Test func checkingOffFromTheNotificationCompletesOnlyWhatIsStillOpen() throws {
        let fixture = try Fixture()
        let me = UUID.v7()
        fixture.writes.currentUserID = { me }
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let milk = Todo(title: "Milk", place: place)
        let eggs = Todo(title: "Eggs", place: place)
        let bread = Todo(title: "Bread", place: place)
        fixture.writes.add(milk)
        fixture.writes.add(eggs)
        fixture.writes.add(bread)
        fixture.writes.toggleCompletion(eggs)
        let eggsCompletedAt = eggs.completedAt
        let before = try fixture.queue().count
        let now = Date(timeIntervalSince1970: 2_000)

        let completed = fixture.writes.checkOff([milk.id, eggs.id, UUID()], now: now)

        #expect(completed == 1)
        #expect(milk.completedAt == now)
        #expect(milk.completerID == me)
        #expect(eggs.completedAt == eggsCompletedAt)
        #expect(bread.completedAt == nil)
        #expect(try fixture.queue().dropFirst(before).map(\.kind) == [.setTodoCompletion])
    }

    @Test func checkingOffNothingOpenWritesNothing() throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let queued = fixture.probe.queued

        #expect(fixture.writes.checkOff([UUID()]) == 0)
        #expect(fixture.probe.queued == queued)
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

    @Test func aDepartureTodoIsSentAsADeparture() throws {
        let fixture = try Fixture()
        let place = Place(name: "Home", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        fixture.writes.add(Todo(title: "Umbrella", place: place, placeEvent: .departure))

        guard case .putTodo(let input) = try fixture.queue().last else {
            Issue.record("Expected putTodo")
            return
        }
        #expect(input.trigger.event == .departure)
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

        fixture.writes.delete([milk, bread], via: .swipe)

        #expect(try fixture.context.fetchCount(FetchDescriptor<Todo>()) == 0)
        let queue = try fixture.queue()
        #expect(queue.suffix(2) == [.delete(milk), .delete(bread)])
        let sequences = try fixture.context
            .fetch(FetchDescriptor<PendingWrite>(sortBy: [SortDescriptor(\.sequence)]))
            .map(\.sequence)
        #expect(sequences == Array(1...Int64(sequences.count)))
    }

    @Test func aNewCategoryGoesLastAndIsQueued() throws {
        let fixture = try Fixture()
        let gym = PlaceCategory(name: "Gym", icon: "dumbbell", color: "teal", sortOrder: 0)

        fixture.writes.add(gym)

        #expect(gym.sortOrder == BuiltinCategory.allCases.count)
        #expect(try fixture.queue() == [.put(gym)])
    }

    @Test func deletingACategoryQueuesTheDeleteAndUncategorizesItsPlaces() throws {
        let fixture = try Fixture()
        let shopping = try #require(try fixture.categories().first)
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0, category: shopping)
        fixture.writes.add(place)
        let write = Write.delete(shopping)

        #expect(fixture.writes.delete(shopping))

        #expect(place.category == nil)
        #expect(try fixture.queue().last == write)
    }

    @Test func theLastCategoryCannotBeDeleted() throws {
        let fixture = try Fixture()
        let categories = try fixture.categories()
        for category in categories.dropLast() {
            fixture.writes.delete(category)
        }
        let last = try #require(categories.last)

        #expect(!fixture.writes.delete(last))
        #expect(try fixture.categories().map(\.id) == [last.id])
    }

    @Test func reorderingSendsOnlyTheCategoriesThatMoved() throws {
        let fixture = try Fixture()
        let categories = try fixture.categories()
        let moved = [categories[1], categories[0], categories[2], categories[3]]

        fixture.writes.reorder(moved)

        #expect(try fixture.categories().map(\.id) == moved.map(\.id))
        #expect(try fixture.queue() == [.put(categories[1]), .put(categories[0])])
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

        func categories() throws -> [PlaceCategory] {
            try context.fetch(FetchDescriptor<PlaceCategory>(sortBy: [SortDescriptor(\.sortOrder)]))
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

extension LocalWritesTests {
    @Test func editingATodoMovesItAndQueuesAPut() throws {
        let fixture = try Fixture()
        let store = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        let pharmacy = Place(name: "Pharmacy", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(store)
        fixture.writes.add(pharmacy)
        let todo = Todo(title: "Milk", place: store)
        fixture.writes.add(todo)
        let assignee = UUID()

        fixture.writes.update(todo, title: "Sunscreen", place: pharmacy, assigneeID: assignee,
                              now: Date(timeIntervalSince1970: 1_000))

        #expect(todo.place?.id == pharmacy.id)
        #expect(store.todos.isEmpty)
        #expect(pharmacy.todos.map(\.id) == [todo.id])
        #expect(todo.updatedAt == Date(timeIntervalSince1970: 1_000))
        guard case .putTodo(let input) = try fixture.queue().last else {
            Issue.record("Expected putTodo")
            return
        }
        #expect(input.title == "Sunscreen")
        #expect(input.placeID == ProtoInput.id(pharmacy.id))
        #expect(input.assigneeID == ProtoInput.id(assignee))
    }

    @Test func undoingADeleteBringsBackTheSameTodo() throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let todo = Todo(title: "Milk", place: place, placeEvent: .departure, now: Date(timeIntervalSince1970: 10))
        fixture.writes.add(todo)
        todo.assigneeID = UUID()
        let id = todo.id
        let assigneeID = todo.assigneeID

        let deleted = fixture.writes.delete([todo], via: .menu)
        fixture.writes.restore(deleted)

        let restored = try #require(try fixture.context.fetch(FetchDescriptor<Todo>()).first)
        #expect(restored.id == id)
        #expect(restored.title == "Milk")
        #expect(restored.place?.id == place.id)
        #expect(restored.assigneeID == assigneeID)
        #expect(restored.placeEvent == .departure)
        #expect(restored.createdAt == Date(timeIntervalSince1970: 10))
        #expect(!restored.isCompleted)
        #expect(try fixture.queue().suffix(2).map(\.kind) == [.deleteTodo, .putTodo])
    }

    @Test func undoingADeleteKeepsItCompleted() throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let todo = Todo(title: "Milk", place: place)
        fixture.writes.add(todo)
        fixture.writes.toggleCompletion(todo, now: Date(timeIntervalSince1970: 20))

        fixture.writes.restore(fixture.writes.delete([todo], via: .swipe))

        let restored = try #require(try fixture.context.fetch(FetchDescriptor<Todo>()).first)
        #expect(restored.completedAt == Date(timeIntervalSince1970: 20))
        #expect(try fixture.queue().suffix(3).map(\.kind) == [.deleteTodo, .putTodo, .setTodoCompletion])
    }

    @Test func undoingADeleteSkipsTodosWhosePlaceIsGone() throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let todo = Todo(title: "Milk", place: place)
        fixture.writes.add(todo)
        let deleted = fixture.writes.delete([todo], via: .swipe)
        fixture.writes.delete(place)
        let queued = fixture.probe.queued

        fixture.writes.restore(deleted)

        #expect(try fixture.context.fetchCount(FetchDescriptor<Todo>()) == 0)
        #expect(fixture.probe.queued == queued)
    }
}
