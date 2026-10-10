import CoreLocation
import Foundation
import UserNotifications

// What arrival reminders still need, and whether to ask for it after a place is saved: at most once a week, until the
// user says not to.
nonisolated enum ReminderSetup {
    struct Permissions: Equatable {
        let location: CLAuthorizationStatus
        let preciseLocation: Bool
        let notifications: UNAuthorizationStatus
    }

    // In the order the sheet lists them and the analytics name them.
    enum Need: String, CaseIterable {
        case notifications
        case locationAlways = "location_always"
        case preciseLocation = "precise_location"
    }

    static let interval: TimeInterval = 7 * 24 * 60 * 60

    static func needsNotifications(_ status: UNAuthorizationStatus) -> Bool {
        DailyState.NotificationAuth(status) != .authorized
    }

    static func needsAlways(_ status: CLAuthorizationStatus) -> Bool {
        status != .authorizedAlways
    }

    // Only a granted location can be approximate; without one the home banner asks for location first.
    static func needsPrecise(location: CLAuthorizationStatus, precise: Bool) -> Bool {
        (location == .authorizedAlways || location == .authorizedWhenInUse) && !precise
    }

    static func missing(_ permissions: Permissions) -> [Need] {
        Need.allCases.filter { need in
            switch need {
            case .notifications: needsNotifications(permissions.notifications)
            case .locationAlways: needsAlways(permissions.location)
            case .preciseLocation: needsPrecise(location: permissions.location, precise: permissions.preciseLocation)
            }
        }
    }

    static func analyticsValue(_ needs: [Need]) -> String {
        needs.map(\.rawValue).joined(separator: ",")
    }

    // Location turned off altogether is left to the home banner; this sheet asks for the steps up from "While Using".
    static func isDue(_ permissions: Permissions, shownAt: Date?, never: Bool, now: Date) -> Bool {
        if never {
            return false
        }
        let location = permissions.location
        guard needsNotifications(permissions.notifications)
                || location == .authorizedWhenInUse
                || needsPrecise(location: location, precise: permissions.preciseLocation) else {
            return false
        }
        guard let shownAt else {
            return true
        }
        return now.timeIntervalSince(shownAt) >= interval
    }
}
