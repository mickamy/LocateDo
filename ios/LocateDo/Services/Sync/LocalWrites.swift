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
    @ObservationIgnored var currentUserID: () -> UUID? = { nil }
    @ObservationIgnored var analytics: any AnalyticsSink = FirebaseAnalyticsSink()
    @ObservationIgnored var daysSinceInstall: () -> Int = {
        InstallDate.daysSinceInstall(defaults: .standard, now: .now)
    }
    @ObservationIgnored private(set) var lastArrivalOpen: ArrivalOpen?

    init(context: ModelContext, isSignedIn: @escaping () -> Bool, onQueued: @escaping () -> Void = {}) {
        self.context = context
        self.isSignedIn = isSignedIn
        self.onQueued = onQueued
    }

    // To-dos typed while adding the place follow it, as many as the free limit leaves room for.
    @discardableResult
    func add(
        _ place: Place,
        source: PlaceSource? = nil,
        todoTitles: [String] = [],
        suggestedCategory: BuiltinCategory? = nil
    ) -> FreeLimit? {
        if reached(.places) {
            logLimitReached(.places)
            return .places
        }
        var titles = todoTitles
        if let remaining = remaining(.openTodos) {
            titles = Array(titles.prefix(remaining))
        }
        context.insert(place)
        commit([.put(place)])
        var parameters: AnalyticsParameters = [
            .placeCount: count(FetchDescriptor<Place>()),
            .category: place.analyticsCategory,
            .radiusM: Int(place.radiusMeters),
            .todoCount: titles.count,
            .suggestedCategory: suggestedCategory?.rawValue ?? "none",
            .daysSinceInstall: daysSinceInstall()
        ]
        if let source {
            parameters[.source] = source.rawValue
        }
        analytics.log(.placeAdded, parameters: parameters)
        updateCountProperties()
        for title in titles {
            add(Todo(title: title, place: place), via: .placeEditor)
        }
        return nil
    }

    // nil when there is no limit (Pro).
    func remaining(_ limit: FreeLimit) -> Int? {
        if isPro() {
            return nil
        }
        switch limit {
        case .places:
            return max(FreeLimit.maxPlaces - count(FetchDescriptor<Place>()), 0)
        case .openTodos:
            return max(FreeLimit.maxOpenTodos - count(Self.openTodos), 0)
        }
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
    func add(_ todo: Todo, via: TodoAddVia = .todoEditor) -> FreeLimit? {
        if reached(.openTodos) {
            logLimitReached(.openTodos)
            return .openTodos
        }
        context.insert(todo)
        todo.place?.todos.append(todo)
        commit([Write.put(todo)].compactMap(\.self))
        analytics.log(.todoAdded, parameters: [
            .via: via.rawValue,
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
            todo.complete(by: currentUserID(), at: now)
        }
        commit([.completion(of: todo)])
        if todo.isCompleted {
            logCompletion(of: todo, now: now)
        }
        updateCountProperties()
        return nil
    }

    // From the arrival notification's checklist; to-dos someone else already checked off are skipped.
    @discardableResult
    func checkOff(_ todoIDs: [UUID], via: CompletionVia = .action, now: Date = .now) -> Int {
        var completed: [Todo] = []
        for id in todoIDs {
            var descriptor = FetchDescriptor<Todo>(predicate: #Predicate { $0.id == id })
            descriptor.fetchLimit = 1
            guard let todo = try? context.fetch(descriptor).first, !todo.isCompleted else {
                continue
            }
            todo.complete(by: currentUserID(), at: now)
            completed.append(todo)
        }
        if completed.isEmpty {
            return 0
        }
        commit(completed.map { .completion(of: $0) })
        for todo in completed {
            logCompletion(of: todo, via: via, now: now)
        }
        updateCountProperties()
        return completed.count
    }

    func arrivalOpened(placeID: UUID, at now: Date = .now) {
        lastArrivalOpen = ArrivalOpen(placeID: placeID, openedAt: now)
    }

    func setAssignee(_ assigneeID: UUID?, of todo: Todo, now: Date = .now) {
        todo.assigneeID = assigneeID
        todo.updatedAt = now
        commit([Write.put(todo)].compactMap(\.self))
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

    private func logCompletion(of todo: Todo, now: Date) {
        var via = CompletionVia.app
        if let lastArrivalOpen {
            via = lastArrivalOpen.via(completingAt: todo.place?.id, now: now)
        }
        logCompletion(of: todo, via: via, now: now)
    }

    private func logCompletion(of todo: Todo, via: CompletionVia, now: Date) {
        let ageHours = Int(now.timeIntervalSince(todo.createdAt) / 3_600)
        analytics.log(.todoCompleted, parameters: [
            .via: via.rawValue,
            .ageHours: max(ageHours, 0),
            .openTodoCount: count(Self.openTodos)
        ])
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
            CrashReporting.record(error, site: "sync.local_write")
            return
        }
        if queues {
            onQueued()
        }
    }
}

// Editing a to-do, deleting to-dos, and bringing them back with Undo.
extension LocalWrites {
    func update(
        _ todo: Todo,
        title: String,
        place: Place,
        assigneeID: UUID?,
        placeEvent: PlaceEvent,
        now: Date = .now
    ) {
        todo.title = title
        if todo.place?.id != place.id {
            todo.place = place
        }
        todo.assigneeID = assigneeID
        todo.placeEvent = placeEvent
        todo.updatedAt = now
        commit([Write.put(todo)].compactMap(\.self))
    }

    @discardableResult
    func delete(_ todos: [Todo], via: TodoDeletionVia) -> [DeletedTodo] {
        if todos.isEmpty {
            return []
        }
        let deleted = todos.map(DeletedTodo.init)
        let writes = todos.map(Write.delete)
        for todo in todos {
            context.delete(todo)
        }
        commit(writes)
        analytics.log(.todoDeleted, parameters: [.via: via.rawValue, .count: todos.count])
        updateCountProperties()
        return deleted
    }

    // Undo after a delete: the same ID comes back, so the server sees a put after the delete.
    func restore(_ deleted: [DeletedTodo]) {
        var writes: [Write] = []
        var restored = 0
        for entry in deleted {
            let placeID = entry.placeID
            var descriptor = FetchDescriptor<Place>(predicate: #Predicate { $0.id == placeID })
            descriptor.fetchLimit = 1
            guard let place = try? context.fetch(descriptor).first else {
                continue
            }
            let todo = entry.recreate(at: place)
            context.insert(todo)
            place.todos.append(todo)
            if let put = Write.put(todo) {
                writes.append(put)
            }
            if todo.isCompleted {
                writes.append(.completion(of: todo))
            }
            restored += 1
        }
        if restored == 0 {
            return
        }
        commit(writes)
        analytics.log(.todoDeleteUndone, parameters: [.count: restored])
        updateCountProperties()
    }
}
