import FirebaseAnalytics
import FirebaseCore
import Foundation

nonisolated enum AnalyticsEvent: String {
    case arrivalNotified = "arrival_notified"
    case arrivalOpened = "arrival_opened"
    case dailyState = "daily_state"
    case limitReached = "limit_reached"
    case locationAuthChanged = "location_auth_changed"
    case onboardingCompleted = "onboarding_completed"
    case placeAdded = "place_added"
    case placeDeleted = "place_deleted"
    case todoAdded = "todo_added"
    case todoCompleted = "todo_completed"
    case shareTapped = "share_tapped"
    case inviteAccepted = "invite_accepted"
    case paywallShown = "paywall_shown"
    case paywallPurchased = "paywall_purchased"
}

nonisolated enum AnalyticsParameter: String {
    case ageDays = "age_days"
    case ageHours = "age_hours"
    case assigned
    case category
    case completedTodoCount7d = "completed_todo_count_7d"
    case customCategoryCount = "custom_category_count"
    case daysSinceInstall = "days_since_install"
    case durationS = "duration_s"
    case from
    case householdMembers = "household_members"
    case kind
    case latencyS = "latency_s"
    case locationAuth = "location_auth"
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
    case signedIn = "signed_in"
    case source
    case step
    case to
    case trigger
    case via
}

nonisolated enum AnalyticsUserProperty: String {
    case householdMembers = "household_members"
    case locationAuth = "location_auth"
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
    static func configure() {
        guard let configuration = Bundle.main.object(forInfoDictionaryKey: "LocateDoConfiguration") as? String,
              let path = Bundle.main.path(forResource: "GoogleService-Info-\(configuration)", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path) else {
            return
        }
        FirebaseApp.configure(options: options)
    }

    static func log(_ event: AnalyticsEvent, parameters: AnalyticsParameters = [:]) {
        send(event.rawValue, parameters: firebaseParameters(parameters))
    }

    static func logScreen(_ screen: AnalyticsScreen, parameters: AnalyticsParameters = [:]) {
        var values = firebaseParameters(parameters) ?? [:]
        values[AnalyticsParameterScreenName] = screen.rawValue
        // Left out, Firebase fills in the SwiftUI hosting controller class, which is over its 100-character limit.
        values[AnalyticsParameterScreenClass] = screen.rawValue
        send(AnalyticsEventScreenView, parameters: values)
    }

    static func setUserProperty(_ value: String?, for property: AnalyticsUserProperty) {
        guard FirebaseApp.app() != nil else {
            return
        }
        FirebaseAnalytics.Analytics.setUserProperty(value, forName: property.rawValue)
    }

    static func firebaseParameters(_ parameters: AnalyticsParameters) -> [String: Any]? {
        if parameters.isEmpty {
            return nil
        }
        var values: [String: Any] = [:]
        for (key, value) in parameters {
            values[key.rawValue] = value.firebaseValue
        }
        return values
    }

    private static func send(_ name: String, parameters: [String: Any]?) {
        guard FirebaseApp.app() != nil else {
            return
        }
        FirebaseAnalytics.Analytics.logEvent(name, parameters: parameters)
    }
}
