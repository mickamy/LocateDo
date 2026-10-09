import Foundation
import SwiftData
import Testing

@testable import LocateDo

struct WatchSnapshotTests {
    @Test func survivesTheApplicationContext() throws {
        let snapshot = WatchSnapshot(places: [
            .init(id: UUID(), name: "Store", categoryIcon: "cart", categoryColor: "orange", todos: [
                .init(id: UUID(), title: "Milk")
            ]),
            .init(id: UUID(), name: "Office", categoryIcon: nil, categoryColor: nil, todos: [
                .init(id: UUID(), title: "Report")
            ])
        ])

        let read = try #require(WatchSnapshot(applicationContext: snapshot.applicationContext))

        #expect(read == snapshot)
    }

    @Test func aContextWithoutASnapshotHasNone() {
        #expect(WatchSnapshot(applicationContext: [:]) == nil)
        #expect(WatchSnapshot(applicationContext: [WatchSnapshot.contextKey: Data("{}".utf8)]) == nil)
    }

    @Test func tellsARequestFromACheckOff() {
        #expect(WatchSnapshot.isRequest(WatchSnapshot.request))
        #expect(!WatchSnapshot.isRequest(WatchCheckOff(todoIDs: [UUID()], source: .app).message))
        #expect(!WatchSnapshot.isRequest([:]))
    }

    @Test func keepsPlacesInOrderWithTheirNotifiableOpenTodos() throws {
        let context = ModelContext(try AppModelContainer.make(inMemory: true))
        let me = UUID()
        let category = PlaceCategory(builtin: .shopping, sortOrder: 0)
        context.insert(category)
        let store = Place(name: "Store", latitude: 0, longitude: 0, category: category)
        let office = Place(name: "Office", latitude: 0, longitude: 0)
        context.insert(store)
        context.insert(office)
        let milk = Todo(title: "Milk", place: store, now: Date(timeIntervalSince1970: 1))
        let bread = Todo(title: "Bread", place: store, now: Date(timeIntervalSince1970: 2))
        let eggs = Todo(title: "Eggs", place: store, now: Date(timeIntervalSince1970: 3))
        let report = Todo(title: "Report", place: office, now: Date(timeIntervalSince1970: 4))
        for todo in [milk, bread, eggs, report] {
            context.insert(todo)
        }
        bread.complete()
        eggs.assigneeID = UUID()
        report.assigneeID = me
        try context.save()

        let snapshot = WatchSnapshot(places: [office, store], userID: me)

        #expect(snapshot.places.map(\.name) == ["Office", "Store"])
        #expect(snapshot.places[0].todos.map(\.title) == ["Report"])
        #expect(snapshot.places[1].todos.map(\.title) == ["Milk"])
        #expect(snapshot.places[1].categoryIcon == category.icon)
        #expect(snapshot.places[1].categoryColor == category.color)
        #expect(snapshot.places[0].categoryIcon == nil)
    }

    @Test func leavesOutPlacesWithNothingToNotify() throws {
        let context = ModelContext(try AppModelContainer.make(inMemory: true))
        let store = Place(name: "Store", latitude: 0, longitude: 0)
        let empty = Place(name: "Empty", latitude: 0, longitude: 0)
        context.insert(store)
        context.insert(empty)
        let milk = Todo(title: "Milk", place: store)
        context.insert(milk)
        milk.assigneeID = UUID()
        try context.save()

        #expect(WatchSnapshot(places: [store, empty], userID: UUID()).places.isEmpty)
    }

    @Test func removingDropsTheTodosAndPlacesLeftEmpty() {
        let milk = WatchSnapshot.Todo(id: UUID(), title: "Milk")
        let bread = WatchSnapshot.Todo(id: UUID(), title: "Bread")
        let report = WatchSnapshot.Todo(id: UUID(), title: "Report")
        let store = WatchSnapshot.Place(
            id: UUID(), name: "Store", categoryIcon: "cart", categoryColor: "orange", todos: [milk, bread]
        )
        let office = WatchSnapshot.Place(
            id: UUID(), name: "Office", categoryIcon: nil, categoryColor: nil, todos: [report]
        )

        let rest = WatchSnapshot(places: [store, office]).removing([milk.id, report.id])

        #expect(rest.places.map(\.name) == ["Store"])
        #expect(rest.places[0].todos == [bread])
        #expect(rest.places[0].categoryIcon == "cart")
    }
}
