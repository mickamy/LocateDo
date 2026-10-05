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

    private let sender: WriteSender
    private let syncService: any Locatedo_Sync_V1_SyncServiceClientInterface
    private let authenticator: Authenticator
    private let context: ModelContext
    private let gate: MaintenanceGate
    private let onPlacesChanged: () async -> Void
    private(set) var lastPullSummary: String?
    @ObservationIgnored private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "sync")
    @ObservationIgnored private var inFlight: Task<Void, Never>?
    @ObservationIgnored private var rerunRequested = false
    @ObservationIgnored private var pullRequested = false
    @ObservationIgnored private var permissionDenied = false
    @ObservationIgnored var onRemoved: () async -> Void = {}
    @ObservationIgnored var isPro: () -> Bool = { false }
    @ObservationIgnored var onLimitRejected: (FreeLimit) -> Void = { _ in }
    @ObservationIgnored private var scheduled: Task<Void, Never>?

    init(
        places: any Locatedo_Place_V1_PlaceServiceClientInterface,
        todos: any Locatedo_Todo_V1_TodoServiceClientInterface,
        categories: any Locatedo_Category_V1_CategoryServiceClientInterface,
        syncService: any Locatedo_Sync_V1_SyncServiceClientInterface,
        authenticator: Authenticator,
        context: ModelContext,
        gate: MaintenanceGate = MaintenanceGate(),
        onPlacesChanged: @escaping () async -> Void = {}
    ) {
        sender = WriteSender(places: places, todos: todos, categories: categories, authenticator: authenticator)
        self.syncService = syncService
        self.authenticator = authenticator
        self.context = context
        self.gate = gate
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
        guard !gate.isClosed() else {
            return
        }
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
                if permissionDenied {
                    permissionDenied = false
                    await confirmSession()
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
            noteIfPermissionDenied(error)
            lastPullSummary = "failed: \(error)"
            logger.notice("Pull failed: \(error, privacy: .public)")
        }
    }

    // PermissionDenied means either the session ended or the member was removed; a refresh tells them apart.
    private func confirmSession() async {
        do {
            try await authenticator.refresh()
            logger.notice("Permission denied with a valid session; treating it as a removal from the household")
            // Not awaited: starting over syncs again, which would wait on this very run.
            Task {
                await onRemoved()
            }
        } catch {
            logger.notice("Permission denied and the refresh failed: \(error, privacy: .public)")
        }
    }

    private func noteIfPermissionDenied(_ error: any Error) {
        if (error as? ConnectError)?.code == .permissionDenied {
            permissionDenied = true
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
            try await sender.send(write, householdID: householdID)
            return true
        } catch {
            switch Self.failure(for: error) {
            case .drop:
                logger.error("Dropping a rejected \(kind, privacy: .public) write: \(error, privacy: .public)")
                return true
            case .keep:
                if (error as? ConnectError)?.code == .failedPrecondition && !isPro() {
                    return await reject(write, kind: kind)
                }
                noteIfPermissionDenied(error)
                head.attempts += 1
                logger.notice("Keeping the \(kind, privacy: .public) write at the head: \(error, privacy: .public)")
                return false
            }
        }
    }

    private func reject(_ write: Write, kind: String) async -> Bool {
        logger.notice("Dropping a \(kind, privacy: .public) write rejected by the free limit")
        do {
            if let limit = try LimitRejection.revert(write, in: context) {
                onLimitRejected(limit)
            }
            if case .putPlace = write {
                await onPlacesChanged()
            }
        } catch {
            logger.error("Could not undo a rejected write: \(error, privacy: .public)")
        }
        return true
    }
}

extension SyncEngine {
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
}
