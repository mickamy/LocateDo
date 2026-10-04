import Connect
import Foundation
import SwiftData
import SwiftProtobuf
import Synchronization
import Testing

@testable import LocateDo

struct SyncEngineTests {
    private static let householdID = UUID(uuidString: "0199bd00-0000-7000-8000-0000000000aa")!

    @Test func sendsTheQueueInOrderWithTheHouseholdID() async throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        let todo = Todo(title: "Milk", place: place)
        let category = PlaceCategory(name: "Gym", icon: "dumbbell", color: "teal", sortOrder: 4)
        try fixture.enqueue([
            .put(category),
            .put(place),
            try #require(.put(todo)),
            .completion(of: todo),
            .delete(todo)
        ])

        await fixture.engine.drain()

        #expect(fixture.services.sent == ["putCategory", "putPlace", "putTodo", "setTodoCompletion", "deleteTodo"])
        #expect(fixture.services.householdIDs == Array(repeating: ProtoInput.id(Self.householdID), count: 3))
        #expect(try fixture.queueCount() == 0)
    }

    @Test func doesNothingWhenSignedOut() async throws {
        let fixture = try Fixture(signedIn: false)
        try fixture.enqueue([.put(Place(name: "Store", latitude: 35.0, longitude: 139.0))])

        await fixture.engine.drain()

        #expect(fixture.services.sent.isEmpty)
        #expect(try fixture.queueCount() == 1)
    }

    @Test func waitsForTheHousehold() async throws {
        let fixture = try Fixture(householdID: nil)
        try fixture.enqueue([.put(Place(name: "Store", latitude: 35.0, longitude: 139.0))])

        await fixture.engine.drain()

        #expect(fixture.services.sent.isEmpty)
        #expect(try fixture.queueCount() == 1)
    }

    @Test(arguments: [Code.unavailable, .failedPrecondition, .permissionDenied, .internalError])
    func keepsTheHeadAndStops(code: Code) async throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        try fixture.enqueue([.put(place), .delete(place)])
        fixture.services.fail(with: [code])

        await fixture.engine.drain()

        #expect(fixture.services.sent == ["putPlace"])
        let head = try #require(try PendingWrite.head(in: fixture.context))
        #expect(head.kind == .putPlace)
        #expect(head.attempts == 1)
        #expect(try fixture.queueCount() == 2)

        await fixture.engine.drain()

        #expect(fixture.services.sent == ["putPlace", "putPlace", "deletePlace"])
        #expect(try fixture.queueCount() == 0)
    }

    @Test(arguments: [Code.alreadyExists, .invalidArgument, .notFound])
    func dropsARejectedHeadAndContinues(code: Code) async throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        try fixture.enqueue([.put(place), .delete(place)])
        fixture.services.fail(with: [code])

        await fixture.engine.drain()

        #expect(fixture.services.sent == ["putPlace", "deletePlace"])
        #expect(try fixture.queueCount() == 0)
    }

    @Test func overlappingDrainsSendEachWriteOnce() async throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        try fixture.enqueue([.put(place), .delete(place)])

        async let first: Void = fixture.engine.drain()
        async let second: Void = fixture.engine.drain()
        _ = await (first, second)

        #expect(fixture.services.sent == ["putPlace", "deletePlace"])
        #expect(try fixture.queueCount() == 0)
    }

    @Test func rescheduledDrainReplacesThePendingOne() async throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        try fixture.enqueue([.put(place)])

        let first = fixture.engine.scheduleDrain(after: .seconds(60))
        try fixture.enqueue([.delete(place)])
        let second = fixture.engine.scheduleDrain(after: .zero)
        await first.value
        await second.value

        #expect(first.isCancelled)
        #expect(fixture.services.sent == ["putPlace", "deletePlace"])
        #expect(try fixture.queueCount() == 0)
    }

    private struct Fixture {
        let context: ModelContext
        let services = FakeWriteServices()
        let engine: SyncEngine

        init(signedIn: Bool = true, householdID: UUID? = SyncEngineTests.householdID) throws {
            context = try AppModelContainer.make(inMemory: true).mainContext
            let state = try SyncState.current(in: context)
            state.householdID = householdID
            try context.save()

            var session: Session?
            if signedIn {
                session = Session(
                    userID: UUID(uuidString: "0199bd00-0000-7000-8000-000000000001")!,
                    accessToken: "access",
                    accessTokenExpiresAt: Date(timeIntervalSinceNow: 3_600),
                    refreshToken: "refresh"
                )
            }
            let authenticator = Authenticator(
                store: InMemorySessionStore(session),
                account: FakeAccountService(),
                tokens: AccessTokenStore()
            )
            engine = SyncEngine(
                places: services,
                todos: services,
                categories: services,
                authenticator: authenticator,
                context: context
            )
        }

        func enqueue(_ writes: [Write]) throws {
            for write in writes {
                try PendingWrite.enqueue(write, in: context)
            }
            try context.save()
        }

        func queueCount() throws -> Int {
            try context.fetchCount(FetchDescriptor<PendingWrite>())
        }
    }
}

nonisolated final class FakeWriteServices: Locatedo_Place_V1_PlaceServiceClientInterface,
    Locatedo_Todo_V1_TodoServiceClientInterface,
    Locatedo_Category_V1_CategoryServiceClientInterface {
    private struct State {
        var sent: [String] = []
        var householdIDs: [String] = []
        var failures: [Code] = []
    }

    private let state = Mutex(State())

    var sent: [String] {
        state.withLock { $0.sent }
    }

    var householdIDs: [String] {
        state.withLock { $0.householdIDs }
    }

    func fail(with codes: [Code]) {
        state.withLock { $0.failures = codes }
    }

    func putPlace(
        request: Locatedo_Place_V1_PutPlaceRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Place_V1_PutPlaceResponse> {
        respond("putPlace", householdID: request.householdID)
    }

    func deletePlace(
        request: Locatedo_Place_V1_DeletePlaceRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Place_V1_DeletePlaceResponse> {
        respond("deletePlace")
    }

    func putTodo(
        request: Locatedo_Todo_V1_PutTodoRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Todo_V1_PutTodoResponse> {
        respond("putTodo", householdID: request.householdID)
    }

    func setTodoCompletion(
        request: Locatedo_Todo_V1_SetTodoCompletionRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Todo_V1_SetTodoCompletionResponse> {
        respond("setTodoCompletion")
    }

    func deleteTodo(
        request: Locatedo_Todo_V1_DeleteTodoRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Todo_V1_DeleteTodoResponse> {
        respond("deleteTodo")
    }

    func putCategory(
        request: Locatedo_Category_V1_PutCategoryRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Category_V1_PutCategoryResponse> {
        respond("putCategory", householdID: request.householdID)
    }

    func deleteCategory(
        request: Locatedo_Category_V1_DeleteCategoryRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Category_V1_DeleteCategoryResponse> {
        respond("deleteCategory")
    }

    private func respond<Output: ProtobufMessage>(
        _ name: String,
        householdID: String? = nil
    ) -> ResponseMessage<Output> {
        let failure = state.withLock { state -> Code? in
            state.sent.append(name)
            if let householdID {
                state.householdIDs.append(householdID)
            }
            if state.failures.isEmpty {
                return nil
            }
            return state.failures.removeFirst()
        }
        if let failure {
            return ResponseMessage(result: .failure(ConnectError(code: failure, message: nil)))
        }
        return ResponseMessage(result: .success(Output()))
    }
}
