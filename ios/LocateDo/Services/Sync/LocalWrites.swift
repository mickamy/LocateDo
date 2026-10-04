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

    init(context: ModelContext, isSignedIn: @escaping () -> Bool, onQueued: @escaping () -> Void = {}) {
        self.context = context
        self.isSignedIn = isSignedIn
        self.onQueued = onQueued
    }

    func add(_ place: Place) {
        context.insert(place)
        commit([.put(place)])
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

    func add(_ todo: Todo) {
        context.insert(todo)
        todo.place?.todos.append(todo)
        commit([Write.put(todo)].compactMap(\.self))
    }

    func toggleCompletion(_ todo: Todo, now: Date = .now) {
        if todo.isCompleted {
            todo.reopen(at: now)
        } else {
            todo.complete(at: now)
        }
        commit([.completion(of: todo)])
    }

    func delete(_ todos: [Todo]) {
        let writes = todos.map(Write.delete)
        for todo in todos {
            context.delete(todo)
        }
        commit(writes)
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
