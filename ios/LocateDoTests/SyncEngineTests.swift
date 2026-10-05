import Connect
import Foundation
import SwiftData
import SwiftProtobuf
import Testing

@testable import LocateDo

struct SyncEngineTests {
    static let householdID = UUID(uuidString: "0199bd00-0000-7000-8000-0000000000aa")!

    @Test func sendsTheQueueInOrderWithTheHouseholdID() async throws {
        let fixture = try SyncEngineFixture()
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
        let fixture = try SyncEngineFixture(signedIn: false)
        try fixture.enqueue([.put(Place(name: "Store", latitude: 35.0, longitude: 139.0))])

        await fixture.engine.drain()

        #expect(fixture.services.sent.isEmpty)
        #expect(try fixture.queueCount() == 1)
    }

    @Test func waitsForTheHousehold() async throws {
        let fixture = try SyncEngineFixture(householdID: nil)
        try fixture.enqueue([.put(Place(name: "Store", latitude: 35.0, longitude: 139.0))])

        await fixture.engine.drain()

        #expect(fixture.services.sent.isEmpty)
        #expect(try fixture.queueCount() == 1)
    }

    @Test func staysQuietDuringMaintenanceAndCatchesUpAfter() async throws {
        let fixture = try SyncEngineFixture()
        try fixture.enqueue([.put(Place(name: "Store", latitude: 35.0, longitude: 139.0))])
        fixture.gate.update(AppStatusDocument.Maintenance(
            startsAt: Date(timeIntervalSinceNow: -60),
            endsAt: Date(timeIntervalSinceNow: 3_600),
            message: nil
        ))

        await fixture.engine.sync()

        #expect(fixture.services.sent.isEmpty)
        #expect(fixture.pulls.cursors.isEmpty)
        let head = try #require(try PendingWrite.head(in: fixture.context))
        #expect(head.attempts == 0)

        fixture.gate.update(nil)
        await fixture.engine.sync()

        #expect(fixture.services.sent == ["putPlace"])
        #expect(fixture.pulls.cursors == [0])
        #expect(try fixture.queueCount() == 0)
    }

    @Test(arguments: [Code.unavailable, .permissionDenied, .internalError])
    func keepsTheHeadAndStops(code: Code) async throws {
        let fixture = try SyncEngineFixture()
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
        let fixture = try SyncEngineFixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        try fixture.enqueue([.put(place), .delete(place)])
        fixture.services.fail(with: [code])

        await fixture.engine.drain()

        #expect(fixture.services.sent == ["putPlace", "deletePlace"])
        #expect(try fixture.queueCount() == 0)
    }

    @Test func overlappingDrainsSendEachWriteOnce() async throws {
        let fixture = try SyncEngineFixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        try fixture.enqueue([.put(place), .delete(place)])

        async let first: Void = fixture.engine.drain()
        async let second: Void = fixture.engine.drain()
        _ = await (first, second)

        #expect(fixture.services.sent == ["putPlace", "deletePlace"])
        #expect(try fixture.queueCount() == 0)
    }

