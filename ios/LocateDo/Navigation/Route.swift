import Foundation

// Screens pushed over Home, the only root.
enum Route: Hashable {
    case place(Place)
    case map
    case allTodos
}
