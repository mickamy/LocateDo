import SwiftUI

// The first place is saved. When reminders still need permissions it says so plainly, rather than promise one that
// cannot arrive yet, and leads on to the setup shown over Home.
struct FirstPlaceDoneStep: View {
    let placeName: String
    let needsSetup: Bool
    let onContinue: () -> Void

    var body: some View {
        OnboardingPage { isSpaced in
            if isSpaced {
                Spacer()
            }
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 88))
                .foregroundStyle(.green)
                .accessibilityHidden(true)
            Text(.firstPlaceDoneTitle(placeName))
                .font(.title2.bold())
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Text(needsSetup ? .firstPlaceDoneAlmost : .firstPlaceDoneReady)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            if isSpaced {
                Spacer()
            }
            Button(action: onContinue) {
                Text(needsSetup ? .firstPlaceDoneContinue : .onboardingStart)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .accessibilityIdentifier("onboarding.done")
        }
        .navigationBarBackButtonHidden()
        .onAppear {
            Analytics.logScreen(.onboarding, parameters: [.step: "done"])
        }
    }
}
