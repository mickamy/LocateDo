import SwiftUI

struct SheetContent: View {
    @Environment(AppPreferences.self) private var preferences
    let sheet: Sheet

    var body: some View {
        switch sheet {
        case .addTodo(let place):
            TodoEditor(adding: place)
        case .editTodo(let todo):
            TodoEditor(editing: todo)
        case .addPlace(let request):
            AddPlaceFlow(request: request, defaultRadiusMeters: preferences.defaultRadiusMeters)
        case .editPlace(let place):
            EditPlaceForm(place: place)
        case .pickLocation(let request):
            NavigationStack {
                LocationPicker(initialCoordinate: request.initialCoordinate, closesOnPick: true, onPick: request.onPick)
            }
        case .settings:
            SettingsScreen()
        case .sharing:
            SharingScreen()
        case .paywall(let trigger):
            PaywallScreen(trigger: trigger)
        case .invite(let invite):
            NavigationStack {
                AcceptInviteScreen(token: invite.token)
            }
        case .reminderSetup(let request):
            ReminderSetupScreen(shownCount: request.shownCount, missing: request.missing)
        case .promotions:
            PromotionsConsentScreen()
        }
    }
}
