import Foundation

nonisolated enum CompletionVia: String {
    case notification
    case app
    case action
}

// A to-do checked off at the notified place soon after opening the arrival notification counts as done through it.
nonisolated struct ArrivalOpen: Equatable {
    static let window: TimeInterval = 30 * 60

    let placeID: UUID
    let openedAt: Date

    func via(completingAt placeID: UUID?, now: Date) -> CompletionVia {
        guard placeID == self.placeID else {
            return .app
        }
        let elapsed = now.timeIntervalSince(openedAt)
        if elapsed >= 0 && elapsed <= Self.window {
            return .notification
        }
        return .app
    }
}
