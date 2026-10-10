import SwiftUI

// What the app is for, at a glance: the notification that arrives at the store. It drops in like a real one once
// `isShown` turns on.
struct SampleArrivalNotification: View {
    let isShown: Bool

    var body: some View {
        ArrivalNotificationCard(
            title: String(localized: .onboardingSamplePlace),
            message: String(localized: .onboardingSampleTodos)
        )
        .offset(y: isShown ? 0 : -24)
        .opacity(isShown ? 1 : 0)
    }
}
