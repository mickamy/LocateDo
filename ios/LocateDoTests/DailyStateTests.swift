import CoreLocation
import Foundation
import SwiftData
import Testing
import UserNotifications

@testable import LocateDo

@MainActor
struct DailyStateTests {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    @Test func countsWhatIsStoredOnTheDevice() throws {
        let container = try AppModelContainer.make(inMemory: true)
        let context = ModelContext(container)
        let grocery = Place(name: "Grocery", latitude: 35.0, longitude: 139.0, sortOrder: 0)
        let pharmacy = Place(name: "Pharmacy", latitude: 35.1, longitude: 139.1, sortOrder: 1)
        context.insert(grocery)
        context.insert(pharmacy)
        context.insert(Todo(title: "Milk", place: grocery))
        context.insert(Todo(title: "Eggs", place: grocery, placeEvent: .departure))
        let recent = Todo(title: "Bread", place: pharmacy)
        context.insert(recent)
        recent.complete(at: now.addingTimeInterval(-2 * 86_400))
        let old = Todo(title: "Stamps", place: pharmacy)
        context.insert(old)
        old.complete(at: now.addingTimeInterval(-8 * 86_400))
        context.insert(PlaceCategory(name: "Kids", icon: "figure.2", color: "orange", sortOrder: 10))
        context.insert(Membership(userID: .v7(), role: .owner, displayName: "A", joinedAt: now, updatedAt: now))
        context.insert(Membership(userID: .v7(), role: .member, displayName: "B", joinedAt: now, updatedAt: now))
        try context.save()

        let counts = try DailyState.counts(in: context, now: now)

        #expect(counts == DailyState.Counts(
            places: 2,
            openTodos: 2,
            openDepartureTodos: 1,
            completedTodosLast7Days: 1,
            placesWithOpenTodos: 1,
            customCategories: 1,
            householdMembers: 2
        ))
    }

    @Test func aDeviceWithoutAHouseholdCountsAsOneMember() throws {
        let container = try AppModelContainer.make(inMemory: true)

        let counts = try DailyState.counts(in: ModelContext(container), now: now)

        #expect(counts.householdMembers == 1)
        #expect(counts.customCategories == 0)
    }

    @Test func aTrialIsToldApartFromAPaidPlan() {
        let trial = Self.subscription(isTrial: true)
        let paid = Self.subscription(isTrial: false)

        #expect(DailyState.plan(subscription: trial, householdPlan: .free) == .trial)
        #expect(DailyState.plan(subscription: paid, householdPlan: .free) == .pro)
        #expect(DailyState.plan(subscription: nil, householdPlan: .pro) == .pro)
        #expect(DailyState.plan(subscription: nil, householdPlan: .free) == .free)
        #expect(DailyState.plan(subscription: nil, householdPlan: nil) == .free)
    }

    @Test func permissionsMapToTheirReportedNames() {
        #expect(DailyState.LocationAuth(.authorizedAlways) == .always)
        #expect(DailyState.LocationAuth(.authorizedWhenInUse) == .whenInUse)
        #expect(DailyState.LocationAuth(.restricted) == .denied)
        #expect(DailyState.LocationAuth(.notDetermined) == .notDetermined)
        #expect(DailyState.NotificationAuth(.provisional) == .authorized)
        #expect(DailyState.NotificationAuth(.denied) == .denied)
    }

    @Test func userPropertiesCapTheCounts() {
        var state = Self.state
        state.counts.places = 25
        state.counts.openTodos = 30

        #expect(state.userProperties[.placeCount] == "20+")
        #expect(state.userProperties[.openTodoCount] == "30+")
    }

    @Test func userPropertiesKeepCountsBelowTheCap() {
        let properties = Self.state.userProperties

        #expect(properties[.placeCount] == "3")
        #expect(properties[.openTodoCount] == "14")
        #expect(properties[.householdMembers] == "2")
        #expect(properties[.plan] == "free")
        #expect(properties[.locationAuth] == "always")
        #expect(properties[.signedIn] == "1")
        #expect(properties[.promotionsConsent] == "1")
    }

    @Test func theEventCarriesRawCounts() throws {
        let values = try #require(Analytics.firebaseParameters(Self.state.parameters))

        #expect(values["place_count"] as? Int == 3)
        #expect(values["open_todo_count"] as? Int == 14)
        #expect(values["open_departure_todos"] as? Int == 4)
        #expect(values["completed_todo_count_7d"] as? Int == 5)
        #expect(values["days_since_install"] as? Int == 12)
        #expect(values["precise_location"] as? Int == 1)
        #expect(values["notification_auth"] as? String == "authorized")
        #expect(values["promotions_consent"] as? Int == 1)
        #expect(values["watch_app_installed"] as? Int == 0)
    }

    private static func subscription(isTrial: Bool) -> ProSubscription {
        ProSubscription(term: .annual, expiresAt: nil, willRenew: true, isTrial: isTrial, hasBillingIssue: false)
    }

    private static let state = DailyState(
        counts: DailyState.Counts(
            places: 3,
            openTodos: 14,
            openDepartureTodos: 4,
            completedTodosLast7Days: 5,
            placesWithOpenTodos: 2,
            customCategories: 0,
            householdMembers: 2
        ),
        daysSinceInstall: 12,
        plan: .free,
        signedIn: true,
        locationAuth: .always,
        preciseLocation: true,
        notificationAuth: .authorized,
        promotionsConsent: true,
        watchAppInstalled: false
    )
}

