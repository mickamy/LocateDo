import Foundation
import Testing

@testable import LocateDo

struct ReinstallGuardTests {
    @Test func aReinstallDropsTheSessionLeftInTheKeychain() throws {
        let defaults = try makeDefaults()
        let store = InMemorySessionStore(Self.session)

        ReinstallGuard.clearStaleSession(defaults: defaults, store: store, hasCompletedOnboarding: false)

        #expect(store.current == nil)
        #expect(defaults.bool(forKey: ReinstallGuard.launchedKey))
    }

    @Test func anUpdatedInstallKeepsItsSession() throws {
        let defaults = try makeDefaults()
        let store = InMemorySessionStore(Self.session)

        ReinstallGuard.clearStaleSession(defaults: defaults, store: store, hasCompletedOnboarding: true)

        #expect(store.current == Self.session)
        #expect(defaults.bool(forKey: ReinstallGuard.launchedKey))
    }

    @Test func laterLaunchesLeaveTheSessionAlone() throws {
        let defaults = try makeDefaults()
        defaults.set(true, forKey: ReinstallGuard.launchedKey)
        let store = InMemorySessionStore(Self.session)

        ReinstallGuard.clearStaleSession(defaults: defaults, store: store, hasCompletedOnboarding: false)

        #expect(store.current == Self.session)
    }

    private static let session = Session(
        userID: UUID(uuidString: "0199bd00-0000-7000-8000-000000000001")!,
        accessToken: "access",
        accessTokenExpiresAt: Date(timeIntervalSince1970: 1_800_000_000),
        refreshToken: "refresh"
    )

    private func makeDefaults() throws -> UserDefaults {
        let suite = "ReinstallGuardTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defaults.removePersistentDomain(forName: suite)
        return defaults
    }
}
