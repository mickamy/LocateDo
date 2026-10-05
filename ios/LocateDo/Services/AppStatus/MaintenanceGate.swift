import Foundation
import Synchronization

nonisolated final class MaintenanceGate: Sendable {
    private let window = Mutex<DateInterval?>(nil)

    func update(_ maintenance: AppStatusDocument.Maintenance?) {
        var interval: DateInterval?
        if let maintenance, maintenance.startsAt < maintenance.endsAt {
            interval = DateInterval(start: maintenance.startsAt, end: maintenance.endsAt)
        }
        window.withLock { $0 = interval }
    }

    func isClosed(at now: Date = .now) -> Bool {
        window.withLock { interval in
            guard let interval else {
                return false
            }
            return interval.start <= now && now < interval.end
        }
    }
}
