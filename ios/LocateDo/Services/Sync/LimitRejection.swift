import Foundation
import SwiftData

enum LimitRejection {
    // Undoes on the device the row a rejected insert created; the server never had it.
    static func revert(_ write: Write, in context: ModelContext, now: Date = .now) throws -> FreeLimit? {
        switch write {
        case .putPlace(let input):
            for place in try places(id: input.id, in: context) {
                context.delete(place)
            }
            return .places
        case .putTodo(let input):
            for todo in try todos(id: input.id, in: context) {
                context.delete(todo)
            }
            return .openTodos
        case .setTodoCompletion(let request) where !request.hasCompletedAt:
            for todo in try todos(id: request.id, in: context) {
                todo.complete(at: now)
            }
            return .openTodos
        default:
            return nil
        }
    }

    private static func places(id: String, in context: ModelContext) throws -> [Place] {
        guard let uuid = UUID(uuidString: id) else {
            return []
        }
        return try context.fetch(FetchDescriptor<Place>(predicate: #Predicate { $0.id == uuid }))
    }

    private static func todos(id: String, in context: ModelContext) throws -> [Todo] {
        guard let uuid = UUID(uuidString: id) else {
            return []
        }
        return try context.fetch(FetchDescriptor<Todo>(predicate: #Predicate { $0.id == uuid }))
    }
}
