import CoreLocation
import Testing
import UserNotifications

@testable import LocateDo

struct PermissionBannerTests {
    @Test func locationComesBeforeNotifications() {
        #expect(banner(.authorizedWhenInUse, .denied) == .locationAlways)
        #expect(banner(.denied, .denied) == .locationDenied)
        #expect(banner(.restricted, .authorized) == .locationDenied)
    }

    @Test func deniedNotificationsShowOnlyWhenLocationIsFine() {
        #expect(banner(.authorizedAlways, .denied) == .notifications)
    }

    @Test func unaskedNotificationsShowOnceLocationIsAlways() {
        #expect(banner(.authorizedAlways, .notDetermined) == .notifications)
        #expect(banner(.authorizedWhenInUse, .notDetermined) == .locationAlways)
        #expect(banner(.notDetermined, .notDetermined) == nil)
    }

    @Test func approximateLocationComesAfterAlwaysAndBeforeNotifications() {
        #expect(banner(.authorizedAlways, precise: false, .denied) == .preciseLocation)
        #expect(banner(.authorizedWhenInUse, precise: false, .authorized) == .locationAlways)
        #expect(banner(.denied, precise: false, .authorized) == .locationDenied)
    }

    @Test func nothingShowsWhenBothAreFineOrUnasked() {
        #expect(banner(.authorizedAlways, .authorized) == nil)
        #expect(banner(.notDetermined, .notDetermined) == nil)
    }

    @Test func kindsUseTheirReportedNames() {
        #expect(PermissionBanner.locationAlways.rawValue == "location_always")
        #expect(PermissionBanner.locationDenied.rawValue == "location_denied")
        #expect(PermissionBanner.preciseLocation.rawValue == "precise_location")
        #expect(PermissionBanner.notifications.rawValue == "notifications")
    }

    private func banner(
        _ location: CLAuthorizationStatus,
        precise: Bool = true,
        _ notifications: UNAuthorizationStatus
    ) -> PermissionBanner? {
        PermissionBanner(location: location, preciseLocation: precise, notifications: notifications)
    }
}
