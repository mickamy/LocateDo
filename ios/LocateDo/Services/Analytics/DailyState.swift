import CoreLocation
import Foundation
import SwiftData
import UserNotifications

nonisolated struct DailyState: Equatable {
    enum PlanState: String {
        case free
        case pro
        case trial
    }

    enum LocationAuth: String {
        case always
        case whenInUse = "when_in_use"
        case denied
        case notDetermined = "not_determined"

        init(_ status: CLAuthorizationStatus) {
            switch status {
            case .authorizedAlways:
                self = .always
            case .authorizedWhenInUse:
                self = .whenInUse
            case .notDetermined:
                self = .notDetermined
            default:
                self = .denied
            }
        }
    }

    enum NotificationAuth: String {
        case authorized
        case denied
        case notDetermined = "not_determined"

        init(_ status: UNAuthorizationStatus) {
            switch status {
            case .notDetermined:
                self = .notDetermined
            case .denied:
                self = .denied
            default:
                self = .authorized
            }
        }
    }

    struct Counts: Equatable {
        var places = 0
        var openTodos = 0
        var completedTodosLast7Days = 0
        var placesWithOpenTodos = 0
        var customCategories = 0
        var householdMembers = 1
    }

    static let placeCountCap = 20
    static let openTodoCountCap = 30

    var counts: Counts
    var daysSinceInstall: Int
    var plan: PlanState
    var signedIn: Bool
    var locationAuth: LocationAuth
    var preciseLocation: Bool
    var notificationAuth: NotificationAuth
    var promotionsConsent: Bool
    var watchAppInstalled: Bool

    var parameters: AnalyticsParameters {
        [
            .placeCount: counts.places,
            .openTodoCount: counts.openTodos,
            .completedTodoCount7d: counts.completedTodosLast7Days,
            .placesWithOpenTodos: counts.placesWithOpenTodos,
            .customCategoryCount: counts.customCategories,
            .householdMembers: counts.householdMembers,
            .daysSinceInstall: daysSinceInstall,
            .plan: plan.rawValue,
            .signedIn: signedIn,
            .locationAuth: locationAuth.rawValue,
            .preciseLocation: preciseLocation,
            .notificationAuth: notificationAuth.rawValue,
            .promotionsConsent: promotionsConsent,
            .watchAppInstalled: watchAppInstalled
        ]
    }

    var userProperties: [AnalyticsUserProperty: String] {
        [
            .plan: plan.rawValue,
            .locationAuth: locationAuth.rawValue,
            .householdMembers: String(counts.householdMembers),
            .placeCount: Self.capped(counts.places, at: Self.placeCountCap),
            .openTodoCount: Self.capped(counts.openTodos, at: Self.openTodoCountCap),
            .signedIn: Self.flag(signedIn),
            .promotionsConsent: Self.flag(promotionsConsent)
        ]
    }

    static func flag(_ value: Bool) -> String {
        if value {
            return "1"
        }
        return "0"
    }

    static func capped(_ count: Int, at cap: Int) -> String {
        if count >= cap {
            return "\(cap)+"
        }
        return String(count)
    }

    @MainActor static func plan(subscription: ProSubscription?, householdPlan: Plan?) -> PlanState {
        if subscription?.isTrial == true {
            return .trial
        }
        if Entitlements.isPro(hasEntitlement: subscription != nil, plan: householdPlan) {
            return .pro
        }
        return .free
    }

    @MainActor static func counts(in context: ModelContext, now: Date) throws -> Counts {
        let places = try context.fetch(FetchDescriptor<Place>())
        let todos = try context.fetch(FetchDescriptor<Todo>())
        let categories = try context.fetch(FetchDescriptor<PlaceCategory>())
        let members = try context.fetchCount(FetchDescriptor<Membership>())
        let weekAgo = now.addingTimeInterval(-7 * 24 * 60 * 60)

        var counts = Counts()
        counts.places = places.count
        counts.openTodos = todos.filter { !$0.isCompleted }.count
        counts.completedTodosLast7Days = todos.filter { todo in
            guard let completedAt = todo.completedAt else {
                return false
            }
            return completedAt > weekAgo
        }.count
        counts.placesWithOpenTodos = places.filter { !$0.openTodos.isEmpty }.count
        counts.customCategories = categories.filter { $0.builtin == nil }.count
        counts.householdMembers = max(members, 1)
        return counts
    }
}

// Sent on the first foreground of each local calendar day.
nonisolated enum DailyStateSchedule {
    static let key = "dailyStateReportedOn"

    static func isDue(defaults: UserDefaults, now: Date, calendar: Calendar = .current) -> Bool {
        defaults.string(forKey: key) != day(of: now, calendar: calendar)
    }

    static func markReported(defaults: UserDefaults, now: Date, calendar: Calendar = .current) {
        defaults.set(day(of: now, calendar: calendar), forKey: key)
    }

    private static func day(of date: Date, calendar: Calendar) -> String {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        return "\(components.year ?? 0)-\(components.month ?? 0)-\(components.day ?? 0)"
    }
}

nonisolated enum AuthHistory {
    static let locationKey = "lastReportedLocationAuth"
    static let notificationKey = "lastReportedNotificationAuth"

    static func change<Auth: RawRepresentable & Equatable>(
        to current: Auth,
        key: String,
        defaults: UserDefaults
    ) -> (from: Auth, to: Auth)? where Auth.RawValue == String {
        let previous = defaults.string(forKey: key).flatMap(Auth.init(rawValue:))
        defaults.set(current.rawValue, forKey: key)
        guard let previous, previous != current else {
            return nil
        }
        return (previous, current)
    }
}
