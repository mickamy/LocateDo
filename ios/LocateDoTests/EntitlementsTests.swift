import Foundation
import Synchronization
import Testing

@testable import LocateDo

struct EntitlementsTests {
    @Test func proComesFromEitherTheEntitlementOrTheHouseholdPlan() {
        #expect(Entitlements.isPro(hasEntitlement: true, plan: .free))
        #expect(Entitlements.isPro(hasEntitlement: false, plan: .pro))
        #expect(!Entitlements.isPro(hasEntitlement: false, plan: .free))
        #expect(!Entitlements.isPro(hasEntitlement: false, plan: nil))
    }

    @Test func logsInWithTheLowercasedUserIDAndTakesItsEntitlement() async {
        let source = FakeEntitlementSource(hasPro: true)
        let entitlements = Entitlements(source: source)
        let userID = UUID(uuidString: "0199BD00-0000-7000-8000-000000000001")!

        await entitlements.logIn(userID: userID)

        #expect(source.loggedIn == ["0199bd00-0000-7000-8000-000000000001"])
        #expect(entitlements.hasEntitlement)
    }

    @Test func loggingOutDropsTheEntitlement() async {
        let source = FakeEntitlementSource(hasPro: true)
        let entitlements = Entitlements(source: source)
        await entitlements.logIn(userID: UUID())
        source.setHasPro(false)

        await entitlements.logOut()

        #expect(!entitlements.hasEntitlement)
    }

    @Test func withoutASourceNothingHappens() async {
        let entitlements = Entitlements(source: nil)

        await entitlements.logIn(userID: UUID())
        entitlements.start()

        #expect(!entitlements.hasEntitlement)
    }
}

nonisolated final class FakeEntitlementSource: EntitlementSource {
    private struct State {
        var hasPro: Bool
        var loggedIn: [String] = []
    }

    private let state: Mutex<State>

    init(hasPro: Bool) {
        state = Mutex(State(hasPro: hasPro))
    }

    var loggedIn: [String] {
        state.withLock { $0.loggedIn }
    }

    func setHasPro(_ hasPro: Bool) {
        state.withLock { $0.hasPro = hasPro }
    }

    func logIn(_ appUserID: String) async throws -> Bool {
        state.withLock { state in
            state.loggedIn.append(appUserID)
            return state.hasPro
        }
    }

    func logOut() async throws -> Bool {
        state.withLock { $0.hasPro }
    }

    func refresh() async throws -> Bool {
        state.withLock { $0.hasPro }
    }

    func updates() -> AsyncStream<Bool> {
        AsyncStream { $0.finish() }
    }
}
