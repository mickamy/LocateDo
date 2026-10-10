import SwiftUI

struct SheetContent: View {
    @Environment(AppPreferences.self) private var preferences
    @Environment(\.dismiss) private var dismiss
    let sheet: Sheet

    var body: some View {
        switch sheet {
        case .addTodo(let place, let entry):
            TodoEditor(adding: place, entry: entry)
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
        case .settings(let entry):
            SettingsScreen(entry: entry)
        case .sharing:
            SharingScreen()
        case .paywall(let trigger):
            PaywallScreen(trigger: trigger)
        case .invite(let invite):
            NavigationStack {
                AcceptInviteScreen(token: invite.token)
                    .toolbar {
                        doneButton
                    }
            }
        case .account:
            NavigationStack {
                AccountScreen()
                    .toolbar {
                        doneButton
                    }
            }
        case .reminderSetup(let request):
            ReminderSetupScreen(shownCount: request.shownCount, missing: request.missing)
        case .promotions:
            PromotionsConsentScreen()
        }
    }

    private var doneButton: some ToolbarContent {
        ToolbarItem(placement: .confirmationAction) {
            Button(.commonDone) {
                dismiss()
            }
        }
    }
}
