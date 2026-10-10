import CoreLocation
import Foundation
import UserNotifications

nonisolated enum PermissionBanner: String {
    case locationAlways = "location_always"
    case locationDenied = "location_denied"
    case preciseLocation = "precise_location"
    case notifications

    init?(location: CLAuthorizationStatus, preciseLocation: Bool, notifications: UNAuthorizationStatus) {
        switch location {
        case .authorizedWhenInUse:
            self = .locationAlways
            return
        case .denied, .restricted:
            self = .locationDenied
            return
        default:
            break
        }
        if ReminderSetup.needsPrecise(location: location, precise: preciseLocation) {
            self = .preciseLocation
            return
        }
        guard notifications == .denied || (notifications == .notDetermined && location == .authorizedAlways) else {
            return nil
        }
        self = .notifications
    }

    var message: LocalizedStringResource {
        switch self {
        case .locationAlways: .homePermissionBannerIosLocation
        case .locationDenied: .homePermissionBannerLocationDenied
        case .preciseLocation: .homePermissionBannerPreciseLocation
        case .notifications: .homePermissionBannerNotifications
        }
    }
}
