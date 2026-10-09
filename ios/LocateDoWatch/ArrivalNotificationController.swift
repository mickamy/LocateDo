import SwiftUI
import UserNotifications
import WatchKit

final class ArrivalNotificationController: WKUserNotificationHostingController<ArrivalNotificationView> {
    private var title = ""
    private var checklist: ArrivalChecklist?

    override static var isInteractive: Bool {
        true
    }

    override var body: ArrivalNotificationView {
        ArrivalNotificationView(title: title, checklist: checklist) { id in
            WatchStore.shared.checkOff([id], source: .notification)
        }
    }

    override func didReceive(_ notification: UNNotification) {
        let content = notification.request.content
        title = content.title
        checklist = ArrivalChecklist(userInfo: content.userInfo)
        // The iPhone's "Mark checked as done" relies on its content extension, which never runs here.
        notificationActions = []
    }
}

struct ArrivalNotificationView: View {
    let title: String
    let checklist: ArrivalChecklist?
    let onCheck: (UUID) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                CategoryBadge(icon: checklist?.categoryIcon, color: checklist?.categoryColor)
                Text(title)
                    .font(.headline)
                    .lineLimit(3)
            }
            WatchChecklist(todos: todos, onCheck: onCheck)
                .buttonStyle(.bordered)
        }
    }

    private var todos: [WatchSnapshot.Todo] {
        guard let checklist else {
            return []
        }
        return checklist.items.map { WatchSnapshot.Todo(id: $0.id, title: $0.title) }
    }
}
