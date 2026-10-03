import SwiftData
import SwiftUI

@main
struct LocateDoApp: App {
    private let container: ModelContainer
    private let router = AppRouter()
    private let preferences = AppPreferences()
    private let locationProvider = LocationProvider()
    private let notifier: ArrivalNotifier
    private let geofence: GeofenceMonitor

    init() {
        do {
            container = try AppModelContainer.make()
        } catch {
            fatalError("Could not create ModelContainer: \(error)")
        }
        notifier = ArrivalNotifier(router: router)
        geofence = GeofenceMonitor(container: container, notifier: notifier, locationProvider: locationProvider)
        if !Self.isRunningTests {
            geofence.start()
        }
    }

    var body: some Scene {
        WindowGroup {
            if Self.isRunningTests {
                EmptyView()
            } else {
                RootView()
            }
        }
        .modelContainer(container)
        .environment(router)
        .environment(preferences)
        .environment(locationProvider)
        .environment(notifier)
        .environment(geofence)
    }

    // The unit test host must not trigger location prompts or start location updates.
    private static var isRunningTests: Bool {
        ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
            || ProcessInfo.processInfo.environment["XCTestBundlePath"] != nil
    }
}
