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

    func open(placeID: UUID) {
        selectedTab = .home
        pendingPlaceID = placeID
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
