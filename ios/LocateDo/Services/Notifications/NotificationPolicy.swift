import Foundation

nonisolated enum NotificationPolicy {
    enum Suppression: String {
        case noOpenTodos = "no_open_todos"
        case assignedToOthers = "assigned_to_others"
        case recentlyNotified = "recently_notified"
        case notificationsOff = "notifications_off"
    }

    static let cooldown: TimeInterval = 30 * 60
    static let maxTitles = 3

    static func suppression(
        openTodoCount: Int,
        notifiableTodoCount: Int,
        lastNotifiedAt: Date?,
        notificationsAllowed: Bool,
        now: Date
    ) -> Suppression? {
        guard openTodoCount > 0 else {
            return .noOpenTodos
        }
        guard notifiableTodoCount > 0 else {
            return .assignedToOthers
        }
        if let lastNotifiedAt, now.timeIntervalSince(lastNotifiedAt) < cooldown {
            return .recentlyNotified
        }
        guard notificationsAllowed else {
            return .notificationsOff
        }
        return nil
    }

    static func notifiableTodos(_ todos: [Todo], for userID: UUID?) -> [Todo] {
        guard let userID else {
            return todos
        }
        return todos.filter { $0.assigneeID == nil || $0.assigneeID == userID }
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
