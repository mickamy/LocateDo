import Connect
import Foundation
import SwiftData
import SwiftProtobuf
import Synchronization
import Testing

@testable import LocateDo

struct AccountManagerTests {
    private static let userID = "0199bd00-0000-7000-8000-000000000001"

    @Test func newUserUploadsLocalDataAndStoresTheHousehold() async throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.context.insert(place)
        fixture.context.insert(Todo(title: "Milk", place: place))
        try fixture.context.save()
        fixture.account.respondToSignIn(with: .success(Self.signInResponse(householdID: nil)))

        try await fixture.manager.signInWithApple(
            identityToken: "identity",
            authorizationCode: "code",
            nonce: "nonce-0123456789abcdef",
            displayName: "Taro"
        )

        let sent = try #require(fixture.account.lastSignIn)
        #expect(sent.identityToken == "identity")
        #expect(sent.authorizationCode == "code")
        #expect(sent.nonce == "nonce-0123456789abcdef")
        #expect(sent.displayName == "Taro")
        #expect(fixture.manager.isSignedIn)

        let created = try #require(fixture.household.lastCreate)
        #expect(created.places.map(\.name) == ["Store"])
        #expect(created.todos.map(\.todo.title) == ["Milk"])
        #expect(created.categories.count == BuiltinCategory.allCases.count)

        let state = try SyncState.current(in: fixture.context)
        #expect(state.householdID?.uuidString.lowercased() == created.id)
        #expect(state.cursor == 42)
    }

    @Test func existingHouseholdIsAdoptedWithoutUploading() async throws {
        let fixture = try Fixture()
        let householdID = "0199bd00-0000-7000-8000-0000000000aa"
        fixture.account.respondToSignIn(with: .success(Self.signInResponse(householdID: householdID)))

        try await fixture.manager.signInWithApple(
            identityToken: "identity",
            authorizationCode: "code",
            nonce: "nonce-0123456789abcdef",
            displayName: nil
        )

        #expect(fixture.household.createCalls == 0)
        #expect(fixture.account.lastSignIn?.hasDisplayName == false)
        #expect(try SyncState.current(in: fixture.context).householdID == UUID(uuidString: householdID))
    }

    @Test func failedUploadRetriesWithTheSameHouseholdID() async throws {
        let fixture = try Fixture()
        fixture.account.respondToSignIn(with: .success(Self.signInResponse(householdID: nil)))
        fixture.household.failNext()

        await #expect(throws: ConnectError.self) {
            try await fixture.manager.signInWithApple(
                identityToken: "identity",
                authorizationCode: "code",
                nonce: "nonce-0123456789abcdef",
                displayName: nil
            )
        }
        let firstID = try #require(fixture.household.lastCreate?.id)
        #expect(try SyncState.current(in: fixture.context).householdID == nil)

        try await fixture.manager.uploadLocalDataIfNeeded()

        #expect(fixture.household.createCalls == 2)
        #expect(fixture.household.lastCreate?.id == firstID)
        #expect(try SyncState.current(in: fixture.context).householdID?.uuidString.lowercased() == firstID)
    }

    @Test func deletingTheAccountResetsLocalData() async throws {
        let fixture = try Fixture()
        fixture.account.respondToSignIn(with: .success(Self.signInResponse(householdID: nil)))
        try await fixture.manager.signInWithApple(
            identityToken: "identity",
            authorizationCode: "code",
            nonce: "nonce-0123456789abcdef",
            displayName: nil
        )
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.context.insert(place)
        fixture.context.insert(Todo(title: "Milk", place: place))
        try fixture.context.save()

        try await fixture.manager.deleteAccount()

        #expect(fixture.account.deleteCalls == 1)
        #expect(!fixture.manager.isSignedIn)
        #expect(try fixture.context.fetch(FetchDescriptor<Place>()).isEmpty)
        #expect(try fixture.context.fetch(FetchDescriptor<Todo>()).isEmpty)
        #expect(try fixture.context.fetch(FetchDescriptor<SyncState>()).isEmpty)
        #expect(try fixture.context.fetchCount(FetchDescriptor<PlaceCategory>()) == BuiltinCategory.allCases.count)
        #expect(fixture.resets.value == 1)
    }

    private static func signInResponse(householdID: String?) -> Locatedo_Account_V1_SignInWithAppleResponse {
        var response = Locatedo_Account_V1_SignInWithAppleResponse()
        response.session.userID = userID
        response.session.accessToken = "access"
        response.session.accessTokenExpiresAt = Google_Protobuf_Timestamp(date: Date(timeIntervalSinceNow: 3_600))
        response.session.refreshToken = "refresh"
        if let householdID {
            response.householdID = householdID
        }
        return response
    }

    private struct Fixture {
        let context: ModelContext
        let account = FakeAccountService()
        let household = FakeHouseholdService()
        let resets = ResetCounter()
        let manager: AccountManager

        init() throws {
            let container = try AppModelContainer.make(inMemory: true)
            context = container.mainContext
            let tokens = AccessTokenStore()
            let authenticator = Authenticator(store: InMemorySessionStore(), account: account, tokens: tokens)
            let resets = resets
            manager = AccountManager(
                account: account,
                household: household,
                authenticator: authenticator,
                context: context
            ) {
                resets.increment()
            }
        }
    }
}

nonisolated final class ResetCounter: Sendable {
    private let count = Mutex(0)

    var value: Int {
        count.withLock { $0 }
    }

    func increment() {
        count.withLock { $0 += 1 }
    }
}

nonisolated final class FakeHouseholdService: Locatedo_Household_V1_HouseholdServiceClientInterface {
    private struct State {
        var lastCreate: Locatedo_Household_V1_CreateHouseholdRequest?
        var createCalls = 0
        var failNext = false
    }

    private let state = Mutex(State())

    var lastCreate: Locatedo_Household_V1_CreateHouseholdRequest? {
        state.withLock { $0.lastCreate }
    }

    var createCalls: Int {
        state.withLock { $0.createCalls }
    }

    func failNext() {
        state.withLock { $0.failNext = true }
    }

    func createHousehold(
        request: Locatedo_Household_V1_CreateHouseholdRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Household_V1_CreateHouseholdResponse> {
        let shouldFail = state.withLock { state in
            state.lastCreate = request
            state.createCalls += 1
            let fail = state.failNext
            state.failNext = false
            return fail
        }
        if shouldFail {
            return ResponseMessage(result: .failure(ConnectError(code: .unavailable, message: nil)))
        }
        var response = Locatedo_Household_V1_CreateHouseholdResponse()
        response.household.id = request.id
        response.cursor = 42
        return ResponseMessage(result: .success(response))
    }

    func createInvite(
        request: Locatedo_Household_V1_CreateInviteRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Household_V1_CreateInviteResponse> {
        ResponseMessage(result: .failure(ConnectError(code: .unimplemented, message: nil)))
    }

    func acceptInvite(
        request: Locatedo_Household_V1_AcceptInviteRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Household_V1_AcceptInviteResponse> {
        ResponseMessage(result: .failure(ConnectError(code: .unimplemented, message: nil)))
    }

    func removeMember(
        request: Locatedo_Household_V1_RemoveMemberRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Household_V1_RemoveMemberResponse> {
        ResponseMessage(result: .failure(ConnectError(code: .unimplemented, message: nil)))
    }
}
