import Connect
import Foundation
import Observation
import OSLog
import SwiftData
import SwiftProtobuf

@Observable
final class SyncEngine {
    private enum Failure {
        case keep
        case drop
    }

    private struct Pulled {
        var changes: [Locatedo_Sync_V1_Change] = []
        var cursor: Int64
        var reset = false
        var plan = Plan.free
    }

    private let places: any Locatedo_Place_V1_PlaceServiceClientInterface
    private let todos: any Locatedo_Todo_V1_TodoServiceClientInterface
    private let categories: any Locatedo_Category_V1_CategoryServiceClientInterface
    private let syncService: any Locatedo_Sync_V1_SyncServiceClientInterface
    private let authenticator: Authenticator
    private let context: ModelContext
    private let onPlacesChanged: () async -> Void
    private(set) var lastPullSummary: String?
    @ObservationIgnored private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "sync")
    @ObservationIgnored private var inFlight: Task<Void, Never>?
    @ObservationIgnored private var rerunRequested = false
    @ObservationIgnored private var pullRequested = false
    @ObservationIgnored private var scheduled: Task<Void, Never>?

    init(
        places: any Locatedo_Place_V1_PlaceServiceClientInterface,
        todos: any Locatedo_Todo_V1_TodoServiceClientInterface,
        categories: any Locatedo_Category_V1_CategoryServiceClientInterface,
        syncService: any Locatedo_Sync_V1_SyncServiceClientInterface,
        authenticator: Authenticator,
        context: ModelContext,
        onPlacesChanged: @escaping () async -> Void = {}
    ) {
        self.places = places
        self.todos = todos
        self.categories = categories
        self.syncService = syncService
        self.authenticator = authenticator
        self.context = context
        self.onPlacesChanged = onPlacesChanged
    }

    @discardableResult
    func scheduleDrain(after delay: Duration = .seconds(2)) -> Task<Void, Never> {
        scheduled?.cancel()
        let task = Task {
            do {
                try await Task.sleep(for: delay)
            } catch {
                return
            }
            await drain()
        }
        scheduled = task
        return task
    }

    func drain() async {
        await run(pulling: false)
    }

    func sync() async {
        await run(pulling: true)
    }

    private func run(pulling: Bool) async {
        rerunRequested = true
        if pulling {
            pullRequested = true
        }
        if let inFlight {
            await inFlight.value
            return
        }
        let task = Task {
            while rerunRequested {
                rerunRequested = false
                let pulls = pullRequested
                pullRequested = false
                await sendQueuedWrites()
                if pulls {
                    await pullChanges()
                }
            }
            inFlight = nil
        }
        inFlight = task
        await task.value
    }

    private func pullChanges() async {
        guard authenticator.isSignedIn else {
            return
        }
        do {
            let state = try SyncState.current(in: context)
            guard let householdID = state.householdID else {
                return
            }
            let pulled = try await fetchChanges(householdID: householdID, after: state.cursor)
            let current = try SyncState.current(in: context)
            guard current.householdID == householdID else {
                return
            }
            let outcome = try ChangeApplier.apply(pulled.changes, reset: pulled.reset, to: context)
            let previousCursor = current.cursor
            current.cursor = pulled.cursor
            current.plan = pulled.plan
            try context.save()
            var summary = "\(pulled.changes.count) changes, cursor \(previousCursor) → \(pulled.cursor)"
            if pulled.reset {
                summary += ", reset"
            }
            lastPullSummary = summary
            logger.notice("Pulled \(summary, privacy: .public)")
            if outcome.placesChanged {
                await onPlacesChanged()
            }
        } catch {
            lastPullSummary = "failed: \(error)"
            logger.notice("Pull failed: \(error, privacy: .public)")
        }
    }

    private func fetchChanges(
        householdID: UUID,
        after cursor: Int64
    ) async throws -> Pulled {
        var pulled = Pulled(cursor: cursor)
        var hasMore = true
        let client = syncService
        while hasMore {
            let request = Locatedo_Sync_V1_PullRequest.with { [cursor = pulled.cursor] in
                $0.householdID = ProtoInput.id(householdID)
                $0.cursor = cursor
            }
            let response = try await authenticator.authorized { await client.pull(request: request, headers: [:]) }
            pulled.changes.append(contentsOf: response.changes)
            pulled.cursor = response.cursor
            if response.reset {
                pulled.reset = true
            }
            if response.household.plan == .pro {
                pulled.plan = .pro
            } else {
                pulled.plan = .free
            }
            hasMore = response.hasMore_p
        }
        return pulled
    }

    private func sendQueuedWrites() async {
        guard authenticator.isSignedIn else {
            return
        }
        do {
            guard let householdID = try SyncState.current(in: context).householdID else {
                return
            }
            var sent = 0
            defer {
                if sent > 0 {
                    logger.notice("Sent \(sent) queued writes")
                }
            }
            while let head = try PendingWrite.head(in: context) {
                let delivered = await deliver(head, householdID: ProtoInput.id(householdID))
                if !delivered {
                    try context.save()
                    return
                }
                context.delete(head)
                try context.save()
                sent += 1
            }
        } catch {
            logger.error("Could not read the write queue: \(error, privacy: .public)")
        }
    }

    private func deliver(_ head: PendingWrite, householdID: String) async -> Bool {
        let kind = head.kind.rawValue
        let write: Write
        do {
            write = try head.decoded()
        } catch {
            logger.error("Dropping an unreadable \(kind, privacy: .public) write: \(error, privacy: .public)")
            return true
        }
        do {
            try await send(write, householdID: householdID)
            return true
        } catch {
            switch Self.failure(for: error) {
            case .drop:
                logger.error("Dropping a rejected \(kind, privacy: .public) write: \(error, privacy: .public)")
                return true
            case .keep:
                head.attempts += 1
                logger.notice("Keeping the \(kind, privacy: .public) write at the head: \(error, privacy: .public)")
                return false
            }
        }
    }

    private static func failure(for error: any Error) -> Failure {
        guard let error = error as? ConnectError else {
            return .keep
        }
        switch error.code {
        case .alreadyExists, .invalidArgument, .notFound:
            return .drop
        default:
            return .keep
        }
    }

    private func send(_ write: Write, householdID: String) async throws {
        switch write {
        case .putPlace(let input):
            let request = Locatedo_Place_V1_PutPlaceRequest.with {
                $0.householdID = householdID
                $0.place = input
            }
            let client = places
            _ = try await authenticator.authorized { await client.putPlace(request: request, headers: [:]) }
        case .deletePlace(let request):
            let client = places
            _ = try await authenticator.authorized { await client.deletePlace(request: request, headers: [:]) }
        case .putTodo(let input):
            let request = Locatedo_Todo_V1_PutTodoRequest.with {
                $0.householdID = householdID
                $0.todo = input
            }
            let client = todos
            _ = try await authenticator.authorized { await client.putTodo(request: request, headers: [:]) }
        case .setTodoCompletion(let request):
            let client = todos
            _ = try await authenticator.authorized { await client.setTodoCompletion(request: request, headers: [:]) }
        case .deleteTodo(let request):
            let client = todos
            _ = try await authenticator.authorized { await client.deleteTodo(request: request, headers: [:]) }
        case .putCategory(let input):
            let request = Locatedo_Category_V1_PutCategoryRequest.with {
                $0.householdID = householdID
                $0.category = input
            }
            let client = categories
            _ = try await authenticator.authorized { await client.putCategory(request: request, headers: [:]) }
        case .deleteCategory(let request):
            let client = categories
            _ = try await authenticator.authorized { await client.deleteCategory(request: request, headers: [:]) }
        }
    }
}
