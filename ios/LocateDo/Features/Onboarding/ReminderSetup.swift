import CoreLocation
import Foundation
import UserNotifications

// What arrival reminders still need, and whether to ask for it after a place is saved: at most once a week, until the
// user says not to.
nonisolated enum ReminderSetup {
    enum Missing: String {
        case notifications
        case locationAlways = "location_always"
        case both
    }

    static let interval: TimeInterval = 7 * 24 * 60 * 60

    static func needsNotifications(_ status: UNAuthorizationStatus) -> Bool {
        DailyState.NotificationAuth(status) != .authorized
    }

    static func needsAlways(_ status: CLAuthorizationStatus) -> Bool {
        status != .authorizedAlways
    }

    static func missing(location: CLAuthorizationStatus, notifications: UNAuthorizationStatus) -> Missing? {
        switch (needsNotifications(notifications), needsAlways(location)) {
        case (true, true): .both
        case (true, false): .notifications
        case (false, true): .locationAlways
        case (false, false): nil
        }
    }

    // Location turned off altogether is left to the home banner; this sheet asks for the step up to "Always".
    static func isDue(
        location: CLAuthorizationStatus,
        notifications: UNAuthorizationStatus,
        shownAt: Date?,
        never: Bool,
        now: Date
    ) -> Bool {
        if never {
            return false
        }
        guard needsNotifications(notifications) || location == .authorizedWhenInUse else {
            return false
        }
        guard let shownAt else {
            return true
        }
        return now.timeIntervalSince(shownAt) >= interval
    }
}
