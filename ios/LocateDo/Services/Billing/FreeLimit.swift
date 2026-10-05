import Foundation

enum FreeLimit {
    case places
    case openTodos

    static let maxPlaces = 3
    static let maxOpenTodos = 15

    var trigger: PaywallTrigger {
        switch self {
        case .places: .placeLimit
        case .openTodos: .todoLimit
        }
    }
}
