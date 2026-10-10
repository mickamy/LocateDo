import Foundation
import Testing

@testable import LocateDo

struct AppPreferencesTests {
    @Test func startsWithDefaults() throws {
        let defaults = try makeDefaults()
        let preferences = AppPreferences(defaults: defaults)
        #expect(!preferences.hasCompletedOnboarding)
        #expect(preferences.defaultRadiusMeters == Place.defaultRadiusMeters)
        #expect(preferences.analyticsConsent == nil)
        #expect(preferences.analyticsConsentRequired == nil)
    }

    @Test func persistsChanges() throws {
        let defaults = try makeDefaults()
        let preferences = AppPreferences(defaults: defaults)
        preferences.hasCompletedOnboarding = true
        preferences.reminderSetupShownAt = Date(timeIntervalSince1970: 1_800_000_000)
        preferences.reminderSetupShownCount = 2
        preferences.reminderSetupNever = true
        preferences.hasRequestedAlwaysLocation = true
        preferences.defaultRadiusMeters = 250
        preferences.hasPendingSessionEndedNotice = true
        preferences.promotionsConsent = true
        preferences.hasReceivedArrivalNotification = true
        preferences.hasShownPromotionsPrompt = true
        preferences.analyticsConsent = false
        preferences.analyticsConsentRequired = true

        let reloaded = AppPreferences(defaults: defaults)
        #expect(reloaded.hasCompletedOnboarding)
        #expect(reloaded.reminderSetupShownAt == Date(timeIntervalSince1970: 1_800_000_000))
        #expect(reloaded.reminderSetupShownCount == 2)
        #expect(reloaded.reminderSetupNever)
        #expect(reloaded.hasRequestedAlwaysLocation)
        #expect(reloaded.defaultRadiusMeters == 250)
        #expect(reloaded.hasPendingSessionEndedNotice)
        #expect(reloaded.promotionsConsent)
        #expect(reloaded.hasReceivedArrivalNotification)
        #expect(reloaded.hasShownPromotionsPrompt)
        #expect(reloaded.analyticsConsent == false)
        #expect(reloaded.analyticsConsentRequired == true)
    }

    @Test func resetKeepsPromotionsConsent() throws {
        let preferences = AppPreferences(defaults: try makeDefaults())
        preferences.promotionsConsent = true
        preferences.hasReceivedArrivalNotification = true
        preferences.hasShownPromotionsPrompt = true

        preferences.reset()

        #expect(preferences.promotionsConsent)
        #expect(preferences.hasReceivedArrivalNotification)
        #expect(preferences.hasShownPromotionsPrompt)
    }

    @Test func resetKeepsAnalyticsConsent() throws {
        let preferences = AppPreferences(defaults: try makeDefaults())
        preferences.analyticsConsent = true
        preferences.analyticsConsentRequired = true

        preferences.reset()

        #expect(preferences.analyticsConsent == true)
        #expect(preferences.analyticsConsentRequired == true)
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
