import FirebaseAnalytics
import FirebaseCore
import Foundation
import Synchronization

typealias AnalyticsParameters = [AnalyticsParameter: any AnalyticsValue]

// GA4 has no boolean type, so flags go out as 0 / 1.
nonisolated protocol AnalyticsValue: Sendable {
    var firebaseValue: Any { get }
}

nonisolated extension Int: AnalyticsValue {
    var firebaseValue: Any { self }
}

nonisolated extension Double: AnalyticsValue {
    var firebaseValue: Any { self }
}

nonisolated extension String: AnalyticsValue {
    var firebaseValue: Any { self }
}

nonisolated extension Bool: AnalyticsValue {
    var firebaseValue: Any {
        if self {
            return 1
        }
        return 0
    }
}

nonisolated enum Analytics {
    private static let queue = Mutex(AnalyticsQueue())
    private static let configuration = Bundle.main.object(forInfoDictionaryKey: "LocateDoConfiguration") as? String

    // Collection starts off from Info.plist; apply(_:) turns it on once the answer is known.
    static func configure() {
        guard let configuration,
              let path = Bundle.main.path(forResource: "GoogleService-Info-\(configuration)", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path) else {
            return
        }
        FirebaseApp.configure(options: options)
        // The exported app version is only the marketing version, so builds of the same version look alike.
        setUserProperty(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String, for: .appBuild)
    }

    // nil while the answer is not known yet: nothing is sent, and what is logged waits in memory.
    static func apply(_ decision: Bool?) {
        guard FirebaseApp.app() != nil else {
            return
        }
        let isSending = decision == true
        var status = ConsentStatus.denied
        if isSending {
            status = .granted
        }
        FirebaseAnalytics.Analytics.setConsent([.analyticsStorage: status])
        FirebaseAnalytics.Analytics.setAnalyticsCollectionEnabled(isSending)
        let held = queue.withLock { $0.decide(decision) }
        for entry in held {
            deliver(entry)
        }
        CrashReporting.apply(decision, configuration: configuration, appInstanceID: appInstanceID())
    }

    static func log(_ event: AnalyticsEvent, parameters: AnalyticsParameters = [:]) {
        send(.event(name: event.rawValue, parameters: wireParameters(parameters)))
    }

    static func logScreen(_ screen: AnalyticsScreen, parameters: AnalyticsParameters = [:]) {
        var values = wireParameters(parameters)
        values[AnalyticsParameterScreenName] = screen.rawValue
        // Left out, Firebase fills in the SwiftUI hosting controller class, which is over its 100-character limit.
        values[AnalyticsParameterScreenClass] = screen.rawValue
        send(.event(name: AnalyticsEventScreenView, parameters: values))
    }

    // Only while sending, so the Support ID and RevenueCat never carry it otherwise.
    static func appInstanceID() -> String? {
        guard FirebaseApp.app() != nil, queue.withLock({ $0.mode == .sending }) else {
            return nil
        }
        return FirebaseAnalytics.Analytics.appInstanceID()
    }

    static func setUserProperty(_ value: String?, for property: AnalyticsUserProperty) {
        send(.userProperty(name: property.rawValue, value: value))
    }

    static func wireParameters(_ parameters: AnalyticsParameters) -> [String: any AnalyticsValue] {
        var values: [String: any AnalyticsValue] = [:]
        for (key, value) in parameters {
            values[key.rawValue] = value
        }
        return values
    }

    static func firebaseParameters(_ parameters: AnalyticsParameters) -> [String: Any]? {
        firebaseValues(wireParameters(parameters))
    }

    private static func firebaseValues(_ values: [String: any AnalyticsValue]) -> [String: Any]? {
        if values.isEmpty {
            return nil
        }
        return values.mapValues(\.firebaseValue)
    }

    private static func send(_ entry: AnalyticsQueue.Entry) {
        guard FirebaseApp.app() != nil else {
            return
        }
        if queue.withLock({ $0.submit(entry) }) {
            deliver(entry)
        }
    }

    private static func deliver(_ entry: AnalyticsQueue.Entry) {
        switch entry {
        case let .event(name, parameters):
            FirebaseAnalytics.Analytics.logEvent(name, parameters: firebaseValues(parameters))
        case let .userProperty(name, value):
            FirebaseAnalytics.Analytics.setUserProperty(value, forName: name)
        }
    }
}
