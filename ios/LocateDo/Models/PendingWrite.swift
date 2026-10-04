import Foundation
import SwiftData
import SwiftProtobuf

nonisolated enum WriteKind: String, Codable {
    case putPlace
    case deletePlace
    case putTodo
    case setTodoCompletion
    case deleteTodo
    case putCategory
    case deleteCategory
}

nonisolated enum Write: Equatable {
    case putPlace(Locatedo_Place_V1_PlaceInput)
    case deletePlace(Locatedo_Place_V1_DeletePlaceRequest)
    case putTodo(Locatedo_Todo_V1_TodoInput)
    case setTodoCompletion(Locatedo_Todo_V1_SetTodoCompletionRequest)
    case deleteTodo(Locatedo_Todo_V1_DeleteTodoRequest)
    case putCategory(Locatedo_Category_V1_CategoryInput)
    case deleteCategory(Locatedo_Category_V1_DeleteCategoryRequest)

    static func put(_ place: Place) -> Write {
        .putPlace(ProtoInput.place(place))
    }

    static func put(_ todo: Todo) -> Write? {
        guard let input = ProtoInput.todo(todo) else {
            return nil
        }
        return .putTodo(input)
    }

    static func put(_ category: PlaceCategory) -> Write {
        .putCategory(ProtoInput.category(category))
    }

    static func completion(of todo: Todo) -> Write {
        var request = Locatedo_Todo_V1_SetTodoCompletionRequest()
        request.id = ProtoInput.id(todo.id)
        if let completedAt = todo.completedAt {
            request.completedAt = Google_Protobuf_Timestamp(date: completedAt)
        }
        return .setTodoCompletion(request)
    }

    static func delete(_ place: Place) -> Write {
        var request = Locatedo_Place_V1_DeletePlaceRequest()
        request.id = ProtoInput.id(place.id)
        return .deletePlace(request)
    }

    static func delete(_ todo: Todo) -> Write {
        var request = Locatedo_Todo_V1_DeleteTodoRequest()
        request.id = ProtoInput.id(todo.id)
        return .deleteTodo(request)
    }

    static func delete(_ category: PlaceCategory) -> Write {
        var request = Locatedo_Category_V1_DeleteCategoryRequest()
        request.id = ProtoInput.id(category.id)
        return .deleteCategory(request)
    }

    var kind: WriteKind {
        switch self {
        case .putPlace: .putPlace
        case .deletePlace: .deletePlace
        case .putTodo: .putTodo
        case .setTodoCompletion: .setTodoCompletion
        case .deleteTodo: .deleteTodo
        case .putCategory: .putCategory
        case .deleteCategory: .deleteCategory
        }
    }

    func serialized() throws -> Data {
        switch self {
        case .putPlace(let message): try message.serializedBytes()
        case .deletePlace(let message): try message.serializedBytes()
        case .putTodo(let message): try message.serializedBytes()
        case .setTodoCompletion(let message): try message.serializedBytes()
        case .deleteTodo(let message): try message.serializedBytes()
        case .putCategory(let message): try message.serializedBytes()
        case .deleteCategory(let message): try message.serializedBytes()
        }
    }

    init(kind: WriteKind, payload: Data) throws {
        switch kind {
        case .putPlace: self = .putPlace(try .init(serializedBytes: payload))
        case .deletePlace: self = .deletePlace(try .init(serializedBytes: payload))
        case .putTodo: self = .putTodo(try .init(serializedBytes: payload))
        case .setTodoCompletion: self = .setTodoCompletion(try .init(serializedBytes: payload))
        case .deleteTodo: self = .deleteTodo(try .init(serializedBytes: payload))
        case .putCategory: self = .putCategory(try .init(serializedBytes: payload))
        case .deleteCategory: self = .deleteCategory(try .init(serializedBytes: payload))
        }
    }
}

@Model
final class PendingWrite {
    @Attribute(.unique) var id: UUID
    var sequence: Int64
    var kind: WriteKind
    var payload: Data
    var createdAt: Date
    var attempts: Int

    init(write: Write, sequence: Int64, now: Date = .now) throws {
        id = .v7()
        self.sequence = sequence
        kind = write.kind
        payload = try write.serialized()
        createdAt = now
        attempts = 0
    }

    func decoded() throws -> Write {
        try Write(kind: kind, payload: payload)
    }

    static func enqueue(_ write: Write, in context: ModelContext, now: Date = .now) throws {
        var last = FetchDescriptor<PendingWrite>(sortBy: [SortDescriptor(\.sequence, order: .reverse)])
        last.fetchLimit = 1
        let sequence = (try context.fetch(last).first?.sequence ?? 0) + 1
        context.insert(try PendingWrite(write: write, sequence: sequence, now: now))
    }

    static func head(in context: ModelContext) throws -> PendingWrite? {
        var first = FetchDescriptor<PendingWrite>(sortBy: [SortDescriptor(\.sequence)])
        first.fetchLimit = 1
        return try context.fetch(first).first
    }
}
