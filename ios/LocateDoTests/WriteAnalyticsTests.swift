import Foundation
import SwiftData
import Testing

@testable import LocateDo

struct WriteAnalyticsTests {
    @Test func addingAPlaceLogsItsShapeAndWhereItWasPicked() throws {
        let fixture = try Fixture()
        let shopping = try fixture.builtin(.shopping)

        fixture.writes.add(
            Place(name: "Grocery", latitude: 35.0, longitude: 139.0, radiusMeters: 150, category: shopping),
            source: .search
        )

        let values = try fixture.recorder.values(of: .placeAdded)
        #expect(values["place_count"] as? Int == 1)
        #expect(values["category"] as? String == "shopping")
        #expect(values["radius_m"] as? Int == 150)
        #expect(values["todo_count"] as? Int == 0)
        #expect(values["suggested_category"] as? String == "none")
        #expect(values["source"] as? String == "search")
        #expect(values["days_since_install"] as? Int == 4)
        #expect(fixture.recorder.userProperties[.placeCount] == "1")
    }

    @Test func aPlaceWithoutACategoryOrPickSourceSaysSo() throws {
        let fixture = try Fixture()

        fixture.writes.add(Place(name: "Somewhere", latitude: 35.0, longitude: 139.0))

        let values = try fixture.recorder.values(of: .placeAdded)
        #expect(values["category"] as? String == "none")
        #expect(values["source"] == nil)
    }

    @Test func aPlaceOverTheFreeLimitLogsTheLimitInsteadOfAnAdd() throws {
        let fixture = try Fixture()
        for index in 0..<FreeLimit.maxPlaces {
            fixture.writes.add(Place(name: "Place \(index)", latitude: 35.0, longitude: 139.0))
        }

        fixture.writes.add(Place(name: "One too many", latitude: 35.0, longitude: 139.0))

        #expect(fixture.recorder.names.filter { $0 == .placeAdded }.count == FreeLimit.maxPlaces)
        let values = try fixture.recorder.values(of: .limitReached)
        #expect(values["kind"] as? String == "place")
        #expect(values["days_since_install"] as? Int == 4)
    }

    @Test func deletingAPlaceLogsHowOldItWasAndWhatWasLeftOpen() throws {
        let fixture = try Fixture()
        let createdAt = Date(timeIntervalSince1970: 1_800_000_000)
        let place = Place(name: "Grocery", latitude: 35.0, longitude: 139.0, now: createdAt)
        fixture.writes.add(place)
        fixture.writes.add(Todo(title: "Milk", place: place))

        fixture.writes.delete(place, now: createdAt.addingTimeInterval(3.5 * 86_400))

        let values = try fixture.recorder.values(of: .placeDeleted)
        #expect(values["place_count"] as? Int == 0)
        #expect(values["age_days"] as? Int == 3)
        #expect(values["open_todos"] as? Int == 1)
        #expect(fixture.recorder.userProperties[.placeCount] == "0")
        #expect(fixture.recorder.userProperties[.openTodoCount] == "0")
    }

    @Test func addingATodoLogsTheOpenCounts() throws {
        let fixture = try Fixture()
        let grocery = Place(name: "Grocery", latitude: 35.0, longitude: 139.0)
        let pharmacy = Place(name: "Pharmacy", latitude: 35.1, longitude: 139.1)
        fixture.writes.add(grocery)
        fixture.writes.add(pharmacy)
        fixture.writes.add(Todo(title: "Stamps", place: pharmacy))
        let todo = Todo(title: "Milk", place: grocery)
        todo.assigneeID = .v7()

        fixture.writes.add(todo)

        let values = try fixture.recorder.values(of: .todoAdded)
        #expect(values["open_todo_count"] as? Int == 2)
        #expect(values["place_open_todos"] as? Int == 1)
        #expect(values["assigned"] as? Int == 1)
        #expect(values["via"] as? String == "todo_editor")
        #expect(fixture.recorder.userProperties[.openTodoCount] == "2")
    }

    @Test func todosTypedWithANewPlaceFollowIt() throws {
        let fixture = try Fixture()
        let place = Place(name: "Grocery", latitude: 35.0, longitude: 139.0)

        fixture.writes.add(place, todoTitles: ["Milk", "Eggs"])

        #expect(place.todos.map(\.title).sorted() == ["Eggs", "Milk"])
        #expect(try fixture.recorder.values(of: .placeAdded)["todo_count"] as? Int == 2)
        let added = fixture.recorder.events.filter { $0.name == .todoAdded }
        #expect(added.count == 2)
        #expect(added.allSatisfy { $0.values["via"] as? String == "place_editor" })
        #expect(fixture.recorder.names.first == .placeAdded)
    }

