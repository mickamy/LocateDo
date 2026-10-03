import SwiftData
import SwiftUI

struct RootView: View {
    @State private var locationProvider = LocationProvider()

    var body: some View {
        TabView {
            Tab(.tabHome, systemImage: "house") {
                NearbyView()
            }
            Tab(.tabMap, systemImage: "map") {
                MapTabView()
            }
            Tab(.tabTodos, systemImage: "checklist") {
                TodoListView()
            }
            Tab(.tabSettings, systemImage: "gearshape") {
                SettingsView()
            }
        }
        .environment(locationProvider)
    }
}

#Preview {
    RootView()
        .modelContainer(try! AppModelContainer.make(inMemory: true))
}
