import FirebaseCore
import FirebaseCrashlytics
import Foundation

nonisolated enum CrashReporting {
    // Debug builds carry no dSYM, so their reports could not be symbolicated anyway.
    static func isCollectionEnabled(for configuration: String) -> Bool {
        configuration != "Debug"
    }

    // The app instance ID is the Support ID in support mail, so reports can be found from it.
    static func configure(configuration: String, appInstanceID: String?) {
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(isCollectionEnabled(for: configuration))
        if let appInstanceID {
            Crashlytics.crashlytics().setUserID(appInstanceID)
        }
    }

    static func record(_ error: any Error, site: String) {
        guard FirebaseApp.app() != nil else {
            return
        }
        Crashlytics.crashlytics().record(error: error, userInfo: ["site": site])
    }
}