struct DailyStateScheduleTests {
    @Test func reportsOncePerCalendarDay() throws {
        let defaults = try makeDefaults()
        let calendar = Self.calendar
        let morning = try #require(calendar.date(from: DateComponents(year: 2026, month: 10, day: 6, hour: 8)))
        let evening = try #require(calendar.date(from: DateComponents(year: 2026, month: 10, day: 6, hour: 22)))
        let justAfterMidnight = DateComponents(year: 2026, month: 10, day: 7, hour: 0, minute: 5)
        let nextDay = try #require(calendar.date(from: justAfterMidnight))

        #expect(DailyStateSchedule.isDue(defaults: defaults, now: morning, calendar: calendar))
        DailyStateSchedule.markReported(defaults: defaults, now: morning, calendar: calendar)
        #expect(!DailyStateSchedule.isDue(defaults: defaults, now: evening, calendar: calendar))
        #expect(DailyStateSchedule.isDue(defaults: defaults, now: nextDay, calendar: calendar))
    }

    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Tokyo")!
        return calendar
    }()

    private func makeDefaults() throws -> UserDefaults {
        let suite = "DailyStateScheduleTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defaults.removePersistentDomain(forName: suite)
        return defaults
    }
}

struct AuthHistoryTests {
    private static let key = AuthHistory.locationKey

    @Test func theFirstReadingIsNotAChange() throws {
        let defaults = try makeDefaults()

        #expect(AuthHistory.change(to: DailyState.LocationAuth.always, key: Self.key, defaults: defaults) == nil)
        #expect(AuthHistory.change(to: DailyState.LocationAuth.always, key: Self.key, defaults: defaults) == nil)
    }

    @Test func aDowngradeIsReportedOnce() throws {
        let defaults = try makeDefaults()
        _ = AuthHistory.change(to: DailyState.LocationAuth.always, key: Self.key, defaults: defaults)

        let change = try #require(
            AuthHistory.change(to: DailyState.LocationAuth.whenInUse, key: Self.key, defaults: defaults)
        )

        #expect(change.from == .always)
        #expect(change.to == .whenInUse)
        #expect(AuthHistory.change(to: DailyState.LocationAuth.whenInUse, key: Self.key, defaults: defaults) == nil)
    }

    @Test func eachPermissionKeepsItsOwnHistory() throws {
        let defaults = try makeDefaults()
        _ = AuthHistory.change(to: DailyState.LocationAuth.always, key: AuthHistory.locationKey, defaults: defaults)
        _ = AuthHistory.change(
            to: DailyState.NotificationAuth.authorized,
            key: AuthHistory.notificationKey,
            defaults: defaults
        )

        let change = try #require(AuthHistory.change(
            to: DailyState.NotificationAuth.denied,
            key: AuthHistory.notificationKey,
            defaults: defaults
        ))

        #expect(change.from == .authorized)
        #expect(change.to == .denied)
        #expect(AuthHistory.change(
            to: DailyState.LocationAuth.always,
            key: AuthHistory.locationKey,
            defaults: defaults
        ) == nil)
    }

    private func makeDefaults() throws -> UserDefaults {
        let suite = "AuthHistoryTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defaults.removePersistentDomain(forName: suite)
        return defaults
    }
}
