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
}
