import Foundation

// Everything Home presents goes through one sheet, so two can never be asked for at once.
enum HomeSheet: Identifiable {
    case addTodo
    case addPlace
    case settings
    case sharing
    case reminderSetup(ReminderSetupRequest)
    case paywall(PaywallTrigger)
    case invite(PendingInvite)
    case promotions

    var id: String {
        switch self {
        case .addTodo: "addTodo"
        case .addPlace: "addPlace"
        case .settings: "settings"
        case .sharing: "sharing"
        case .reminderSetup(let request): "reminderSetup-\(request.id)"
        case .paywall(let trigger): "paywall-\(trigger.id)"
        case .invite(let invite): "invite-\(invite.id)"
        case .promotions: "promotions"
        }
    }
}

struct ReminderSetupRequest: Identifiable {
    let id = UUID()
    let shownCount: Int
    let missing: [ReminderSetup.Need]
}
