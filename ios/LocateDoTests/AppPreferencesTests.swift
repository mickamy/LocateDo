import Foundation
import Testing

@testable import LocateDo

struct AppPreferencesTests {
    @Test func startsWithDefaults() throws {
        let defaults = try makeDefaults()
        let preferences = AppPreferences(defaults: defaults)
        #expect(!preferences.hasCompletedOnboarding)
        #expect(preferences.defaultRadiusMeters == Place.defaultRadiusMeters)
    }

    @Test func persistsChanges() throws {
        let defaults = try makeDefaults()
        let preferences = AppPreferences(defaults: defaults)
        preferences.hasCompletedOnboarding = true
        preferences.hasPromptedAlwaysLocation = true
        preferences.defaultRadiusMeters = 250
        preferences.hasPendingSessionEndedNotice = true
        preferences.marketingConsent = true
        preferences.hasReceivedArrivalNotification = true
        preferences.hasShownMarketingPrompt = true

        let reloaded = AppPreferences(defaults: defaults)
        #expect(reloaded.hasCompletedOnboarding)
        #expect(reloaded.hasPromptedAlwaysLocation)
        #expect(reloaded.defaultRadiusMeters == 250)
        #expect(reloaded.hasPendingSessionEndedNotice)
        #expect(reloaded.marketingConsent)
        #expect(reloaded.hasReceivedArrivalNotification)
        #expect(reloaded.hasShownMarketingPrompt)
    }

    @Test func resetKeepsMarketingConsent() throws {
        let preferences = AppPreferences(defaults: try makeDefaults())
        preferences.marketingConsent = true
        preferences.hasReceivedArrivalNotification = true
        preferences.hasShownMarketingPrompt = true

        preferences.reset()

        #expect(preferences.marketingConsent)
        #expect(preferences.hasReceivedArrivalNotification)
        #expect(preferences.hasShownMarketingPrompt)
    }

    @Test func ignoresAnOutOfRangeStoredRadius() throws {
        let defaults = try makeDefaults()
        defaults.set(5.0, forKey: "defaultRadiusMeters")
        #expect(AppPreferences(defaults: defaults).defaultRadiusMeters == Place.defaultRadiusMeters)
    }

    private func makeDefaults() throws -> UserDefaults {
        let suite = "AppPreferencesTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defaults.removePersistentDomain(forName: suite)
        return defaults
    }
}
