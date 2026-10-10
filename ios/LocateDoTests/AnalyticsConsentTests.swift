import Foundation
import Testing

@testable import LocateDo

struct ConsentRegionTests {
    @Test(arguments: ["GBR", "DEU", "NOR", "irl"])
    func asksForAStorefrontInScopeWhateverTheDeviceRegion(storefront: String) {
        #expect(ConsentRegion.requiresConsent(storefront: storefront, region: "US"))
    }

    @Test(arguments: ["USA", "JPN", "CHE"])
    func skipsAStorefrontOutOfScopeWhateverTheDeviceRegion(storefront: String) {
        #expect(!ConsentRegion.requiresConsent(storefront: storefront, region: "GB"))
    }

    @Test(arguments: [
        ("GB", true),
        ("ie", true),
        ("US", false),
        ("JP", false)
    ])
    func fallsBackToTheDeviceRegion(region: String, expected: Bool) {
        #expect(ConsentRegion.requiresConsent(storefront: nil, region: region) == expected)
    }

    @Test func treatsAnUnknownRegionAsInScope() {
        #expect(ConsentRegion.requiresConsent(storefront: nil, region: nil))
    }

    @Test func takesTheStorefrontFromTheLaunchArguments() async {
        let storefront = await ConsentRegion.currentStorefront(arguments: ["LocateDo", "-consentStorefront", "GBR"])

        #expect(storefront == "GBR")
    }
}

struct AnalyticsConsentTests {
    @Test func sendsNothingBeforeTheFirstRegionCheck() throws {
        let consent = AnalyticsConsent(preferences: try Self.preferences(), region: "US")

        #expect(!consent.isSending)
        #expect(!consent.needsAnswer)
    }

    @Test func sendsOutsideTheEEAAndTheUKWithoutAsking() throws {
        let preferences = try Self.preferences()
        let consent = AnalyticsConsent(preferences: preferences, region: "US")
        let changes = Changes()
        consent.onChanged = changes.record

        consent.resolveRegion(storefront: "USA", region: "US")

        #expect(consent.isSending)
        #expect(!consent.needsAnswer)
        #expect(changes.values == [true])
        #expect(preferences.analyticsConsentRequired == false)
    }

    @Test func asksInTheEEAAndTheUK() throws {
        let consent = AnalyticsConsent(preferences: try Self.preferences(), region: "US")
        let changes = Changes()
        consent.onChanged = changes.record

        consent.resolveRegion(storefront: "GBR", region: "US")

        #expect(!consent.isSending)
        #expect(consent.needsAnswer)
        #expect(changes.values.isEmpty)
    }

    @Test func guessesFromTheDeviceRegionUntilTheStorefrontIsKnown() throws {
        let consent = AnalyticsConsent(preferences: try Self.preferences(), region: "FR")

        #expect(consent.needsAnswer)
    }

    @Test func startsFromTheLastRegionCheck() throws {
        let preferences = try Self.preferences()
        preferences.analyticsConsentRequired = false

        let consent = AnalyticsConsent(preferences: preferences, region: "FR")

        #expect(consent.isSending)
        #expect(!consent.needsAnswer)
    }

    @Test func grantingStartsSending() throws {
        let preferences = try Self.preferences()
        preferences.analyticsConsentRequired = true
        let consent = AnalyticsConsent(preferences: preferences, region: nil)
        let changes = Changes()
        consent.onChanged = changes.record

        consent.set(true, source: .onboarding)

        #expect(consent.isSending)
        #expect(!consent.needsAnswer)
        #expect(changes.values == [true])
    }

    @Test func decliningKeepsItOff() throws {
        let preferences = try Self.preferences()
        preferences.analyticsConsentRequired = true
        let consent = AnalyticsConsent(preferences: preferences, region: nil)
        let changes = Changes()
        consent.onChanged = changes.record

        consent.set(false, source: .onboarding)

        #expect(!consent.isSending)
        #expect(!consent.needsAnswer)
        #expect(changes.values.isEmpty)
    }

    @Test func turningOffInSettingsStopsSendingAnywhere() throws {
        let preferences = try Self.preferences()
        preferences.analyticsConsentRequired = false
        let consent = AnalyticsConsent(preferences: preferences, region: nil)
        let changes = Changes()
        consent.onChanged = changes.record

        consent.set(false, source: .settings)

        #expect(!consent.isSending)
        #expect(changes.values == [false])
    }

    @Test func anAnswerOutlastsAMoveIntoTheEEA() throws {
        let preferences = try Self.preferences()
        preferences.analyticsConsentRequired = false
        let consent = AnalyticsConsent(preferences: preferences, region: nil)
        consent.set(true, source: .settings)

        consent.resolveRegion(storefront: "DEU", region: "DE")

        #expect(consent.isSending)
        #expect(!consent.needsAnswer)
    }

    @Test func movingIntoTheEEAWithoutAnAnswerStopsSending() throws {
        let preferences = try Self.preferences()
        preferences.analyticsConsentRequired = false
        let consent = AnalyticsConsent(preferences: preferences, region: nil)
        let changes = Changes()
        consent.onChanged = changes.record

        consent.resolveRegion(storefront: "DEU", region: "DE")

        #expect(!consent.isSending)
        #expect(consent.needsAnswer)
        #expect(changes.values == [false])
    }

    private static func preferences() throws -> AppPreferences {
        let suite = "AnalyticsConsentTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defaults.removePersistentDomain(forName: suite)
        return AppPreferences(defaults: defaults)
    }
}

private final class Changes {
    var values: [Bool] = []

    func record(_ isSending: Bool) {
        values.append(isSending)
    }
}
