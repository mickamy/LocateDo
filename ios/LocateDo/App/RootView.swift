import SwiftUI

struct RootView: View {
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
    }
}

#Preview {
    RootView()
}
