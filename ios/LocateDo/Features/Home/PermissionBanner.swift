import CoreLocation
import Foundation
import UserNotifications

nonisolated enum PermissionBanner: String {
    case locationAlways = "location_always"
    case locationDenied = "location_denied"
    case notifications

    init?(location: CLAuthorizationStatus, notifications: UNAuthorizationStatus) {
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
        guard notifications == .denied else {
            return nil
        }
        self = .notifications
    }

    var message: LocalizedStringResource {
        switch self {
        case .locationAlways: .homePermissionBannerLocation
        case .locationDenied: .homePermissionBannerLocationDenied
        case .notifications: .homePermissionBannerNotifications
        }
    }
}
