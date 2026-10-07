import SwiftUI
import UIKit
import UserNotifications
import UserNotificationsUI

final class NotificationViewController: UIViewController, UNNotificationContentExtension {
    private let selection = ChecklistSelection()
    private let arrivalSelection = ArrivalSelection.shared()
    private var requestID: String?

    override func viewDidLoad() {
        super.viewDidLoad()
        selection.onChange = { [weak self] ids in
            self?.keep(ids)
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
        requestID = notification.request.identifier
        // A checklist left open earlier for the same place must not carry over.
        keep([])
        selection.placeName = content.title
        guard let checklist = ArrivalChecklist(userInfo: content.userInfo) else {
            return
        }
        selection.items = checklist.items
        selection.systemImage = CategoryAppearance.systemImage(forIcon: checklist.categoryIcon)
        selection.tint = CategoryAppearance.tint(forColor: checklist.categoryColor)
    }

    // Every action, including the check-off one, goes on to the app, which does the writing.
    func didReceive(
        _ response: UNNotificationResponse,
        completionHandler completion: @escaping (UNNotificationContentExtensionResponseOption) -> Void
    ) {
        completion(.dismissAndForwardAction)
    }

    private func keep(_ ids: [UUID]) {
        guard let requestID else {
            return
        }
        arrivalSelection?.save(ids, for: requestID)
    }
}
