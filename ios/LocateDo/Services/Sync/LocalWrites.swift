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
    @ObservationIgnored var analytics: any AnalyticsSink = FirebaseAnalyticsSink()
    @ObservationIgnored var daysSinceInstall: () -> Int = {
        InstallDate.daysSinceInstall(defaults: .standard, now: .now)
    }

    init(context: ModelContext, isSignedIn: @escaping () -> Bool, onQueued: @escaping () -> Void = {}) {
        self.context = context
        self.isSignedIn = isSignedIn
        self.onQueued = onQueued
    }

    @discardableResult
    func add(_ place: Place, source: PlaceSource? = nil) -> FreeLimit? {
        if reached(.places) {
            logLimitReached(.places)
            return .places
        }
        context.insert(place)
        commit([.put(place)])
        var parameters: AnalyticsParameters = [
            .placeCount: count(FetchDescriptor<Place>()),
            .category: place.analyticsCategory,
            .radiusM: Int(place.radiusMeters),
            .daysSinceInstall: daysSinceInstall()
        ]
        if let source {
            parameters[.source] = source.rawValue
        }
        analytics.log(.placeAdded, parameters: parameters)
        updateCountProperties()
        return nil
    }

    func update(_ place: Place, now: Date = .now) {
        place.updatedAt = now
        commit([.put(place)])
    }

    func delete(_ place: Place, now: Date = .now) {
        let write = Write.delete(place)
        let ageDays = Int(now.timeIntervalSince(place.createdAt) / 86_400)
        let openTodos = place.openTodos.count
        context.delete(place)
        commit([write])
        analytics.log(.placeDeleted, parameters: [
            .placeCount: count(FetchDescriptor<Place>()),
            .ageDays: max(ageDays, 0),
            .openTodos: openTodos
        ])
        updateCountProperties()
    }

    @discardableResult
    func add(_ todo: Todo) -> FreeLimit? {
        if reached(.openTodos) {
            logLimitReached(.openTodos)
            return .openTodos
        }
        context.insert(todo)
        todo.place?.todos.append(todo)
        commit([Write.put(todo)].compactMap(\.self))
        analytics.log(.todoAdded, parameters: [
            .openTodoCount: count(Self.openTodos),
            .placeOpenTodos: todo.place?.openTodos.count ?? 0,
            .assigned: todo.assigneeID != nil,
            .daysSinceInstall: daysSinceInstall()
        ])
        updateCountProperties()
        return nil
    }

    @discardableResult
    func toggleCompletion(_ todo: Todo, now: Date = .now) -> FreeLimit? {
        if todo.isCompleted {
            if reached(.openTodos) {
                logLimitReached(.openTodos)
                return .openTodos
            }
            todo.reopen(at: now)
        } else {
            todo.complete(at: now)
        }
        commit([.completion(of: todo)])
        updateCountProperties()
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
        updateCountProperties()
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
                return try context.fetchCount(Self.openTodos) >= FreeLimit.maxOpenTodos
            }
        } catch {
            logger.error("Could not count rows for the free limit: \(error, privacy: .public)")
            return false
        }
    }

    private static var openTodos: FetchDescriptor<Todo> {
        FetchDescriptor<Todo>(predicate: #Predicate { $0.completedAt == nil })
    }

    private func count<T: PersistentModel>(_ descriptor: FetchDescriptor<T>) -> Int {
        (try? context.fetchCount(descriptor)) ?? 0
    }

    func logLimitReached(_ limit: FreeLimit) {
        analytics.log(.limitReached, parameters: [
            .kind: limit.analyticsKind,
            .daysSinceInstall: daysSinceInstall()
        ])
    }

    private func updateCountProperties() {
        let places = count(FetchDescriptor<Place>())
        let openTodos = count(Self.openTodos)
        analytics.setUserProperty(DailyState.capped(places, at: DailyState.placeCountCap), for: .placeCount)
        analytics.setUserProperty(DailyState.capped(openTodos, at: DailyState.openTodoCountCap), for: .openTodoCount)
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
