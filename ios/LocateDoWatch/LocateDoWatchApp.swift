import SwiftUI

@main
struct LocateDoWatchApp: App {
    private let store = WatchStore.shared

    init() {
        store.start()
    }

    var body: some Scene {
        WindowGroup {
            root
                .environment(store)
        }
        WKNotificationScene(controller: ArrivalNotificationController.self, category: ArrivalChecklist.category)
    }

    @ViewBuilder
    private var root: some View {
        #if DEBUG
        if WatchScreenshotSeed.showsArrival {
            let arrival = WatchScreenshotSeed.arrival
            ScrollView {
                ArrivalNotificationView(title: arrival.title, checklist: arrival.checklist) { _ in }
            }
        } else {
            PlaceListView()
        }
        #else
        PlaceListView()
        #endif
    }
}