    @Test func aGuessedCategoryIsLoggedBesideTheChosenOne() throws {
        let fixture = try Fixture()
        let life = try fixture.builtin(.life)

        fixture.writes.add(
            Place(name: "Post office", latitude: 35.0, longitude: 139.0, category: life),
            suggestedCategory: .shopping
        )

        let values = try fixture.recorder.values(of: .placeAdded)
        #expect(values["suggested_category"] as? String == "shopping")
        #expect(values["category"] as? String == "life")
    }

    @Test func todosTypedWithANewPlaceStopAtTheFreeLimit() throws {
        let fixture = try Fixture()
        let other = Place(name: "Pharmacy", latitude: 35.1, longitude: 139.1)
        fixture.writes.add(other)
        for index in 0..<(FreeLimit.maxOpenTodos - 1) {
            fixture.writes.add(Todo(title: "Todo \(index)", place: other))
        }
        #expect(fixture.writes.remaining(.openTodos) == 1)
        let place = Place(name: "Grocery", latitude: 35.0, longitude: 139.0)

        fixture.writes.add(place, todoTitles: ["Milk", "Eggs"])

        #expect(place.todos.map(\.title) == ["Milk"])
        #expect(try fixture.recorder.values(of: .placeAdded)["todo_count"] as? Int == 1)
        #expect(fixture.writes.remaining(.openTodos) == 0)
        #expect(!fixture.recorder.names.contains(.limitReached))
    }

    @Test func proHasNoRemainingCount() throws {
        let fixture = try Fixture()
        fixture.writes.isPro = { true }

        #expect(fixture.writes.remaining(.openTodos) == nil)
        #expect(fixture.writes.remaining(.places) == nil)
    }

