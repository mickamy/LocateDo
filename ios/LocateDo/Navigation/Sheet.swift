import CoreLocation
import Foundation

enum Sheet: Identifiable {
    case addTodo(place: Place?, entry: ScreenEntry)
    case editTodo(Todo)
    case addPlace(AddPlace)
    case editPlace(Place)
    case pickLocation(PickLocation)
    case settings(ScreenEntry)
    case sharing
    case paywall(PaywallTrigger)
    case invite(PendingInvite)
    case account
    case reminderSetup(ReminderSetupRequest)
    case promotions

    var id: String {
        switch self {
        case .addTodo: "addTodo"
        case .editTodo(let todo): "editTodo-\(todo.id)"
        case .addPlace: "addPlace"
        case .editPlace(let place): "editPlace-\(place.id)"
        case .pickLocation: "pickLocation"
        case .settings: "settings"
        case .sharing: "sharing"
        case .paywall(let trigger): "paywall-\(trigger.id)"
        case .invite(let invite): "invite-\(invite.id)"
        case .account: "account"
        case .reminderSetup(let request): "reminderSetup-\(request.id)"
        case .promotions: "promotions"
        }
    }
}

// Adding a place from Home goes on to its to-dos; from the to-do screen it stops at the category and hands the place
// back, since the to-do being written is the one for it.
struct AddPlace {
    let entry: ScreenEntry
    var forTodo: ((Place) -> Void)?
    var pickExisting: ((Place) -> Void)?
}

struct PickLocation {
    let initialCoordinate: CLLocationCoordinate2D?
    let onPick: (PlacePick) -> Void
}

// Without a token, the link is pasted on the invite screen.
struct PendingInvite: Identifiable {
    var token: String?

    var id: String {
        token ?? "pasted"
    }
}

struct ReminderSetupRequest: Identifiable {
    let id = UUID()
    let shownCount: Int
    let missing: [ReminderSetup.Need]
    let placeName: String
}
