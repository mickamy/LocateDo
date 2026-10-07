import SwiftUI
import UIKit
import UserNotifications
import UserNotificationsUI

final class NotificationViewController: UIViewController, UNNotificationContentExtension {
    private let selection = ChecklistSelection()

    override func viewDidLoad() {
        super.viewDidLoad()
        selection.onChange = { [weak self] ids in
            self?.offerCheckOff(ids)
        }
        let host = UIHostingController(rootView: ChecklistView(selection: selection))
        host.sizingOptions = .preferredContentSize
        host.view.backgroundColor = .clear
        host.view.translatesAutoresizingMaskIntoConstraints = false
        addChild(host)
        view.addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.topAnchor.constraint(equalTo: view.topAnchor),
            host.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            host.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        host.didMove(toParent: self)
    }

    override func preferredContentSizeDidChange(forChildContentContainer container: any UIContentContainer) {
        super.preferredContentSizeDidChange(forChildContentContainer: container)
        preferredContentSize = CGSize(width: view.bounds.width, height: container.preferredContentSize.height)
    }

    func didReceive(_ notification: UNNotification) {
        let content = notification.request.content
        selection.placeName = content.title
        selection.items = ArrivalChecklist(userInfo: content.userInfo)?.items ?? []
    }

    // Every action, including the check-off one, goes on to the app, which does the writing.
    func didReceive(
        _ response: UNNotificationResponse,
        completionHandler completion: @escaping (UNNotificationContentExtensionResponseOption) -> Void
    ) {
        completion(.dismissAndForwardAction)
    }

    private func offerCheckOff(_ ids: [UUID]) {
        guard !ids.isEmpty else {
            extensionContext?.notificationActions = []
            return
        }
        extensionContext?.notificationActions = [
            UNNotificationAction(
                identifier: ArrivalChecklist.actionIdentifier(checking: ids),
                title: String(localized: .notificationCheckOff(ids.count))
            )
        ]
    }
}
