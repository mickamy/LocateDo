import Foundation
import Observation

// Requests that come from outside the screens: notifications, links, and sync.
@Observable
final class AppRouter {
    var pendingPlaceID: UUID?
    var isAllTodosRequested = false
    var pendingInvite: PendingInvite?
    var pendingPaywall: PaywallTrigger?
    var isPromotionsPromptRequested = false
    // Sheets and dialogs open on screens inside Home, which Home cannot present over.
    var presentationsInsideHome = 0

    func open(placeID: UUID) {
        pendingPlaceID = placeID
    }

    func openAllTodos() {
        isAllTodosRequested = true
    }
}

struct PendingInvite: Identifiable {
    let token: String

    var id: String {
        token
    }
}
