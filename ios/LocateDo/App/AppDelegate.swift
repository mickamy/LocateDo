import OSLog
import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate {
    static var onDeviceToken: (Data) async -> Void = { _ in }
    static var onRemoteNotification: () async -> Void = {}

    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "push")

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        application.registerForRemoteNotifications()
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Task {
            await Self.onDeviceToken(deviceToken)
        }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: any Error) {
        logger.error("Remote notification registration failed: \(error, privacy: .public)")
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any]
    ) async -> UIBackgroundFetchResult {
        await Self.onRemoteNotification()
        return .newData
    }
}
