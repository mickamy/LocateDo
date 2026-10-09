import SwiftUI

@main
struct LocateDoWatchApp: App {
    private let store = WatchStore()

    init() {
        store.start()
    }

    var body: some Scene {
        WindowGroup {
            PlaceListView()
                .environment(store)
        }
    }
}
