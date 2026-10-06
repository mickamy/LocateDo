import Foundation

protocol AnalyticsSink {
    func log(_ event: AnalyticsEvent, parameters: AnalyticsParameters)
    func setUserProperty(_ value: String?, for property: AnalyticsUserProperty)
}

struct FirebaseAnalyticsSink: AnalyticsSink {
    func log(_ event: AnalyticsEvent, parameters: AnalyticsParameters) {
        Analytics.log(event, parameters: parameters)
    }

    func setUserProperty(_ value: String?, for property: AnalyticsUserProperty) {
        Analytics.setUserProperty(value, for: property)
    }
}

nonisolated enum PlaceSource: String {
    case search
    case map
    case currentLocation = "current_location"
}

extension FreeLimit {
    var analyticsKind: String {
        switch self {
        case .places: "place"
        case .openTodos: "todo"
        }
    }
}

extension Place {
    var analyticsCategory: String {
        guard let category else {
            return "none"
        }
        return category.builtin?.rawValue ?? "custom"
    }
}
