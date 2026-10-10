import FirebaseCore
import FirebaseCrashlytics
import Foundation

nonisolated enum CrashReporting {
    // Debug builds carry no dSYM, so their reports could not be symbolicated anyway.
    static func isCollectionEnabled(for configuration: String) -> Bool {
        configuration != "Debug"
    }

    // Reports from before the answer stay on the device until it is known.
    static func apply(_ decision: Bool?, configuration: String?, appInstanceID: String?) {
        let crashlytics = Crashlytics.crashlytics()
        guard let decision else {
            crashlytics.setCrashlyticsCollectionEnabled(false)
            return
        }
        guard decision, let configuration, isCollectionEnabled(for: configuration) else {
            crashlytics.setCrashlyticsCollectionEnabled(false)
            crashlytics.deleteUnsentReports()
            return
        }
        crashlytics.sendUnsentReports()
        crashlytics.setCrashlyticsCollectionEnabled(true)
        // The app instance ID is the Support ID in support mail, so reports can be found from it.
        if let appInstanceID {
            crashlytics.setUserID(appInstanceID)
        }
    }

    static func record(_ error: any Error, site: String) {
        guard FirebaseApp.app() != nil else {
            return
        }
        Crashlytics.crashlytics().record(error: error, userInfo: ["site": site])
    }
}
