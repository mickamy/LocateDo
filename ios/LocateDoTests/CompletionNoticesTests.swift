import Foundation
import Testing

@testable import LocateDo

struct CompletionNoticesTests {
    @Test func isOnByDefault() throws {
        #expect(CompletionNotices(preferences: try Self.preferences()).isOn)
    }

    @Test func notifiesOnlyWhenTheValueChanges() async throws {
        let preferences = try Self.preferences()
        let notices = CompletionNotices(preferences: preferences)
        let changes = ChangeCounter()
        notices.onChanged = {
            changes.value += 1
        }

        #expect(notices.set(true) == nil)
        await notices.set(false)?.value
        await notices.set(true)?.value

        #expect(preferences.completionNotices)
        #expect(changes.value == 2)
    }

    @Test func persists() throws {
        let suite = "CompletionNoticesTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defaults.removePersistentDomain(forName: suite)
        CompletionNotices(preferences: AppPreferences(defaults: defaults)).set(false)

        #expect(!AppPreferences(defaults: defaults).completionNotices)
    }

    private static func preferences() throws -> AppPreferences {
        let suite = "CompletionNoticesTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defaults.removePersistentDomain(forName: suite)
        return AppPreferences(defaults: defaults)
    }
}

private final class ChangeCounter {
    var value = 0
}
