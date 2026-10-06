import Foundation
import OSLog
import SwiftData

enum DailyStateReporter {
    static func report(
        context: ModelContext,
        entitlements: Entitlements,
        authenticator: Authenticator,
        locationProvider: LocationProvider,
        notifier: ArrivalNotifier,
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
                notificationAuth: DailyState.NotificationAuth(notifier.authorizationStatus)
            )
        } catch {
            Logger(subsystem: "com.locatedo.LocateDo", category: "analytics")
                .error("Could not read the daily state: \(error, privacy: .public)")
            return
        }
        if let change = LocationAuthHistory.change(to: state.locationAuth, defaults: defaults) {
            Analytics.log(.locationAuthChanged, parameters: [.from: change.from.rawValue, .to: change.to.rawValue])
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
