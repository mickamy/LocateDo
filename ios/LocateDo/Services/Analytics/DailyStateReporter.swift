import Foundation
import OSLog
import SwiftData

enum DailyStateReporter {
    // swiftlint:disable:next function_parameter_count
    static func report(
        context: ModelContext,
        entitlements: Entitlements,
        authenticator: Authenticator,
        locationProvider: LocationProvider,
        notifier: ArrivalNotifier,
        marketingConsent: Bool,
        defaults: UserDefaults = .standard,
        now: Date = .now
    ) async {
        locationProvider.refreshAuthorizationStatus()
        await notifier.refreshAuthorizationStatus()
        let state: DailyState
        do {
            state = DailyState(
                counts: try DailyState.counts(in: context, now: now),
                daysSinceInstall: InstallDate.daysSinceInstall(defaults: defaults, now: now),
                plan: DailyState.plan(
                    subscription: entitlements.subscription,
                    householdPlan: try SyncState.current(in: context).plan
                ),
                signedIn: authenticator.isSignedIn,
                locationAuth: DailyState.LocationAuth(locationProvider.authorizationStatus),
                preciseLocation: locationProvider.hasPreciseLocation,
                notificationAuth: DailyState.NotificationAuth(notifier.authorizationStatus),
                marketingConsent: marketingConsent
            )
        } catch {
            Logger(subsystem: "com.locatedo.LocateDo", category: "analytics")
                .error("Could not read the daily state: \(error, privacy: .public)")
            return
        }
        if let change = AuthHistory.change(to: state.locationAuth, key: AuthHistory.locationKey, defaults: defaults) {
            Analytics.log(.locationAuthChanged, parameters: [.from: change.from.rawValue, .to: change.to.rawValue])
        }
        let notificationChange = AuthHistory.change(
            to: state.notificationAuth,
            key: AuthHistory.notificationKey,
            defaults: defaults
        )
        if let change = notificationChange {
            Analytics.log(.notificationAuthChanged, parameters: [.from: change.from.rawValue, .to: change.to.rawValue])
        }
        for (property, value) in state.userProperties {
            Analytics.setUserProperty(value, for: property)
        }
        if DailyStateSchedule.isDue(defaults: defaults, now: now) {
            Analytics.log(.dailyState, parameters: state.parameters)
            DailyStateSchedule.markReported(defaults: defaults, now: now)
        }
    }
}
