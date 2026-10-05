import Foundation
import Observation
import OSLog

@Observable
final class AppStatusStore {
    private enum Key {
        static let document = "appStatus.document"
        static let dismissedMaintenance = "appStatus.dismissedMaintenance"
        static let shownNotices = "appStatus.shownNotices"
    }

    private(set) var document: AppStatusDocument?
    private(set) var now: Date
    private(set) var dismissedMaintenance: String?
    private(set) var shownNotices: [String]

    @ObservationIgnored var onMaintenanceEnded: () -> Void = {}
    @ObservationIgnored let gate: MaintenanceGate

    private let url: URL?
    private let currentVersion: String
    private let defaults: UserDefaults
    private let clock: () -> Date
    private let fetch: (URL) async throws -> Data
    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "appStatus")
    @ObservationIgnored private var boundary: Task<Void, Never>?

    init(
        url: URL?,
        currentVersion: String,
        gate: MaintenanceGate,
        defaults: UserDefaults = .standard,
        clock: @escaping () -> Date = { .now },
        fetch: @escaping (URL) async throws -> Data = AppStatusStore.download
    ) {
        self.url = url
        self.currentVersion = currentVersion
        self.gate = gate
        self.defaults = defaults
        self.clock = clock
        self.fetch = fetch
        now = clock()
        dismissedMaintenance = defaults.string(forKey: Key.dismissedMaintenance)
        shownNotices = defaults.stringArray(forKey: Key.shownNotices) ?? []
        if let data = defaults.data(forKey: Key.document) {
            document = try? AppStatusDocument.decode(data)
        }
        apply()
    }

    var requiresUpdate: Bool {
        guard let minimum = document?.minimumVersion?.ios else {
            return false
        }
        return AppVersion.isOlder(currentVersion, than: minimum)
    }

    var maintenancePhase: MaintenancePhase {
        MaintenancePhase(document?.maintenance, at: now)
    }

    var activeMaintenance: AppStatusDocument.Maintenance? {
        guard case .active(let maintenance) = maintenancePhase else {
            return nil
        }
        return maintenance
    }

    var showsUpcomingBanner: Bool {
        guard case .upcoming(let maintenance) = maintenancePhase else {
            return false
        }
        return dismissedMaintenance != maintenance.key
    }

    var pendingNotice: AppStatusDocument.Notice? {
        guard let notice = document?.notice, now < notice.until, !shownNotices.contains(notice.id) else {
            return nil
        }
        return notice
    }

    func refresh() async {
        guard let url else {
            return
        }
        do {
            let data = try await fetch(url)
            document = try AppStatusDocument.decode(data)
            defaults.set(data, forKey: Key.document)
        } catch {
            logger.notice("Could not read the app status: \(error, privacy: .public)")
        }
        apply()
    }

    func dismissUpcomingBanner() {
        guard case .upcoming(let maintenance) = maintenancePhase else {
            return
        }
        dismissedMaintenance = maintenance.key
        defaults.set(maintenance.key, forKey: Key.dismissedMaintenance)
    }

    func markNoticeShown(_ notice: AppStatusDocument.Notice) {
        shownNotices.append(notice.id)
        defaults.set(shownNotices, forKey: Key.shownNotices)
    }

    private func apply() {
        now = clock()
        gate.update(document?.maintenance)
        boundary?.cancel()
        guard let next = nextBoundary() else {
            return
        }
        let delay = next.timeIntervalSince(now)
        boundary = Task { [weak self] in
            do {
                try await Task.sleep(for: .seconds(delay))
            } catch {
                return
            }
            self?.crossBoundary()
        }
    }

    private func crossBoundary() {
        var wasActive = false
        if case .active = maintenancePhase {
            wasActive = true
        }
        apply()
        if wasActive && maintenancePhase == .none {
            onMaintenanceEnded()
        }
    }

    private func nextBoundary() -> Date? {
        guard let maintenance = document?.maintenance else {
            return nil
        }
        return [maintenance.startsAt, maintenance.endsAt].filter { $0 > now }.min()
    }

    nonisolated static func download(_ url: URL) async throws -> Data {
        var request = URLRequest(url: url)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 10
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
        return data
    }
}
