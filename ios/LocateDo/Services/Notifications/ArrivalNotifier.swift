import Observation
import OSLog
import UserNotifications

@Observable
final class ArrivalNotifier: NSObject, UNUserNotificationCenterDelegate {
    private(set) var authorizationStatus: UNAuthorizationStatus = .notDetermined
    @ObservationIgnored var onOpened: (UUID) -> Void = { _ in }

    private let router: AppRouter
    private let center = UNUserNotificationCenter.current()
    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "notifications")
    private nonisolated static let placeIDKey = "placeID"

    init(router: AppRouter) {
        self.router = router
        super.init()
        center.delegate = self
    }

    func refreshAuthorizationStatus() async {
        authorizationStatus = await center.notificationSettings().authorizationStatus
    }

    func requestAuthorization() async {
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
        await refreshAuthorizationStatus()
    }

    func notifyArrival(at place: Place, todoTitles: [String], after delay: TimeInterval? = nil) async {
        let content = UNMutableNotificationContent()
        content.title = String(localized: .notificationArrivedTitle(place.name))
        content.body = NotificationPolicy.body(todoTitles: todoTitles)
        content.sound = .default
        content.interruptionLevel = .timeSensitive
        content.threadIdentifier = place.id.uuidString
        content.userInfo = [Self.placeIDKey: place.id.uuidString]
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
        if let raw = response.notification.request.content.userInfo[Self.placeIDKey] as? String,
           let placeID = UUID(uuidString: raw) {
            let latency = Date().timeIntervalSince(response.notification.date)
            Analytics.log(.arrivalOpened, parameters: [.latencyS: max(Int(latency), 0)])
            Task { @MainActor in
                onOpened(placeID)
                router.open(placeID: placeID)
            }
        }
        completionHandler()
    }
}
