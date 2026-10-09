import Foundation

extension WatchSnapshot {
    init(places: [LocateDo.Place], userID: UUID?) {
        var entries: [Place] = []
        for place in places {
            let todos = NotificationPolicy.notifiableTodos(place.openTodos, for: userID)
            if todos.isEmpty {
                continue
            }
            entries.append(Place(
                id: place.id,
                name: place.name,
                categoryIcon: place.category?.icon,
                categoryColor: place.category?.color,
                todos: todos.map { Todo(id: $0.id, title: $0.title) }
            ))
        }
        self.init(places: entries)
    }
}
