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

    private let places: any Locatedo_Place_V1_PlaceServiceClientInterface
    private let todos: any Locatedo_Todo_V1_TodoServiceClientInterface
    private let categories: any Locatedo_Category_V1_CategoryServiceClientInterface
    private let authenticator: Authenticator
    private let context: ModelContext
    @ObservationIgnored private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "sync")
    @ObservationIgnored private var inFlight: Task<Void, Never>?
    @ObservationIgnored private var rerunRequested = false
    @ObservationIgnored private var scheduled: Task<Void, Never>?

    init(
        places: any Locatedo_Place_V1_PlaceServiceClientInterface,
        todos: any Locatedo_Todo_V1_TodoServiceClientInterface,
        categories: any Locatedo_Category_V1_CategoryServiceClientInterface,
        authenticator: Authenticator,
        context: ModelContext
    ) {
        self.places = places
        self.todos = todos
        self.categories = categories
        self.authenticator = authenticator
        self.context = context
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
        if let inFlight {
            rerunRequested = true
            await inFlight.value
            return
        }
        let task = Task {
            repeat {
                rerunRequested = false
                await sendQueuedWrites()
            } while rerunRequested
        }
        inFlight = task
        await task.value
        inFlight = nil
    }

    private func sendQueuedWrites() async {
        guard authenticator.isSignedIn else {
            return
        }
        do {
            guard let householdID = try SyncState.current(in: context).householdID else {
                return
            }
            while let head = try PendingWrite.head(in: context) {
                let delivered = await deliver(head, householdID: ProtoInput.id(householdID))
                if !delivered {
                    try context.save()
                    return
                }
                context.delete(head)
                try context.save()
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
