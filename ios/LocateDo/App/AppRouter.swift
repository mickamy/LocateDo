import Foundation
import Observation

@Observable
final class AppRouter {
    var pendingPlaceID: UUID?
    var isAllTodosRequested = false
    var isAddPlaceRequested = false
    var isSettingsPresented = false
    var pendingInvite: PendingInvite?
    var pendingPaywall: PaywallTrigger?
    // Set when a new place is saved from anywhere, so Home can offer the reminder setup once its sheets close.
    @ObservationIgnored var didAddPlace = false

    func open(placeID: UUID) {
        isSettingsPresented = false
        pendingPlaceID = placeID
    }

    func openAllTodos() {
        isSettingsPresented = false
        isAllTodosRequested = true
    }

    func requestAddPlace() {
        isSettingsPresented = false
        isAddPlaceRequested = true
    }
}

struct PendingInvite: Identifiable {
    let token: String

    var id: String {
        token
    }
}
