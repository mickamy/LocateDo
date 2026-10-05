import Foundation
import Synchronization
import Testing

@testable import LocateDo

struct AppStatusTests {
    private static let fullJSON = """
    {
      "minimum_version": { "ios": "1.2.0", "android": "1.0.0" },
      "maintenance": {
        "starts_at": "2026-12-01T15:00:00Z",
        "ends_at": "2026-12-01T17:00:00Z",
        "message": { "ja": "移行のため", "en": "Moving servers" }
      },
      "notice": {
        "id": "2026-12-terms",
        "until": "2026-12-31T00:00:00Z",
        "message": { "ja": "規約を更新しました", "en": "Terms updated" }
      },
      "something_new": true
    }
    """
    private static let full = Data(fullJSON.utf8)

    private static let starts = Date(timeIntervalSince1970: 1_796_137_200)
    private static let ends = Date(timeIntervalSince1970: 1_796_144_400)

    @Test func decodesEveryFieldAndIgnoresUnknownKeys() throws {
        let document = try AppStatusDocument.decode(Self.full)

        #expect(document.minimumVersion?.ios == "1.2.0")
        #expect(document.maintenance?.startsAt == Self.starts)
        #expect(document.maintenance?.endsAt == Self.ends)
        #expect(document.maintenance?.message?.text(for: "ja") == "移行のため")
        #expect(document.notice?.id == "2026-12-terms")
        #expect(document.notice?.message.text(for: "fr") == "Terms updated")
    }

