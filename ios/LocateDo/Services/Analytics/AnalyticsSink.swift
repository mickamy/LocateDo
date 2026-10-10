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

nonisolated enum TodoAddVia: String {
    case todoEditor = "todo_editor"
    case placeEditor = "place_editor"
}

// The place the to-do editor started with: the nearest one from Home's plus menu, the place it was opened on, the
// first place when neither gave one, or none when there were no places.
nonisolated enum PlacePreset: String {
    case nearest
    case place
    case first
    case noPlace = "none"
}

nonisolated struct TodoAddOrigin {
    let entry: ScreenEntry
    let placePreset: PlacePreset
    let placeChanged: Bool
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
