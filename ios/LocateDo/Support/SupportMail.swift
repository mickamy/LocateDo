import Foundation

nonisolated enum SupportMail {
    static let address = "support@locatedo.com"

    static func url(subject: String, body: String) -> URL? {
        URL(string: "mailto:\(address)?subject=\(encode(subject))&body=\(encode(body))")
    }

    private static let unreserved = CharacterSet(
        charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
    )

    private static func encode(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: unreserved) ?? ""
    }
}

nonisolated struct SupportDiagnostics: Equatable {
    enum BackgroundRefresh: String {
        case on
        case off
        case restricted
    }

    var appVersion: String
    var osVersion: String
    var deviceModel: String
    var language: String
    var timeZone: String
    var supportID: String?
    var sharesUsageData: Bool
    var userID: UUID?
    var plan: DailyState.PlanState
    var locationAuth: DailyState.LocationAuth
    var preciseLocation: Bool
    var notificationAuth: DailyState.NotificationAuth
    var backgroundRefresh: BackgroundRefresh
    var lowPowerMode: Bool

    var text: String {
        var lines = [
            "App: StopBy \(appVersion)",
            "OS: iOS \(osVersion) (\(deviceModel))",
            "Language: \(language) / Time zone: \(timeZone)"
        ]
        if let supportID {
            lines.append("Support ID: \(supportID)")
        } else if !sharesUsageData {
            // Says why there is no Support ID, rather than leaving it to look like a failure.
            lines.append("Usage data: off")
        }
        if let userID {
            lines.append("User ID: \(userID.uuidString.lowercased())")
        }
        var location = locationAuth.rawValue
        if preciseLocation {
            location += ", precise"
        } else {
            location += ", approximate"
        }
        lines.append(contentsOf: [
            "Plan: \(plan.rawValue)",
            "Location: \(location)",
            "Notifications: \(notificationAuth.rawValue)",
            "Background App Refresh: \(backgroundRefresh.rawValue)",
            "Low Power Mode: \(Self.onOff(lowPowerMode))"
        ])
        return lines.joined(separator: "\n")
    }

    private static func onOff(_ value: Bool) -> String {
        if value {
            return "on"
        }
        return "off"
    }
}
