import CoreLocation
import Testing
import UserNotifications

@testable import LocateDo

struct PermissionBannerTests {
    @Test func locationComesBeforeNotifications() {
        #expect(PermissionBanner(location: .authorizedWhenInUse, notifications: .denied) == .locationAlways)
        #expect(PermissionBanner(location: .denied, notifications: .denied) == .locationDenied)
        #expect(PermissionBanner(location: .restricted, notifications: .authorized) == .locationDenied)
    }

    @Test func deniedNotificationsShowOnlyWhenLocationIsFine() {
        #expect(PermissionBanner(location: .authorizedAlways, notifications: .denied) == .notifications)
    }

    @Test func nothingShowsWhenBothAreFineOrUnasked() {
        #expect(PermissionBanner(location: .authorizedAlways, notifications: .authorized) == nil)
        #expect(PermissionBanner(location: .notDetermined, notifications: .notDetermined) == nil)
    }

    @Test func kindsUseTheirReportedNames() {
        #expect(PermissionBanner.locationAlways.rawValue == "location_always")
        #expect(PermissionBanner.locationDenied.rawValue == "location_denied")
        #expect(PermissionBanner.notifications.rawValue == "notifications")
    }
}
