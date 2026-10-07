import Foundation
import Testing

@testable import LocateDo

struct MarketingConsentTests {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    @Test func doesNotAskBeforeTheFirstArrival() throws {
        let consent = MarketingConsent(preferences: try Self.preferences())

        #expect(!consent.shouldPrompt(notificationAuth: .authorized, lastArrivalOpenedAt: nil, now: now))
    }

    @Test func asksAfterTheFirstArrival() throws {
        let consent = MarketingConsent(preferences: try Self.preferences())
        consent.arrivalNotified()

        #expect(consent.shouldPrompt(notificationAuth: .authorized, lastArrivalOpenedAt: nil, now: now))
        #expect(consent.shouldPrompt(notificationAuth: .provisional, lastArrivalOpenedAt: nil, now: now))
    }

    @Test func doesNotAskWithoutNotificationPermission() throws {
        let consent = MarketingConsent(preferences: try Self.preferences())
        consent.arrivalNotified()

        #expect(!consent.shouldPrompt(notificationAuth: .denied, lastArrivalOpenedAt: nil, now: now))
        #expect(!consent.shouldPrompt(notificationAuth: .notDetermined, lastArrivalOpenedAt: nil, now: now))
    }

    @Test func waitsAfterAnArrivalNotificationWasOpened() throws {
        let consent = MarketingConsent(preferences: try Self.preferences())
        consent.arrivalNotified()
        let quiet = MarketingConsent.quietPeriodAfterArrivalOpened

        #expect(!consent.shouldPrompt(
            notificationAuth: .authorized,
            lastArrivalOpenedAt: now.addingTimeInterval(-60),
            now: now
        ))
        #expect(consent.shouldPrompt(
            notificationAuth: .authorized,
            lastArrivalOpenedAt: now.addingTimeInterval(-quiet),
            now: now
        ))
    }

    @Test func asksOnlyOnce() throws {
        let consent = MarketingConsent(preferences: try Self.preferences())
        consent.arrivalNotified()

        consent.promptShown(daysSinceInstall: 3, notificationAuth: .authorized)

        #expect(!consent.shouldPrompt(notificationAuth: .authorized, lastArrivalOpenedAt: nil, now: now))
    }

    @Test func doesNotAskWhenAlreadyOn() throws {
        let consent = MarketingConsent(preferences: try Self.preferences())
        consent.arrivalNotified()
        consent.set(true, source: .settings)

        #expect(!consent.shouldPrompt(notificationAuth: .authorized, lastArrivalOpenedAt: nil, now: now))
    }

    @Test func acceptingTurnsConsentOn() async throws {
        let preferences = try Self.preferences()
        let consent = MarketingConsent(preferences: preferences)
        let changes = Counter()
        consent.onChanged = {
            changes.value += 1
        }

        await consent.answerPrompt(.accepted, after: 4)?.value

        #expect(preferences.marketingConsent)
        #expect(changes.value == 1)
    }

    @Test func decliningOrDismissingLeavesConsentOff() throws {
        let preferences = try Self.preferences()
        let consent = MarketingConsent(preferences: preferences)

        #expect(consent.answerPrompt(.declined, after: 2) == nil)
        #expect(consent.answerPrompt(.dismissed, after: 2) == nil)
        #expect(!preferences.marketingConsent)
    }

    @Test func notifiesOnlyWhenTheValueChanges() async throws {
        let consent = MarketingConsent(preferences: try Self.preferences())
        let changes = Counter()
        consent.onChanged = {
            changes.value += 1
        }

        await consent.set(true, source: .settings)?.value
        #expect(consent.set(true, source: .settings) == nil)
        await consent.set(false, source: .settings)?.value

        #expect(!consent.isOn)
        #expect(changes.value == 2)
    }

    private static func preferences() throws -> AppPreferences {
        let suite = "MarketingConsentTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defaults.removePersistentDomain(forName: suite)
        return AppPreferences(defaults: defaults)
    }
}

private final class Counter {
    var value = 0
}