    @Test func nullOrMissingSectionsMeanNothing() throws {
        let nulls = try AppStatusDocument.decode(Data(#"{"minimum_version":{"ios":"1.0.0"},"maintenance":null}"#.utf8))
        #expect(nulls.maintenance == nil)
        #expect(nulls.notice == nil)
    }

    @Test func comparesVersionsNumerically() {
        #expect(AppVersion.isOlder("1.9.0", than: "1.10.0"))
        #expect(!AppVersion.isOlder("1.10.0", than: "1.9.0"))
        #expect(!AppVersion.isOlder("1.0", than: "1.0.0"))
        #expect(AppVersion.isOlder("1.0", than: "1.0.1"))
        #expect(!AppVersion.isOlder("2.0", than: "1.99.99"))
    }

    @Test func phaseFollowsTheWindow() throws {
        let maintenance = try #require(try AppStatusDocument.decode(Self.full).maintenance)

        #expect(MaintenancePhase(maintenance, at: Self.starts.addingTimeInterval(-1)) == .upcoming(maintenance))
        #expect(MaintenancePhase(maintenance, at: Self.starts) == .active(maintenance))
        #expect(MaintenancePhase(maintenance, at: Self.ends) == .none)
        #expect(MaintenancePhase(nil, at: Self.starts) == .none)
    }

    @Test func theGateClosesOnlyInsideTheWindow() throws {
        let gate = MaintenanceGate()
        gate.update(try AppStatusDocument.decode(Self.full).maintenance)

        #expect(!gate.isClosed(at: Self.starts.addingTimeInterval(-1)))
        #expect(gate.isClosed(at: Self.starts))
        #expect(!gate.isClosed(at: Self.ends))
    }

    @Test func aFetchedStatusIsKeptForWhenTheNextFetchFails() async throws {
        let defaults = try makeDefaults()
        let first = makeStore(defaults: defaults, version: "1.0") { _ in Self.full }
        await first.refresh()
        #expect(first.requiresUpdate)

        let second = makeStore(defaults: defaults, version: "1.0") { _ in throw URLError(.notConnectedToInternet) }
        await second.refresh()

        #expect(second.requiresUpdate)
        #expect(second.gate.isClosed(at: Self.starts))
    }

    @Test func withoutAnyStatusNothingIsRestricted() async throws {
        let store = makeStore(defaults: try makeDefaults(), version: "1.0") { _ in throw URLError(.timedOut) }

        await store.refresh()

        #expect(!store.requiresUpdate)
        #expect(store.maintenancePhase == .none)
        #expect(store.pendingNotice == nil)
    }

    @Test func theUpcomingBannerStaysDismissedUntilTheWindowChanges() async throws {
        let defaults = try makeDefaults()
        let store = makeStore(defaults: defaults, version: "1.2.0", now: Self.starts.addingTimeInterval(-60)) { _ in
            Self.full
        }
        await store.refresh()
        #expect(store.showsUpcomingBanner)

        store.dismissUpcomingBanner()
        #expect(!store.showsUpcomingBanner)

        let moved = Data(Self.fullJSON.replacingOccurrences(of: "17:00:00Z", with: "18:00:00Z").utf8)
        let reloaded = makeStore(defaults: defaults, version: "1.2.0", now: Self.starts.addingTimeInterval(-60)) { _ in
            moved
        }
        await reloaded.refresh()
        #expect(reloaded.showsUpcomingBanner)
    }

    @Test func aNoticeIsShownOnceAndOnlyUntilItsDate() async throws {
        let defaults = try makeDefaults()
        let store = makeStore(defaults: defaults, version: "1.2.0", now: Self.starts) { _ in Self.full }
        await store.refresh()
        let notice = try #require(store.pendingNotice)

        store.markNoticeShown(notice)
        #expect(store.pendingNotice == nil)

        let fresh = try makeDefaults()
        let late = makeStore(defaults: fresh, version: "1.2.0", now: notice.until) { _ in Self.full }
        await late.refresh()
        #expect(late.pendingNotice == nil)
    }

    @Test func theEndShowsOnlyTheTimeWhenTheWindowStaysWithinOneDay() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try #require(TimeZone(identifier: "Asia/Tokyo"))
        var timeOnly = Date.FormatStyle(date: .omitted, time: .shortened)
        timeOnly.timeZone = calendar.timeZone
        var withDate = Date.FormatStyle(date: .abbreviated, time: .shortened)
        withDate.timeZone = calendar.timeZone
        let sameDay = AppStatusDocument.Maintenance(startsAt: Self.starts, endsAt: Self.ends, message: nil)
        let overnight = AppStatusDocument.Maintenance(
            startsAt: Self.starts,
            endsAt: Self.ends.addingTimeInterval(86_400),
            message: nil
        )

        #expect(MaintenanceText.end(of: sameDay, calendar: calendar) == Self.ends.formatted(timeOnly))
        #expect(MaintenanceText.end(of: overnight, calendar: calendar) == overnight.endsAt.formatted(withDate))
    }

    @Test func onlyAnActiveWindowCountsAsActive() async throws {
        let earlier = Self.starts.addingTimeInterval(-1)
        let before = makeStore(defaults: try makeDefaults(), version: "1.2.0", now: earlier) { _ in Self.full }
        await before.refresh()
        #expect(before.activeMaintenance == nil)

        let during = makeStore(defaults: try makeDefaults(), version: "1.2.0", now: Self.starts) { _ in Self.full }
        await during.refresh()
        #expect(during.activeMaintenance?.endsAt == Self.ends)
    }

    @Test(.timeLimit(.minutes(1)))
    func syncResumesWhenTheWindowEnds() async throws {
        let clock = Mutex(Self.ends.addingTimeInterval(-0.2))
        let store = AppStatusStore(
            url: URL(string: "https://locatedo.com/app-status-stg.json"),
            currentVersion: "1.2.0",
            gate: MaintenanceGate(),
            defaults: try makeDefaults(),
            clock: { clock.withLock { $0 } },
            fetch: { _ in Self.full }
        )
        await store.refresh()
        #expect(store.maintenancePhase != .none)

        clock.withLock { $0 = Self.ends }
        await withCheckedContinuation { continuation in
            store.onMaintenanceEnded = {
                continuation.resume()
            }
        }

        #expect(store.maintenancePhase == .none)
    }

    private func makeStore(
        defaults: UserDefaults,
        version: String,
        now: Date = Date(timeIntervalSince1970: 1_790_000_000),
        fetch: @escaping (URL) async throws -> Data
    ) -> AppStatusStore {
        AppStatusStore(
            url: URL(string: "https://locatedo.com/app-status-stg.json"),
            currentVersion: version,
            gate: MaintenanceGate(),
            defaults: defaults,
            clock: { now },
            fetch: fetch
        )
    }

    private func makeDefaults() throws -> UserDefaults {
        let suite = "AppStatusTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defaults.removePersistentDomain(forName: suite)
        return defaults
    }
}
