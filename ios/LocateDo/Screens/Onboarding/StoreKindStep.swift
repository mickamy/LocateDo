import SwiftUI

struct StoreKindStep: View {
    let onPick: (StoreKind) -> Void
    let onLater: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            Text(.firstPlaceKindTitle)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
            Text(.firstPlaceKindMessage)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            VStack(spacing: 12) {
                ForEach(StoreKind.allCases) { kind in
                    row(kind)
                }
            }
            .padding(.top, 8)
            Spacer()
            Button(.onboardingLater, action: onLater)
                .accessibilityIdentifier("onboarding.later")
        }
        .padding(32)
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            Analytics.logScreen(.onboarding, parameters: [.step: "store_kind"])
        }
    }

    // A raised card with a chevron, so it reads as something to tap that leads on.
    private func row(_ kind: StoreKind) -> some View {
        Button {
            onPick(kind)
        } label: {
            HStack(spacing: 16) {
                Image(systemName: kind.systemImage)
                    .font(.title3)
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(.tint, in: Circle())
                Text(kind.name)
                    .font(.headline)
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
            .overlay {
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color(.separator), lineWidth: 0.5)
            }
            .shadow(color: .black.opacity(0.08), radius: 6, y: 2)
            .contentShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("onboarding.kind.\(kind.rawValue)")
    }
}
