import Foundation

nonisolated enum NotificationPolicy {
    static let cooldown: TimeInterval = 30 * 60
    static let maxTitles = 3

    static func shouldNotify(openTodoCount: Int, lastNotifiedAt: Date?, now: Date) -> Bool {
        guard openTodoCount > 0 else {
            return false
        }
        guard let lastNotifiedAt else {
            return true
        }
        return now.timeIntervalSince(lastNotifiedAt) >= cooldown
    }

    static func body(todoTitles: [String]) -> String {
        let shown = Array(todoTitles.prefix(maxTitles)).formatted(.list(type: .and, width: .narrow))
        let rest = todoTitles.count - maxTitles
        guard rest > 0 else {
            return shown
        }
        return shown + "\n" + String(localized: .notificationMore(rest))
    }
}
