import Foundation
import Observation
import OSLog
import SwiftData

@Observable
final class LocalWrites {
    private let context: ModelContext
    private let isSignedIn: () -> Bool
    private let onQueued: () -> Void
    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "sync")
    @ObservationIgnored var isPro: () -> Bool = { false }

    init(context: ModelContext, isSignedIn: @escaping () -> Bool, onQueued: @escaping () -> Void = {}) {
        self.context = context
        self.isSignedIn = isSignedIn
        self.onQueued = onQueued
    }

    @discardableResult
    func add(_ place: Place) -> FreeLimit? {
        if reached(.places) {
            return .places
        }
        context.insert(place)
        commit([.put(place)])
        return nil
    }

    func update(_ place: Place, now: Date = .now) {
        place.updatedAt = now
        commit([.put(place)])
    }

    func delete(_ place: Place) {
        let write = Write.delete(place)
        context.delete(place)
        commit([write])
    }

    @discardableResult
    func add(_ todo: Todo) -> FreeLimit? {
        if reached(.openTodos) {
            return .openTodos
        }
        context.insert(todo)
        todo.place?.todos.append(todo)
        commit([Write.put(todo)].compactMap(\.self))
        return nil
    }

    @discardableResult
    func toggleCompletion(_ todo: Todo, now: Date = .now) -> FreeLimit? {
        if todo.isCompleted {
            if reached(.openTodos) {
                return .openTodos
            }
            todo.reopen(at: now)
        } else {
            todo.complete(at: now)
        }
        commit([.completion(of: todo)])
        return nil
    }

    func setAssignee(_ assigneeID: UUID?, of todo: Todo, now: Date = .now) {
        todo.assigneeID = assigneeID
        todo.updatedAt = now
        commit([Write.put(todo)].compactMap(\.self))
    }

    func delete(_ todos: [Todo]) {
        let writes = todos.map(Write.delete)
        for todo in todos {
            context.delete(todo)
        }
        commit(writes)
    }

    func add(_ category: PlaceCategory) {
        let last = FetchDescriptor<PlaceCategory>(sortBy: [SortDescriptor(\.sortOrder, order: .reverse)])
        let highest = (try? context.fetch(last).first?.sortOrder) ?? -1
        category.sortOrder = highest + 1
        context.insert(category)
        commit([.put(category)])
    }

    func update(_ category: PlaceCategory, now: Date = .now) {
        category.updatedAt = now
        commit([.put(category)])
    }

    // The last category stays, because an empty store re-seeds the built-ins at launch.
    @discardableResult
    func delete(_ category: PlaceCategory) -> Bool {
        let count = (try? context.fetchCount(FetchDescriptor<PlaceCategory>())) ?? 0
        guard count > 1 else {
            return false
        }
        let write = Write.delete(category)
        context.delete(category)
        commit([write])
        return true
    }

    func reorder(_ categories: [PlaceCategory], now: Date = .now) {
        var writes: [Write] = []
        for (index, category) in categories.enumerated() where category.sortOrder != index {
            category.sortOrder = index
            category.updatedAt = now
            writes.append(.put(category))
        }
        commit(writes)
    }

    private func reached(_ limit: FreeLimit) -> Bool {
        if isPro() {
            return false
        }
        do {
            switch limit {
            case .places:
                return try context.fetchCount(FetchDescriptor<Place>()) >= FreeLimit.maxPlaces
            case .openTodos:
                let open = FetchDescriptor<Todo>(predicate: #Predicate { $0.completedAt == nil })
                return try context.fetchCount(open) >= FreeLimit.maxOpenTodos
            }
        } catch {
            logger.error("Could not count rows for the free limit: \(error, privacy: .public)")
            return false
        }
    }

    private func commit(_ writes: [Write]) {
        let queues = isSignedIn() && !writes.isEmpty
        do {
            if queues {
                for write in writes {
                    try PendingWrite.enqueue(write, in: context)
                }
            }
            try context.save()
        } catch {
            logger.error("Could not save a local write: \(error, privacy: .public)")
            return
        }
        if queues {
            onQueued()
        }
    }
}
