import SwiftUI

@main
struct LocateDoWatchApp: App {
    private let store = WatchStore.shared

    init() {
        store.start()
    }

    var body: some Scene {
        WindowGroup {
            PlaceListView()
                .environment(store)
        }
        WKNotificationScene(controller: ArrivalNotificationController.self, category: ArrivalChecklist.category)
    }
}
