import Foundation
import SwiftData
import Testing

@testable import LocateDo

struct TodoGroupingTests {
    @Test func groupsByPlaceInSortOrderWithOpenTodosFirst() throws {
        let context = ModelContext(try AppModelContainer.make(inMemory: true))
        let office = Place(name: "Office", latitude: 0, longitude: 0, sortOrder: 1)
        let store = Place(name: "Store", latitude: 0, longitude: 0, sortOrder: 0)
        context.insert(office)
        context.insert(store)
        let milk = Todo(title: "Milk", place: store, now: Date(timeIntervalSince1970: 1))
        let bread = Todo(title: "Bread", place: store, now: Date(timeIntervalSince1970: 2))
        let eggs = Todo(title: "Eggs", place: store, now: Date(timeIntervalSince1970: 3))
        let report = Todo(title: "Report", place: office, now: Date(timeIntervalSince1970: 4))
        for todo in [milk, bread, eggs, report] {
            context.insert(todo)
        }
        milk.complete(at: Date(timeIntervalSince1970: 10))
        try context.save()
        let todos = [report, eggs, bread, milk]

        let all = TodoGrouping.groups(todos, filter: .all)
        #expect(all.map(\.place.name) == ["Store", "Office"])
        #expect(all[0].todos.map(\.title) == ["Bread", "Eggs", "Milk"])
        #expect(all[1].todos.map(\.title) == ["Report"])

        let open = TodoGrouping.groups(todos, filter: .open)
        #expect(open.map(\.place.name) == ["Store", "Office"])
        #expect(open[0].todos.map(\.title) == ["Bread", "Eggs"])

        let done = TodoGrouping.groups(todos, filter: .done)
        #expect(done.map(\.place.name) == ["Store"])
        #expect(done[0].todos.map(\.title) == ["Milk"])
    }

    @Test func returnsNoGroupsWhenNothingMatches() throws {
        let context = ModelContext(try AppModelContainer.make(inMemory: true))
        let store = Place(name: "Store", latitude: 0, longitude: 0)
        context.insert(store)
        let milk = Todo(title: "Milk", place: store)
        context.insert(milk)
        try context.save()

        #expect(TodoGrouping.groups([milk], filter: .done).isEmpty)
        #expect(TodoGrouping.groups([], filter: .all).isEmpty)
    }
}
