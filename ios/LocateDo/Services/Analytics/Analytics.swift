import FirebaseAnalytics
import FirebaseCore
import Foundation

enum AnalyticsEvent: String {
    case arrivalNotified = "arrival_notified"
    case arrivalOpened = "arrival_opened"
    case shareTapped = "share_tapped"
    case inviteAccepted = "invite_accepted"
}

nonisolated enum Analytics {
    static func configure() {
        guard let configuration = Bundle.main.object(forInfoDictionaryKey: "LocateDoConfiguration") as? String,
              let path = Bundle.main.path(forResource: "GoogleService-Info-\(configuration)", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path) else {
            return
        }
        FirebaseApp.configure(options: options)
    }

    static func log(_ event: AnalyticsEvent, parameters: [String: Any]? = nil) {
        guard FirebaseApp.app() != nil else {
            return
        }
        FirebaseAnalytics.Analytics.logEvent(event.rawValue, parameters: parameters)
    }
}
