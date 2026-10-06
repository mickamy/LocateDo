import Foundation
import SwiftData
import Testing

@testable import LocateDo

struct PurchaseLinkTests {
    @Test func signedInUsersAskTheServerToRecheckAfterLinking() async throws {
        let fixture = try Fixture(signedIn: true)

        await fixture.manager.linkPurchases(fixture.entitlements)

        #expect(fixture.source.loggedIn == ["0199bd00-0000-7000-8000-000000000001"])
        #expect(fixture.account.syncEntitlementCalls == 1)
    }

    @Test func aFailedLinkSkipsTheRecheck() async throws {
        let fixture = try Fixture(signedIn: true)
        fixture.source.failLogIns()

        await fixture.manager.linkPurchases(fixture.entitlements)

        #expect(fixture.account.syncEntitlementCalls == 0)
    }

    @Test func signedOutUsersAreNotLinked() async throws {
        let fixture = try Fixture(signedIn: false)

        await fixture.manager.linkPurchases(fixture.entitlements)

        #expect(fixture.source.loggedIn.isEmpty)
        #expect(fixture.account.syncEntitlementCalls == 0)
    }

    private struct Fixture {
        let container: ModelContainer
        let account = FakeAccountService()
        let source = FakeEntitlementSource(hasPro: true)
        let entitlements: Entitlements
        let manager: AccountManager

        init(signedIn: Bool) throws {
            container = try AppModelContainer.make(inMemory: true)
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
            entitlements = Entitlements(source: source)
            manager = AccountManager(
                account: account,
                household: FakeHouseholdService(),
                authenticator: authenticator,
                context: container.mainContext,
                resetsOffscreen: false
            )
        }
    }
}
