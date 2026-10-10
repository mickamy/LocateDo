import Foundation
import Observation

enum AppTab: Hashable {
    case home
    case map
    case todos
    case settings
}

@Observable
final class AppRouter {
    var selectedTab: AppTab = .home
    var pendingPlaceID: UUID?
    var isAddPlaceRequested = false
    var pendingInvite: PendingInvite?
    var pendingPaywall: PaywallTrigger?
    // Set when Home's map preview switches to the Map tab, so the Map tab's screen view can say where it came from.
    @ObservationIgnored private var isMapFromHomePreview = false

    func open(placeID: UUID) {
        selectedTab = .home
        pendingPlaceID = placeID
    }

    func openMapFromHomePreview() {
        isMapFromHomePreview = true
        selectedTab = .map
    }

    // Read once per Map tab appearance.
    func takeMapSource() -> String {
        defer {
            isMapFromHomePreview = false
        }
        if isMapFromHomePreview {
            return "home_preview"
        }
        return "tab"
    }

    func requestAddPlace() {
        selectedTab = .home
        isAddPlaceRequested = true
    }
}

struct PendingInvite: Identifiable {
    let token: String

    var id: String {
        token
    }
}
