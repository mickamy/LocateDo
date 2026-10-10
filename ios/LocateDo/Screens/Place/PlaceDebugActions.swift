#if DEBUG || STAGING
import SwiftUI
import UserNotifications

// The place menu's shortcuts for checking reminders without going anywhere.
struct PlaceDebugActions: View {
    @Environment(GeofenceMonitor.self) private var geofence
    @Environment(ArrivalNotifier.self) private var notifier
    let place: Place

    var body: some View {
        action("Simulate arrival (debug)", systemImage: "location.fill.viewfinder") {
            await geofence.simulateArrival(at: place)
        }
        action("Simulate departure (debug)", systemImage: "figure.walk.departure") {
            await geofence.simulateDeparture(at: place)
        }
        action("Simulate arrival in 10 s (debug)", systemImage: "timer") {
            UNUserNotificationCenter.current().removeAllDeliveredNotifications()
            await notifier.notify(.arrival, at: place, todos: place.openTodos(for: .arrival), after: 10)
        }
        #if DEBUG
        action("Simulate completion notice in 10 s (debug)", systemImage: "checkmark.circle", asksFirst: false) {
            await ScreenshotSeed.scheduleCompletionNotice(after: 10)
        }
        #endif
    }

    private func action(
        _ title: String,
        systemImage: String,
        asksFirst: Bool = true,
        perform: @escaping () async -> Void
    ) -> some View {
        Button {
            Task {
                if asksFirst {
                    await notifier.requestAuthorization()
                }
                await perform()
            }
        } label: {
            Label {
                Text(verbatim: title)
            } icon: {
                Image(systemName: systemImage)
            }
        }
    }
}
#endif
