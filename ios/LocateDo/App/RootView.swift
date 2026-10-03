import SwiftData
import SwiftUI

struct RootView: View {
    @Environment(AppRouter.self) private var router
    @Environment(AppPreferences.self) private var preferences

    var body: some View {
        if preferences.hasCompletedOnboarding {
            tabs
        } else {
            OnboardingView()
        }
    }

    private var tabs: some View {
        @Bindable var router = router
        return TabView(selection: $router.selectedTab) {
            Tab(.tabHome, systemImage: "house", value: AppTab.home) {
                NearbyView()
            }
            Tab(.tabMap, systemImage: "map", value: AppTab.map) {
                MapTabView()
            }
            Tab(.tabTodos, systemImage: "checklist", value: AppTab.todos) {
                TodoListView()
            }
            Tab(.tabSettings, systemImage: "gearshape", value: AppTab.settings) {
                SettingsView()
            }
        }
    }
}

#Preview {
    // swiftlint:disable:next force_try
    let container = try! AppModelContainer.make(inMemory: true)
    let router = AppRouter()
    let locationProvider = LocationProvider()
    let notifier = ArrivalNotifier(router: router)
    RootView()
        .modelContainer(container)
        .environment(router)
        .environment(AppPreferences(defaults: UserDefaults(suiteName: "preview")!))
        .environment(locationProvider)
        .environment(notifier)
        .environment(GeofenceMonitor(container: container, notifier: notifier, locationProvider: locationProvider))
}
