import SwiftUI

// Looks like the arrival notification on the Lock Screen, for showing what one will say.
struct ArrivalNotificationCard: View {
    let title: String
    let message: String
    var isMessageMuted = false

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image("AppMark")
                .resizable()
                .frame(width: 38, height: 38)
                .clipShape(RoundedRectangle(cornerRadius: 9))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                    Spacer()
                    Text(.onboardingSampleTime)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(isMessageMuted ? .secondary : .primary)
            }
        }
        .padding(14)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22))
        .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
        .accessibilityElement(children: .combine)
    }
}
