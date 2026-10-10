import Foundation

nonisolated enum TodoDeletionVia: String {
    case swipe
    case menu
    case editor
    case completedBulk = "completed_bulk"
}

// What a deleted to-do needs to come back from Undo.
nonisolated struct DeletedTodo: Equatable {
    let id: UUID
    let title: String
    let placeID: UUID
    let assigneeID: UUID?
    let creatorID: UUID?
    let completedAt: Date?
    let completerID: UUID?
    let placeEvent: PlaceEvent
    let createdAt: Date

    init(_ todo: Todo) {
        id = todo.id
        title = todo.title
        placeID = todo.place?.id ?? UUID()
        assigneeID = todo.assigneeID
        creatorID = todo.creatorID
        completedAt = todo.completedAt
        completerID = todo.completerID
        placeEvent = todo.placeEvent
        createdAt = todo.createdAt
    }

    func recreate(at place: Place) -> Todo {
        let todo = Todo(id: id, title: title, place: place, placeEvent: placeEvent, now: createdAt)
        todo.assigneeID = assigneeID
        todo.creatorID = creatorID
        todo.completedAt = completedAt
        todo.completerID = completerID
        return todo
    }
}
