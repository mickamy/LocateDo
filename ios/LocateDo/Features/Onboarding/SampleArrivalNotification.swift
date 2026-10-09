import SwiftUI

// What the app is for, at a glance: the notification that arrives at the store. It drops in like a real one once
// `isShown` turns on.
struct SampleArrivalNotification: View {
    let isShown: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image("AppMark")
                .resizable()
                .frame(width: 38, height: 38)
                .clipShape(RoundedRectangle(cornerRadius: 9))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(.notificationArrivedTitle(String(localized: .onboardingSamplePlace)))
                        .font(.subheadline.weight(.semibold))
                    Spacer()
                    Text(.onboardingSampleTime)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Text(.onboardingSampleTodos)
                    .font(.subheadline)
            }
        }
        .padding(14)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22))
        .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
        .offset(y: isShown ? 0 : -24)
        .opacity(isShown ? 1 : 0)
        .accessibilityElement(children: .combine)
    }
}
