import Foundation
import Testing

@testable import LocateDo

struct NotificationPolicyTests {
    private let now = Date(timeIntervalSince1970: 100_000)

    @Test func staysQuietWithoutOpenTodos() {
        #expect(!NotificationPolicy.shouldNotify(openTodoCount: 0, lastNotifiedAt: nil, now: now))
    }

    @Test func notifiesTheFirstTime() {
        #expect(NotificationPolicy.shouldNotify(openTodoCount: 1, lastNotifiedAt: nil, now: now))
    }

    @Test func respectsTheCooldown() {
        let tenMinutesAgo = now.addingTimeInterval(-10 * 60)
        #expect(!NotificationPolicy.shouldNotify(openTodoCount: 2, lastNotifiedAt: tenMinutesAgo, now: now))

        let thirtyOneMinutesAgo = now.addingTimeInterval(-31 * 60)
        #expect(NotificationPolicy.shouldNotify(openTodoCount: 2, lastNotifiedAt: thirtyOneMinutesAgo, now: now))
    }

    @Test func bodyListsUpToThreeTitles() {
        let body = NotificationPolicy.body(todoTitles: ["Milk", "Bread"])
        #expect(body.contains("Milk"))
        #expect(body.contains("Bread"))
        #expect(!body.contains("+"))
    }

    @Test func bodyCountsTheRest() {
        let body = NotificationPolicy.body(todoTitles: ["Milk", "Bread", "Eggs", "Butter", "Jam"])
        #expect(body.contains("Eggs"))
        #expect(!body.contains("Butter"))
        #expect(body.contains("2"))
    }

    @Test func notifiesOnlyForTodosAssignedToAnyoneOrToTheUser() {
        let me = UUID()
        let partner = UUID()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        let anyone = Todo(title: "Milk", place: place)
        let mine = Todo(title: "Bread", place: place)
        mine.assigneeID = me
        let theirs = Todo(title: "Eggs", place: place)
        theirs.assigneeID = partner

        let todos = [anyone, mine, theirs]

        #expect(NotificationPolicy.notifiableTodos(todos, for: me).map(\.title) == ["Milk", "Bread"])
        #expect(NotificationPolicy.notifiableTodos(todos, for: partner).map(\.title) == ["Milk", "Eggs"])
        #expect(NotificationPolicy.notifiableTodos(todos, for: nil).map(\.title) == ["Milk", "Bread", "Eggs"])
    }
}
