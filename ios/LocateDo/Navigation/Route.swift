import Foundation

// Screens pushed over Home, the only root.
enum Route: Hashable {
    case place(Place, entry: ScreenEntry, rank: Int? = nil)
    case map
    case allTodos(entry: ScreenEntry)
}
