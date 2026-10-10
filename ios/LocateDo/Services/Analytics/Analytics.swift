import FirebaseAnalytics
import FirebaseCore
import Foundation
import Synchronization

nonisolated enum AnalyticsEvent: String {
    case alwaysPromptAnswered = "always_prompt_answered"
    case analyticsConsentGranted = "analytics_consent_granted"
    case arrivalNotified = "arrival_notified"
    case arrivalOpened = "arrival_opened"
    case arrivalSuppressed = "arrival_suppressed"
    case campaignOpened = "campaign_opened"
    case completionNoticeOpened = "completion_notice_opened"
    case completionNoticesChanged = "completion_notices_changed"
    case dailyState = "daily_state"
    case limitReached = "limit_reached"
    case locationAuthChanged = "location_auth_changed"
    case promotionsConsentChanged = "promotions_consent_changed"
    case promotionsPromptAnswered = "promotions_prompt_answered"
    case promotionsPromptShown = "promotions_prompt_shown"
    case notificationAuthChanged = "notification_auth_changed"
    case onboardingCompleted = "onboarding_completed"
    case permissionActionTapped = "permission_action_tapped"
    case permissionBannerTapped = "permission_banner_tapped"
    case placeAdded = "place_added"
    case placeDeleted = "place_deleted"
    case todoAdded = "todo_added"
    case todoCompleted = "todo_completed"
    case todoDeleted = "todo_deleted"
    case todoDeleteUndone = "todo_delete_undone"
    case shareTapped = "share_tapped"
    case inviteAccepted = "invite_accepted"
    case paywallShown = "paywall_shown"
    case paywallPurchased = "paywall_purchased"
    case paywallDismissed = "paywall_dismissed"
    case purchaseStarted = "purchase_started"
    case purchaseCancelled = "purchase_cancelled"
    case purchaseFailed = "purchase_failed"
    case restoreCompleted = "restore_completed"
}

nonisolated enum AnalyticsParameter: String {
    case action
    case ageDays = "age_days"
    case ageHours = "age_hours"
    case assigned
    case campaignID = "campaign_id"
    case category
    case completedTodoCount7d = "completed_todo_count_7d"
    case count
    case customCategoryCount = "custom_category_count"
    case daysSinceInstall = "days_since_install"
    case durationS = "duration_s"
    case from
    case hasURL = "has_url"
    case householdMembers = "household_members"
    case kind
    case latencyS = "latency_s"
    case locationAuth = "location_auth"
    case missing
    case promotionsConsent = "promotions_consent"
    case mode
    case notificationAuth = "notification_auth"
    case openTodoCount = "open_todo_count"
    case openTodos = "open_todos"
    case placeCount = "place_count"
    case placeOpenTodos = "place_open_todos"
    case placesWithOpenTodos = "places_with_open_todos"
    case plan
    case preciseLocation = "precise_location"
    case radiusM = "radius_m"
    case reason
    case result
    case shownCount = "shown_count"
    case signedIn = "signed_in"
    case source
    case step
    case to
    case trigger
    case via
    case watchAppInstalled = "watch_app_installed"
}

nonisolated enum AnalyticsUserProperty: String {
    case appBuild = "app_build"
    case householdMembers = "household_members"
    case locationAuth = "location_auth"
    case promotionsConsent = "promotions_consent"
    case openTodoCount = "open_todo_count"
    case placeCount = "place_count"
    case plan
    case signedIn = "signed_in"
}

nonisolated enum AnalyticsScreen: String {
    case account
    case accountBenefits = "account_benefits"
    case acceptInvite = "accept_invite"
    case alwaysLocationPrompt = "always_location_prompt"
    case categories
    case categoryEditor = "category_editor"
    case home
    case map
    case onboarding
    case paywall
    case placeDetail = "place_detail"
    case placeEditor = "place_editor"
    case placePicker = "place_picker"
    case settings
    case sharing
    case sharingIntro = "sharing_intro"
    case todoEditor = "todo_editor"
    case todos
    case updateRequired = "update_required"
}

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
