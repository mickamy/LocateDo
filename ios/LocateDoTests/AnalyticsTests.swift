import Foundation
import Testing

@testable import LocateDo

struct AnalyticsTests {
    @Test func parametersAreKeyedByTheirWireNames() throws {
        let values = try #require(Analytics.firebaseParameters([.trigger: "share", .openTodos: 3]))

        #expect(values["trigger"] as? String == "share")
        #expect(values["open_todos"] as? Int == 3)
    }

    @Test func flagsGoOutAsZeroOrOne() throws {
        let values = try #require(Analytics.firebaseParameters([.plan: true, .source: false]))

        #expect(values["plan"] as? Int == 1)
        #expect(values["source"] as? Int == 0)
    }

    @Test func noParametersSendNil() {
        #expect(Analytics.firebaseParameters([:]) == nil)
    }
}

struct InstallDateTests {
    @Test func theFirstLaunchIsKept() throws {
        let defaults = try makeDefaults()
        let first = Date(timeIntervalSince1970: 1_800_000_000)

        InstallDate.record(defaults: defaults, now: first)
        InstallDate.record(defaults: defaults, now: first.addingTimeInterval(86_400))

        #expect(defaults.object(forKey: InstallDate.key) as? Date == first)
    }

    @Test func daysCountCalendarDaysNotElapsedHours() throws {
        let defaults = try makeDefaults()
        let calendar = Self.calendar
        let lateEvening = try #require(calendar.date(from: DateComponents(year: 2026, month: 10, day: 6, hour: 23)))
        let nextMorning = try #require(calendar.date(from: DateComponents(year: 2026, month: 10, day: 7, hour: 7)))
        InstallDate.record(defaults: defaults, now: lateEvening)

        #expect(InstallDate.daysSinceInstall(defaults: defaults, now: lateEvening, calendar: calendar) == 0)
        #expect(InstallDate.daysSinceInstall(defaults: defaults, now: nextMorning, calendar: calendar) == 1)
    }

    @Test func anUnrecordedInstallCountsAsDayZero() throws {
        let defaults = try makeDefaults()

        #expect(InstallDate.daysSinceInstall(defaults: defaults, now: .now) == 0)
    }

    @Test func aClockSetBackDoesNotGoNegative() throws {
        let defaults = try makeDefaults()
        let installedAt = Date(timeIntervalSince1970: 1_800_000_000)
        InstallDate.record(defaults: defaults, now: installedAt)

        let days = InstallDate.daysSinceInstall(defaults: defaults, now: installedAt.addingTimeInterval(-3 * 86_400))

        #expect(days == 0)
    }

    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Tokyo")!
        return calendar
    }()

    private func makeDefaults() throws -> UserDefaults {
        let suite = "InstallDateTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defaults.removePersistentDomain(forName: suite)
        return defaults
    }
}
