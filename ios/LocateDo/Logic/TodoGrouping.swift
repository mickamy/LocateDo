import Foundation

nonisolated enum TodoFilter: CaseIterable, Identifiable {
    case all
    case open
    case done

    var id: Self { self }

    var title: LocalizedStringResource {
        switch self {
        case .all: .todoFilterAll
        case .open: .todoFilterOpen
        case .done: .todoFilterDone
        }
    }

    func includes(_ todo: Todo) -> Bool {
        switch self {
        case .all: true
        case .open: !todo.isCompleted
        case .done: todo.isCompleted
        }
    }
}

struct TodoGroup: Identifiable {
    let place: Place
    let todos: [Todo]

    var id: UUID { place.id }
}

enum TodoGrouping {
    static func groups(_ todos: [Todo], filter: TodoFilter) -> [TodoGroup] {
        var placesByID: [UUID: Place] = [:]
        var todosByPlace: [UUID: [Todo]] = [:]
        for todo in todos where filter.includes(todo) {
            guard let place = todo.place else {
                continue
            }
            placesByID[place.id] = place
            todosByPlace[place.id, default: []].append(todo)
        }
        return placesByID.values
            .sorted { ($0.sortOrder, $0.name) < ($1.sortOrder, $1.name) }
            .map { place in
                let group = todosByPlace[place.id] ?? []
                return TodoGroup(place: place, todos: group.open + group.completedNewestFirst)
            }
    }
}
