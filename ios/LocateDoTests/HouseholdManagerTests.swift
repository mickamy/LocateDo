import Connect
import Foundation
import SwiftData
import Testing

@testable import LocateDo

struct HouseholdManagerTests {
    private static let userID = UUID(uuidString: "0199bd00-0000-7000-8000-000000000001")!
    private static let otherID = UUID(uuidString: "0199bd00-0000-7000-8000-000000000002")!
    private static let householdID = UUID(uuidString: "0199bd00-0000-7000-8000-0000000000aa")!

    @Test func removingAMemberSendsTheIDsAndPulls() async throws {
        let fixture = try Fixture()

        try await fixture.manager.remove(Self.otherID)

        let request = try #require(fixture.household.removed.first)
        #expect(request.householdID == ProtoInput.id(Self.householdID))
        #expect(request.userID == ProtoInput.id(Self.otherID))
        #expect(!fixture.pulls.cursors.isEmpty)
    }

    @Test func leavingSendsQueuedWritesThenStartsOverInANewHousehold() async throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.context.insert(place)
        try PendingWrite.enqueue(.put(place), in: fixture.context)
        try fixture.context.save()

        try await fixture.manager.leave()

        #expect(fixture.services.sent == ["putPlace"])
        #expect(fixture.household.removed.map(\.userID) == [ProtoInput.id(Self.userID)])
        #expect(try fixture.context.fetchCount(FetchDescriptor<Place>()) == 0)
        let created = try #require(fixture.household.lastCreate)
        #expect(created.places.isEmpty)
        #expect(created.categories.count == BuiltinCategory.allCases.count)
        let householdID = try SyncState.current(in: fixture.context).householdID
        #expect(householdID?.uuidString.lowercased() == created.id)
        #expect(householdID != Self.householdID)
    }

    @Test func aFailedLeaveKeepsEverything() async throws {
        let fixture = try Fixture()
        fixture.context.insert(Place(name: "Store", latitude: 35.0, longitude: 139.0))
        try fixture.context.save()
        fixture.household.failRemove()

        await #expect(throws: (any Error).self) {
            try await fixture.manager.leave()
        }

        #expect(try fixture.context.fetchCount(FetchDescriptor<Place>()) == 1)
        #expect(try SyncState.current(in: fixture.context).householdID == Self.householdID)
        #expect(fixture.household.createCalls == 0)
    }

    @Test func aRemovalStartsOverAndAsksForTheNotice() async throws {
        let fixture = try Fixture()
        fixture.context.insert(Place(name: "Store", latitude: 35.0, longitude: 139.0))
        try fixture.context.save()
        let notices = ResetCounter()
        fixture.manager.onRemoved = { notices.increment() }

        await fixture.manager.handleRemoval()

        #expect(try fixture.context.fetchCount(FetchDescriptor<Place>()) == 0)
        #expect(fixture.household.createCalls == 1)
        #expect(notices.value == 1)
    }

    @Test func anInviteIsALinkOnTheOwnedDomain() async throws {
        let fixture = try Fixture()

        let invite = try await fixture.manager.createInvite()

        #expect(invite.url.absoluteString == "https://locatedo.com/i/invite-token-0123456789")
        #expect(invite.expiresAt == Date(timeIntervalSince1970: 1_800_000_000))
    }

    @Test func acceptingSendsQueuedWritesThenMovesToTheNewHousehold() async throws {
        let fixture = try Fixture()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        fixture.context.insert(place)
        try PendingWrite.enqueue(.put(place), in: fixture.context)
        try fixture.context.save()
        let joined = UUID(uuidString: "0199bd00-0000-7000-8000-0000000000bb")!
        var response = Locatedo_Household_V1_AcceptInviteResponse()
        response.household.id = ProtoInput.id(joined)
        response.household.plan = .pro
        fixture.household.respondToAccept(with: .success(response))

        try await fixture.manager.accept(token: "invite-token-0123456789")

        #expect(fixture.services.sent == ["putPlace"])
        #expect(fixture.household.acceptedTokens == ["invite-token-0123456789"])
        #expect(try fixture.context.fetchCount(FetchDescriptor<Place>()) == 0)
        let state = try SyncState.current(in: fixture.context)
        #expect(state.householdID == joined)
        #expect(state.cursor == 0)
        #expect(state.plan == .pro)
    }

    @Test func aRejectedInviteKeepsEverything() async throws {
        let fixture = try Fixture()
        fixture.context.insert(Place(name: "Store", latitude: 35.0, longitude: 139.0))
        try fixture.context.save()
        fixture.household.respondToAccept(with: .failure(ConnectError(code: .failedPrecondition, message: nil)))

        await #expect(throws: ConnectError.self) {
            try await fixture.manager.accept(token: "invite-token-0123456789")
        }

        #expect(try fixture.context.fetchCount(FetchDescriptor<Place>()) == 1)
        #expect(try SyncState.current(in: fixture.context).householdID == Self.householdID)
    }

    private struct Fixture {
        let container: ModelContainer
        let context: ModelContext
        let household = FakeHouseholdService()
        let services = FakeWriteServices()
        let pulls = FakeSyncService()
        let manager: HouseholdManager

        init() throws {
            container = try AppModelContainer.make(inMemory: true)
            context = container.mainContext
            try SyncState.current(in: context).householdID = HouseholdManagerTests.householdID
            try context.save()

            let authenticator = Authenticator(
                store: InMemorySessionStore(Session(
                    userID: HouseholdManagerTests.userID,
                    accessToken: "access",
                    accessTokenExpiresAt: Date(timeIntervalSinceNow: 3_600),
                    refreshToken: "refresh"
                )),
                account: FakeAccountService(),
                tokens: AccessTokenStore()
            )
            let account = AccountManager(
                account: FakeAccountService(),
                household: household,
                authenticator: authenticator,
                context: context,
                resetsOffscreen: false
            )
            let sync = SyncEngine(
                places: services,
                todos: services,
                categories: services,
                syncService: pulls,
                authenticator: authenticator,
                context: context
            )
            manager = HouseholdManager(
                household: household,
                authenticator: authenticator,
                context: context,
                account: account,
                sync: sync
            )
        }
    }
}
