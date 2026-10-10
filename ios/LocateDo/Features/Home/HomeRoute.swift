import Foundation

enum HomeRoute: Hashable {
    case place(Place)
    case map
    case allTodos

    var place: Place? {
        if case .place(let place) = self {
            return place
        }
        return nil
    }
}