    @Test func rescheduledDrainReplacesThePendingOne() async throws {
        let fixture = try SyncEngineFixture()
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

    @Test func syncSendsTheQueueThenPullsEveryPage() async throws {
        let fixture = try SyncEngineFixture()
        let state = try SyncState.current(in: fixture.context)
        state.cursor = 10
        try fixture.context.save()
        try fixture.enqueue([.put(Place(name: "Local", latitude: 35.0, longitude: 139.0))])
        let placeID = UUID.v7()
        fixture.pulls.respond(with: [
            Self.page([.with { $0.place = Self.place(placeID, version: 11) }], cursor: 11, hasMore: true),
            Self.page([.with { $0.todo = Self.todo(UUID.v7(), placeID: placeID, version: 12) }], cursor: 12, plan: .pro)
        ])

        await fixture.engine.sync()

        #expect(fixture.services.sent == ["putPlace"])
        #expect(fixture.pulls.cursors == [10, 11])
        #expect(fixture.pulls.householdIDs.allSatisfy { $0 == ProtoInput.id(Self.householdID) })
        #expect(state.cursor == 12)
        #expect(state.plan == .pro)
        let pulled = try #require(try fixture.context.fetch(FetchDescriptor<Place>()).first { $0.id == placeID })
        #expect(pulled.todos.count == 1)
        #expect(fixture.placeChanges.value == 1)
    }

    @Test func resetOnAnyPageReplacesLocalData() async throws {
        let fixture = try SyncEngineFixture()
        fixture.context.insert(Place(name: "Local", latitude: 35.0, longitude: 139.0))
        try fixture.context.save()
        fixture.pulls.respond(with: [Self.page([], cursor: 30, reset: true)])

        await fixture.engine.sync()

        #expect(try fixture.context.fetchCount(FetchDescriptor<Place>()) == 0)
        #expect(try SyncState.current(in: fixture.context).cursor == 30)
        #expect(fixture.placeChanges.value == 1)
    }

    @Test func failedPullKeepsTheCursorAndAppliesNothing() async throws {
        let fixture = try SyncEngineFixture()
        fixture.pulls.respond(with: [
            Self.page([.with { $0.place = Self.place(UUID.v7(), version: 1) }], cursor: 1, hasMore: true)
        ])
        fixture.pulls.fail(afterPages: 1)

        await fixture.engine.sync()

        #expect(fixture.pulls.cursors == [0, 1])
        #expect(try SyncState.current(in: fixture.context).cursor == 0)
        #expect(try fixture.context.fetchCount(FetchDescriptor<Place>()) == 0)
        #expect(fixture.placeChanges.value == 0)
    }

    @Test func todoOnlyPullsLeaveTheGeofencesAlone() async throws {
        let fixture = try SyncEngineFixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.context.insert(place)
        try fixture.context.save()
        fixture.pulls.respond(with: [
            Self.page([.with { $0.todo = Self.todo(UUID.v7(), placeID: place.id, version: 1) }], cursor: 1)
        ])

        await fixture.engine.sync()

        #expect(place.todos.count == 1)
        #expect(fixture.placeChanges.value == 0)
    }

    @Test func drainAloneDoesNotPull() async throws {
        let fixture = try SyncEngineFixture()

        await fixture.engine.drain()

        #expect(fixture.pulls.cursors.isEmpty)
    }

    @Test func pullWaitsForTheHousehold() async throws {
        let fixture = try SyncEngineFixture(householdID: nil)

        await fixture.engine.sync()

        #expect(fixture.pulls.cursors.isEmpty)
    }

    @Test func aSyncRequestedDuringADrainStillPulls() async throws {
        let fixture = try SyncEngineFixture()
        try fixture.enqueue([.put(Place(name: "Store", latitude: 35.0, longitude: 139.0))])

        async let drained: Void = fixture.engine.drain()
        async let synced: Void = fixture.engine.sync()
        _ = await (drained, synced)

        #expect(fixture.services.sent == ["putPlace"])
        #expect(fixture.pulls.cursors == [0])
    }

    @Test func aDeniedWriteWithARejectedRefreshEndsTheSession() async throws {
        let fixture = try SyncEngineFixture()
        try fixture.enqueue([.put(Place(name: "Store", latitude: 35.0, longitude: 139.0))])
        fixture.services.fail(with: [.permissionDenied])
        fixture.account.enqueue(.failure(ConnectError(code: .unauthenticated, message: nil)))

        await fixture.engine.drain()

        #expect(fixture.account.refreshCalls == 1)
        #expect(fixture.sessionEnded.value == 1)
    }

    @Test func aDeniedPullWithAValidSessionKeepsItAndRefreshesOnce() async throws {
        let fixture = try SyncEngineFixture()
        fixture.pulls.fail(afterPages: 0, with: .permissionDenied)
        var refreshed = Locatedo_Account_V1_RefreshTokenResponse()
        refreshed.session.userID = "0199bd00-0000-7000-8000-000000000001"
        refreshed.session.accessToken = "access-2"
        refreshed.session.accessTokenExpiresAt = Google_Protobuf_Timestamp(date: Date(timeIntervalSinceNow: 3_600))
        refreshed.session.refreshToken = "refresh-2"
        fixture.account.enqueue(.success(refreshed))

        await fixture.engine.sync()

        #expect(fixture.account.refreshCalls == 1)
        #expect(fixture.sessionEnded.value == 0)
    }

    @Test func otherFailuresDoNotTouchTheSession() async throws {
        let fixture = try SyncEngineFixture()
        fixture.pulls.fail(afterPages: 0)

        await fixture.engine.sync()

        #expect(fixture.account.refreshCalls == 0)
    }

    private static func page(
        _ changes: [Locatedo_Sync_V1_Change],
        cursor: Int64,
        hasMore: Bool = false,
        reset: Bool = false,
        plan: Locatedo_Household_V1_Plan = .free
    ) -> Locatedo_Sync_V1_PullResponse {
        var response = Locatedo_Sync_V1_PullResponse()
        response.changes = changes
        response.cursor = cursor
        response.hasMore_p = hasMore
        response.reset = reset
        response.household.plan = plan
        return response
    }

    private static func place(_ id: UUID, version: Int64) -> Locatedo_Place_V1_Place {
        var place = Locatedo_Place_V1_Place()
        place.id = ProtoInput.id(id)
        place.name = "Store"
        place.lat = 35.0
        place.lng = 139.0
        place.radiusM = 100
        place.version = version
        return place
    }

    private static func todo(_ id: UUID, placeID: UUID, version: Int64) -> Locatedo_Todo_V1_Todo {
        var todo = Locatedo_Todo_V1_Todo()
        todo.id = ProtoInput.id(id)
        todo.placeID = ProtoInput.id(placeID)
        todo.title = "Milk"
        todo.version = version
        return todo
    }
}

struct SyncEngineFixture {
    let container: ModelContainer
    let context: ModelContext
    let services = FakeWriteServices()
    let pulls = FakeSyncService()
    let account = FakeAccountService()
    let sessionEnded = ResetCounter()
    let placeChanges = ResetCounter()
    let gate = MaintenanceGate()
    let engine: SyncEngine

    init(signedIn: Bool = true, householdID: UUID? = SyncEngineTests.householdID) throws {
        container = try AppModelContainer.make(inMemory: true)
        context = container.mainContext
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
            account: account,
            tokens: AccessTokenStore()
        )
        let sessionEnded = sessionEnded
        authenticator.onSessionEnded = { sessionEnded.increment() }
        let placeChanges = placeChanges
        engine = SyncEngine(
            places: services,
            todos: services,
            categories: services,
            syncService: pulls,
            authenticator: authenticator,
            context: context,
            gate: gate
        ) {
            placeChanges.increment()
        }
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
