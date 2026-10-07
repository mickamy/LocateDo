import Foundation
import Testing

@testable import LocateDo

struct NotificationPolicyTests {
    private let now = Date(timeIntervalSince1970: 100_000)

    @Test func staysQuietWithoutOpenTodos() {
        #expect(suppression(open: 0, notifiable: 0) == .noOpenTodos)
    }

    @Test func staysQuietWhenEveryOpenTodoIsSomeoneElses() {
        #expect(suppression(open: 2, notifiable: 0) == .assignedToOthers)
    }

    @Test func notifiesTheFirstTime() {
        #expect(suppression(open: 1, notifiable: 1) == nil)
    }

    @Test func respectsTheCooldown() {
        let tenMinutesAgo = now.addingTimeInterval(-10 * 60)
        #expect(suppression(open: 2, notifiable: 2, lastNotifiedAt: tenMinutesAgo) == .recentlyNotified)

        let thirtyOneMinutesAgo = now.addingTimeInterval(-31 * 60)
        #expect(suppression(open: 2, notifiable: 2, lastNotifiedAt: thirtyOneMinutesAgo) == nil)
    }

    @Test func staysQuietWhenNotificationsAreOff() {
        #expect(suppression(open: 1, notifiable: 1, notificationsAllowed: false) == .notificationsOff)
    }

    @Test func reasonsUseTheirReportedNames() {
        #expect(NotificationPolicy.Suppression.noOpenTodos.rawValue == "no_open_todos")
        #expect(NotificationPolicy.Suppression.assignedToOthers.rawValue == "assigned_to_others")
        #expect(NotificationPolicy.Suppression.recentlyNotified.rawValue == "recently_notified")
        #expect(NotificationPolicy.Suppression.notificationsOff.rawValue == "notifications_off")
    }

    private func suppression(
        open: Int,
        notifiable: Int,
        lastNotifiedAt: Date? = nil,
        notificationsAllowed: Bool = true
    ) -> NotificationPolicy.Suppression? {
        NotificationPolicy.suppression(
            openTodoCount: open,
            notifiableTodoCount: notifiable,
            lastNotifiedAt: lastNotifiedAt,
            notificationsAllowed: notificationsAllowed,
            now: now
        )
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
