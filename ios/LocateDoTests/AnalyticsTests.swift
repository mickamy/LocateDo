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

    @Test func purchaseErrorsAreReportedByDomainAndCode() {
        let error = NSError(domain: "RevenueCat.ErrorCode", code: 2)

        #expect(PaywallAnalytics.reason(for: error) == "RevenueCat.ErrorCode:2")
    }

    @Test func editorsReportWhetherTheyCreateOrEdit() {
        #expect(EditorMode(editing: nil) == .new)
        #expect(EditorMode(editing: Place(name: "Store", latitude: 35.0, longitude: 139.0)) == .edit)
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

struct ScreenStackTests {
    private func entry(_ screen: AnalyticsScreen) -> ScreenStack.Entry {
        ScreenStack.Entry(screen: screen, parameters: [:])
    }

    @Test func closingASheetUncoversTheScreenBelowIt() {
        var stack = ScreenStack()
        stack.appeared(entry(.home), at: 0)
        stack.appeared(entry(.settings), at: 1)
        stack.appeared(entry(.categories), at: 1)

        #expect(stack.closed(from: 1)?.screen == .home)
        #expect(stack.screens == [.home])
    }

    @Test func closingStackedSheetsUncoversTheTopOneLeft() {
        var stack = ScreenStack()
        stack.appeared(entry(.placeDetail), at: 0)
        stack.appeared(entry(.todoEditor), at: 1)
        stack.appeared(entry(.placePicker), at: 2)
        stack.appeared(entry(.paywall), at: 3)

        #expect(stack.closed(from: 2)?.screen == .todoEditor)
        #expect(stack.screens == [.placeDetail, .todoEditor])
    }

    @Test func aLevelClosedTwiceIsSentOnce() {
        var stack = ScreenStack()
        stack.appeared(entry(.home), at: 0)
        stack.appeared(entry(.sharing), at: 1)

        #expect(stack.closed(from: 1) != nil)
        #expect(stack.closed(from: 1) == nil)
    }

    // A notification opens a place behind Settings, which then closes; the place is already the current screen.
    @Test func aScreenAppearingBelowDropsTheSheetsAboveIt() {
        var stack = ScreenStack()
        stack.appeared(entry(.home), at: 0)
        stack.appeared(entry(.settings), at: 1)
        stack.appeared(entry(.placeDetail), at: 0)

        #expect(stack.closed(from: 1) == nil)
        #expect(stack.screens == [.placeDetail])
    }
}
