import CoreLocation
import Foundation
import Testing
import UserNotifications

@testable import LocateDo

struct ReminderSetupTests {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    @Test(arguments: [
        (CLAuthorizationStatus.authorizedWhenInUse, true, UNAuthorizationStatus.denied,
         [ReminderSetup.Need.notifications, .locationAlways]),
        (.authorizedAlways, true, .notDetermined, [.notifications]),
        (.authorizedWhenInUse, true, .authorized, [.locationAlways]),
        (.authorizedAlways, false, .authorized, [.preciseLocation]),
        (.authorizedWhenInUse, false, .denied, [.notifications, .locationAlways, .preciseLocation]),
        (.authorizedAlways, true, .authorized, [])
    ])
    func namesWhatIsMissing(location: CLAuthorizationStatus, precise: Bool, notifications: UNAuthorizationStatus,
                            expected: [ReminderSetup.Need]) {
        let permissions = ReminderSetup.Permissions(location: location, preciseLocation: precise,
                                                    notifications: notifications)
        #expect(ReminderSetup.missing(permissions) == expected)
    }

    @Test func approximateLocationCountsOnlyOnceLocationIsAllowed() {
        #expect(!ReminderSetup.needsPrecise(location: .denied, precise: false))
        #expect(!ReminderSetup.needsPrecise(location: .notDetermined, precise: false))
        #expect(ReminderSetup.needsPrecise(location: .authorizedWhenInUse, precise: false))
    }

    @Test func analyticsNameEveryMissingStepInOrder() {
        #expect(ReminderSetup.analyticsValue([.notifications, .locationAlways, .preciseLocation])
                == "notifications,location_always,precise_location")
        #expect(ReminderSetup.analyticsValue([.preciseLocation]) == "precise_location")
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

    @Test func asksWhenOnlyPreciseLocationIsMissing() {
        #expect(isDue(location: .authorizedAlways, precise: false, notifications: .authorized, shownAt: nil))
    }

    @Test func leavesLocationTurnedOffToTheHomeBanner() {
        #expect(!isDue(location: .denied, notifications: .authorized, shownAt: nil))
        #expect(isDue(location: .denied, notifications: .denied, shownAt: nil))
    }

    private func isDue(
        location: CLAuthorizationStatus = .authorizedWhenInUse,
        precise: Bool = true,
        notifications: UNAuthorizationStatus = .authorized,
        shownAt: Date?,
        never: Bool = false
    ) -> Bool {
        let permissions = ReminderSetup.Permissions(location: location, preciseLocation: precise,
                                                    notifications: notifications)
        return ReminderSetup.isDue(permissions, shownAt: shownAt, never: never, now: now)
    }
}
