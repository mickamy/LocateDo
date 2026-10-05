import SwiftUI

struct MemberRow: View {
    let membership: Membership
    let isCurrentUser: Bool

    var body: some View {
        HStack(spacing: 12) {
            Text(initial)
                .font(.headline)
                .foregroundStyle(.tint)
                .frame(width: 36, height: 36)
                .background(.tint.opacity(0.15), in: .circle)
                .accessibilityHidden(true)
            Text(membership.shownName)
            if isCurrentUser {
                Text(.sharingYou)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if membership.role == .owner {
                Text(.sharingOwner)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tint)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(.tint.opacity(0.15), in: .capsule)
            }
        }
    }

    private var initial: String {
        guard let first = membership.shownName.first else {
            return ""
        }
        return String(first).uppercased()
    }
}
