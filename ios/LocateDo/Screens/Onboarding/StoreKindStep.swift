import SwiftUI

struct StoreKindStep: View {
    let onPick: (StoreKind) -> Void
    let onLater: () -> Void

    var body: some View {
        OnboardingPage { isSpaced in
            if isSpaced {
                Spacer()
            }
            Text(.firstPlaceKindTitle)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Text(.firstPlaceKindMessage)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            VStack(spacing: 12) {
                ForEach(StoreKind.allCases) { kind in
                    ChoiceCard(title: kind.label, systemImage: kind.systemImage) {
                        onPick(kind)
                    }
                    .accessibilityIdentifier("onboarding.kind.\(kind.rawValue)")
                }
            }
            .padding(.top, 8)
            if isSpaced {
                Spacer()
            }
            Button(.onboardingLater, action: onLater)
                .accessibilityIdentifier("onboarding.later")
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            Analytics.logScreen(.onboarding, parameters: [.step: "store_kind"])
        }
    }
}