    @Test func reopeningOverTheFreeLimitLogsTheLimit() throws {
        let fixture = try Fixture()
        let place = Place(name: "Grocery", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let done = Todo(title: "Done", place: place)
        fixture.writes.add(done)
        fixture.writes.toggleCompletion(done)
        for index in 0..<FreeLimit.maxOpenTodos {
            fixture.writes.add(Todo(title: "Todo \(index)", place: place))
        }

        fixture.writes.toggleCompletion(done)

        let values = try fixture.recorder.values(of: .limitReached)
        #expect(values["kind"] as? String == "todo")
    }

    @Test func completingAndDeletingTodosKeepTheOpenCountCurrent() throws {
        let fixture = try Fixture()
        let place = Place(name: "Grocery", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let milk = Todo(title: "Milk", place: place)
        let eggs = Todo(title: "Eggs", place: place)
        fixture.writes.add(milk)
        fixture.writes.add(eggs)

        fixture.writes.toggleCompletion(milk)
        #expect(fixture.recorder.userProperties[.openTodoCount] == "1")

        fixture.writes.delete([eggs], via: .swipe)
        #expect(fixture.recorder.userProperties[.openTodoCount] == "0")
    }

    @Test func deletingAndUndoingSayWhereAndHowMany() throws {
        let fixture = try Fixture()
        let place = Place(name: "Grocery", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let milk = Todo(title: "Milk", place: place)
        let eggs = Todo(title: "Eggs", place: place)
        fixture.writes.add(milk)
        fixture.writes.add(eggs)

        let deleted = fixture.writes.delete([milk, eggs], via: .completedBulk)
        fixture.writes.restore(deleted)

        let deletion = try fixture.recorder.values(of: .todoDeleted)
        #expect(deletion["via"] as? String == "completed_bulk")
        #expect(deletion["count"] as? Int == 2)
        #expect(try fixture.recorder.values(of: .todoDeleteUndone)["count"] as? Int == 2)
        #expect(fixture.recorder.userProperties[.openTodoCount] == "2")
    }

    @Test func completingATodoInTheAppSaysSo() throws {
        let fixture = try Fixture()
        let createdAt = Date(timeIntervalSince1970: 1_800_000_000)
        let place = Place(name: "Grocery", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let milk = Todo(title: "Milk", place: place, now: createdAt)
        fixture.writes.add(milk)
        fixture.writes.add(Todo(title: "Eggs", place: place))

        fixture.writes.toggleCompletion(milk, now: createdAt.addingTimeInterval(26 * 3_600))

        let values = try fixture.recorder.values(of: .todoCompleted)
        #expect(values["via"] as? String == "app")
        #expect(values["age_hours"] as? Int == 26)
        #expect(values["open_todo_count"] as? Int == 1)
    }

    @Test func completingRightAfterOpeningTheArrivalNotificationCountsForIt() throws {
        let fixture = try Fixture()
        let openedAt = Date(timeIntervalSince1970: 1_800_000_000)
        let place = Place(name: "Grocery", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let milk = Todo(title: "Milk", place: place)
        fixture.writes.add(milk)

        fixture.writes.arrivalOpened(placeID: place.id, at: openedAt)
        fixture.writes.toggleCompletion(milk, now: openedAt.addingTimeInterval(5 * 60))

        #expect(try fixture.recorder.values(of: .todoCompleted)["via"] as? String == "notification")
    }

    @Test func checkingOffFromTheNotificationSaysSo() throws {
        let fixture = try Fixture()
        let place = Place(name: "Grocery", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let milk = Todo(title: "Milk", place: place)
        let eggs = Todo(title: "Eggs", place: place)
        fixture.writes.add(milk)
        fixture.writes.add(eggs)
        fixture.writes.arrivalOpened(placeID: place.id)

        fixture.writes.checkOff([milk.id, eggs.id])

        let completions = fixture.recorder.events.filter { $0.name == .todoCompleted }
        #expect(completions.map { $0.values["via"] as? String } == ["action", "action"])
        #expect(fixture.recorder.userProperties[.openTodoCount] == "0")
    }

    @Test func checkingOffFromTheWatchSaysWhere() throws {
        let fixture = try Fixture()
        let place = Place(name: "Grocery", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let milk = Todo(title: "Milk", place: place)
        let eggs = Todo(title: "Eggs", place: place)
        fixture.writes.add(milk)
        fixture.writes.add(eggs)

        fixture.writes.checkOff([milk.id], via: CompletionVia(.app))
        fixture.writes.checkOff([eggs.id], via: CompletionVia(.notification))

        let completions = fixture.recorder.events.filter { $0.name == .todoCompleted }
        #expect(completions.map { $0.values["via"] as? String } == ["watch", "watch_action"])
    }

    @Test func reopeningATodoIsNotACompletion() throws {
        let fixture = try Fixture()
        let place = Place(name: "Grocery", latitude: 35.0, longitude: 139.0)
        fixture.writes.add(place)
        let milk = Todo(title: "Milk", place: place)
        fixture.writes.add(milk)
        fixture.writes.toggleCompletion(milk)

        fixture.writes.toggleCompletion(milk)

        #expect(fixture.recorder.names.filter { $0 == .todoCompleted }.count == 1)
    }

    private struct Fixture {
        let container: ModelContainer
        let context: ModelContext
        let writes: LocalWrites
        let recorder = RecordingAnalytics()

        init() throws {
            container = try AppModelContainer.make(inMemory: true)
            context = container.mainContext
            writes = LocalWrites(context: context) { false }
            writes.analytics = recorder
            writes.daysSinceInstall = { 4 }
        }

        func builtin(_ kind: BuiltinCategory) throws -> PlaceCategory {
            let categories = try context.fetch(FetchDescriptor<PlaceCategory>())
            return try #require(categories.first { $0.builtin == kind })
        }
    }
}

private final class RecordingAnalytics: AnalyticsSink {
    private(set) var events: [(name: AnalyticsEvent, values: [String: Any])] = []
    private(set) var userProperties: [AnalyticsUserProperty: String] = [:]

    var names: [AnalyticsEvent] {
        events.map(\.name)
    }

    func values(of name: AnalyticsEvent) throws -> [String: Any] {
        try #require(events.last { $0.name == name }).values
    }

    func log(_ event: AnalyticsEvent, parameters: AnalyticsParameters) {
        events.append((event, Analytics.firebaseParameters(parameters) ?? [:]))
    }

    func setUserProperty(_ value: String?, for property: AnalyticsUserProperty) {
        userProperties[property] = value
    }
}

struct ArrivalOpenTests {
    private let openedAt = Date(timeIntervalSince1970: 1_800_000_000)
    private let placeID = UUID.v7()

    @Test func countsCompletionsAtTheNotifiedPlaceWithinTheWindow() {
        let open = ArrivalOpen(placeID: placeID, openedAt: openedAt)

        #expect(open.via(completingAt: placeID, now: openedAt.addingTimeInterval(ArrivalOpen.window)) == .notification)
    }

    @Test func laterCompletionsAreFromTheApp() {
        let open = ArrivalOpen(placeID: placeID, openedAt: openedAt)

        #expect(open.via(completingAt: placeID, now: openedAt.addingTimeInterval(ArrivalOpen.window + 1)) == .app)
    }

    @Test func completionsAtAnotherPlaceAreFromTheApp() {
        let open = ArrivalOpen(placeID: placeID, openedAt: openedAt)

        #expect(open.via(completingAt: .v7(), now: openedAt.addingTimeInterval(60)) == .app)
        #expect(open.via(completingAt: nil, now: openedAt.addingTimeInterval(60)) == .app)
    }

    @Test func aClockSetBackDoesNotCount() {
        let open = ArrivalOpen(placeID: placeID, openedAt: openedAt)

        #expect(open.via(completingAt: placeID, now: openedAt.addingTimeInterval(-60)) == .app)
    }
}
