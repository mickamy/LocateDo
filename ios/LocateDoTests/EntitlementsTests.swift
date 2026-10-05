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

    @Test func aPurchaseGrantsTheEntitlement() async throws {
        let entitlements = Entitlements(source: FakeEntitlementSource(hasPro: false))

        let completed = try await entitlements.purchase(.annual)

        #expect(completed)
        #expect(entitlements.hasEntitlement)
    }

    @Test func aCancelledPurchaseChangesNothing() async throws {
        let source = FakeEntitlementSource(hasPro: false)
        source.cancelPurchases()
        let entitlements = Entitlements(source: source)

        let completed = try await entitlements.purchase(.monthly)

        #expect(!completed)
        #expect(!entitlements.hasEntitlement)
    }

    @Test func restoringTakesWhateverTheStoreHas() async throws {
        let source = FakeEntitlementSource(hasPro: true)
        let entitlements = Entitlements(source: source)

        try await entitlements.restore()

        #expect(entitlements.hasEntitlement)
    }

    @Test func keepsWhatTheStoreSaysAboutTheSubscription() async {
        let subscription = ProSubscription(
            term: .monthly,
            expiresAt: Date(timeIntervalSince1970: 1_800_000_000),
            willRenew: false,
            isTrial: false,
            hasBillingIssue: false
        )
        let entitlements = Entitlements(source: FakeEntitlementSource(subscription: subscription))

        await entitlements.logIn(userID: UUID())

        #expect(entitlements.subscription == subscription)
        #expect(entitlements.hasEntitlement)
    }

    @Test func withoutASourceNothingHappens() async {
        let entitlements = Entitlements(source: nil)

        await entitlements.logIn(userID: UUID())
        entitlements.start()

        #expect(!entitlements.hasEntitlement)
    }
}

nonisolated final class FakeEntitlementSource: EntitlementSource {
    static let annual = ProSubscription(
        term: .annual,
        expiresAt: Date(timeIntervalSince1970: 1_800_000_000),
        willRenew: true,
        isTrial: false,
        hasBillingIssue: false
    )

    private struct State {
        var subscription: ProSubscription?
        var loggedIn: [String] = []
        var cancelsPurchase = false
    }

    private let state: Mutex<State>

    init(subscription: ProSubscription?) {
        state = Mutex(State(subscription: subscription))
    }

    convenience init(hasPro: Bool) {
        self.init(subscription: Self.subscription(hasPro: hasPro))
    }

    var loggedIn: [String] {
        state.withLock { $0.loggedIn }
    }

    func setHasPro(_ hasPro: Bool) {
        state.withLock { $0.subscription = Self.subscription(hasPro: hasPro) }
    }

    private static func subscription(hasPro: Bool) -> ProSubscription? {
        guard hasPro else {
            return nil
        }
        return annual
    }

    func logIn(_ appUserID: String) async throws -> ProSubscription? {
        state.withLock { state in
            state.loggedIn.append(appUserID)
            return state.subscription
        }
    }

    func logOut() async throws -> ProSubscription? {
        state.withLock { $0.subscription }
    }

    func refresh() async throws -> ProSubscription? {
        state.withLock { $0.subscription }
    }

    func updates() -> AsyncStream<ProSubscription?> {
        AsyncStream { $0.finish() }
    }

    func plans() async throws -> [PaywallPlan] {
        [
            PaywallPlan(kind: .annual, price: "$14.99", trialDays: 7),
            PaywallPlan(kind: .monthly, price: "$2.99", trialDays: nil)
        ]
    }

    func purchase(_ kind: PaywallPlan.Kind) async throws -> PurchaseOutcome {
        state.withLock { state in
            if state.cancelsPurchase {
                return .cancelled
            }
            state.subscription = Self.annual
            return .completed(Self.annual)
        }
    }

    func restore() async throws -> ProSubscription? {
        state.withLock { $0.subscription }
    }

    func cancelPurchases() {
        state.withLock { $0.cancelsPurchase = true }
    }
}
