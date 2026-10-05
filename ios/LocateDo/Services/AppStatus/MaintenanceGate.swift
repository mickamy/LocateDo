import Foundation
import Synchronization

nonisolated final class MaintenanceGate: Sendable {
    private struct State {
        var window: DateInterval?
        var requiresUpdate = false
    }

    private let state = Mutex(State())

    func update(_ maintenance: AppStatusDocument.Maintenance?, requiresUpdate: Bool = false) {
        var window: DateInterval?
        if let maintenance, maintenance.startsAt < maintenance.endsAt {
            window = DateInterval(start: maintenance.startsAt, end: maintenance.endsAt)
        }
        state.withLock { $0 = State(window: window, requiresUpdate: requiresUpdate) }
    }

    func isClosed(at now: Date = .now) -> Bool {
        state.withLock { state in
            if state.requiresUpdate {
                return true
            }
            guard let window = state.window else {
                return false
            }
            return window.start <= now && now < window.end
        }
    }
}
