import SwiftData
import SwiftUI

struct RootView: View {
    @Environment(AppRouter.self) private var router

    var body: some View {
        @Bindable var router = router
        TabView(selection: $router.selectedTab) {
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
    let container = try! AppModelContainer.make(inMemory: true)
    let router = AppRouter()
    let locationProvider = LocationProvider()
    let notifier = ArrivalNotifier(router: router)
    RootView()
        .modelContainer(container)
        .environment(router)
        .environment(locationProvider)
        .environment(notifier)
        .environment(GeofenceMonitor(container: container, notifier: notifier, locationProvider: locationProvider))
}
