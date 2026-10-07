import Observation
import OSLog
import UserNotifications

@Observable
final class ArrivalNotifier: NSObject, UNUserNotificationCenterDelegate {
    private(set) var authorizationStatus: UNAuthorizationStatus = .notDetermined
    @ObservationIgnored var onOpened: (UUID) -> Void = { _ in }
    @ObservationIgnored private(set) var lastOpenedAt: Date?
    @ObservationIgnored var onCampaignLink: (URL) -> Void = { _ in }
    @ObservationIgnored var onCheckOff: ([UUID]) async -> Void = { _ in }
    @ObservationIgnored var selection = ArrivalSelection.shared()

    private let router: AppRouter
    private let center = UNUserNotificationCenter.current()
    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "notifications")
    private nonisolated static let placeIDKey = "placeID"

    init(router: AppRouter) {
        self.router = router
        super.init()
        center.delegate = self
        center.setNotificationCategories([
            UNNotificationCategory(
                identifier: ArrivalChecklist.category,
                actions: [
                    UNNotificationAction(
                        identifier: ArrivalChecklist.checkOffAction,
                        title: String(localized: .notificationCheckOffChecked)
                    )
                ],
                intentIdentifiers: []
            )
        ])
    }

    func refreshAuthorizationStatus() async {
        authorizationStatus = await center.notificationSettings().authorizationStatus
    }

    func requestAuthorization() async {
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
        await refreshAuthorizationStatus()
    }

    func notifyArrival(at place: Place, todos: [Todo], after delay: TimeInterval? = nil) async {
        let checklist = ArrivalChecklist(
            items: todos.map { ArrivalChecklist.Item(id: $0.id, title: $0.title) },
            categoryIcon: place.category?.icon,
            categoryColor: place.category?.color
        )
        let content = UNMutableNotificationContent()
        content.title = String(localized: .notificationArrivedTitle(place.name))
        // Actions only show on press and hold, so every arrival notification says so.
        content.body = NotificationPolicy.body(todoTitles: todos.map(\.title))
            + "\n" + String(localized: .notificationPressAndHoldHint)
        content.sound = .default
        content.interruptionLevel = .timeSensitive
        content.threadIdentifier = place.id.uuidString
        content.categoryIdentifier = ArrivalChecklist.category
        var userInfo = checklist.userInfo
        userInfo[Self.placeIDKey] = place.id.uuidString
        content.userInfo = userInfo
        var trigger: UNNotificationTrigger?
        if let delay {
            trigger = UNTimeIntervalNotificationTrigger(timeInterval: delay, repeats: false)
        }
        let request = UNNotificationRequest(
            identifier: "arrival-\(place.id.uuidString)",
            content: content,
            trigger: trigger
        )
        do {
            try await center.add(request)
        } catch {
            logger.error("Could not schedule the arrival notification: \(error, privacy: .public)")
            CrashReporting.record(error, site: "notifications.arrival")
        }
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .list])
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        let latency = max(Int(Date().timeIntervalSince(response.notification.date)), 0)
        if response.actionIdentifier == ArrivalChecklist.checkOffAction {
            let requestID = response.notification.request.identifier
            Task { @MainActor in
                let todoIDs = selection?.take(for: requestID) ?? []
                if !todoIDs.isEmpty {
                    await onCheckOff(todoIDs)
                }
            }
        } else if userInfo["type"] as? String == "completion" {
            var count = 1
            if let sent = userInfo["count"] as? Int {
                count = sent
            }
            Analytics.log(.completionNoticeOpened, parameters: [.count: count])
            Task { @MainActor in
                router.selectedTab = .todos
            }
        } else if let campaign = CampaignNotification(userInfo: userInfo) {
            Analytics.log(.campaignOpened, parameters: [
                .campaignID: campaign.id,
                .latencyS: latency,
                .hasURL: campaign.url != nil
            ])
            if let url = campaign.url {
                Task { @MainActor in
                    onCampaignLink(url)
                }
            }
        } else if let raw = userInfo[Self.placeIDKey] as? String, let placeID = UUID(uuidString: raw) {
            Analytics.log(.arrivalOpened, parameters: [.latencyS: latency])
            Task { @MainActor in
                lastOpenedAt = .now
                onOpened(placeID)
                router.open(placeID: placeID)
            }
        }
        completionHandler()
    }
}
