import CoreLocation
import Foundation
import Testing
import UserNotifications

@testable import LocateDo

struct ReminderSetupTests {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    @Test(arguments: [
        (CLAuthorizationStatus.authorizedWhenInUse, UNAuthorizationStatus.denied, ReminderSetup.Missing?.some(.both)),
        (.authorizedAlways, .notDetermined, .some(.notifications)),
        (.authorizedWhenInUse, .authorized, .some(.locationAlways)),
        (.authorizedAlways, .authorized, nil)
    ])
    func namesWhatIsMissing(location: CLAuthorizationStatus, notifications: UNAuthorizationStatus,
                            expected: ReminderSetup.Missing?) {
        #expect(ReminderSetup.missing(location: location, notifications: notifications) == expected)
    }

    @Test func asksTheFirstTime() {
        #expect(isDue(shownAt: nil))
    }

    @Test func waitsAWeekBeforeAskingAgain() {
        #expect(!isDue(shownAt: now.addingTimeInterval(-6 * 24 * 60 * 60)))
        #expect(isDue(shownAt: now.addingTimeInterval(-7 * 24 * 60 * 60)))
    }

    @Test func neverAsksAfterDontShowAgain() {
        #expect(!isDue(shownAt: nil, never: true))
    }

    @Test func doesNotAskWhenNothingIsMissing() {
        #expect(!isDue(location: .authorizedAlways, notifications: .authorized, shownAt: nil))
    }

    @Test func leavesLocationTurnedOffToTheHomeBanner() {
        #expect(!isDue(location: .denied, notifications: .authorized, shownAt: nil))
        #expect(isDue(location: .denied, notifications: .denied, shownAt: nil))
    }

    private func isDue(
        location: CLAuthorizationStatus = .authorizedWhenInUse,
        notifications: UNAuthorizationStatus = .authorized,
        shownAt: Date?,
        never: Bool = false
    ) -> Bool {
        ReminderSetup.isDue(location: location, notifications: notifications, shownAt: shownAt, never: never, now: now)
    }
}
