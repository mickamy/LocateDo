import SwiftUI

// For someone who is not starting fresh: joining a household or coming back to an account skips the first place,
// which would only be replaced by the household's.
struct ReturningStep: View {
    let onInvite: () -> Void
    let onSignIn: () -> Void
    let onBack: () -> Void

    var body: some View {
        OnboardingPage { isSpaced in
            if isSpaced {
                Spacer()
            }
            Text(.onboardingReturningTitle)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            VStack(spacing: 12) {
                ChoiceCard(
                    title: .onboardingReturningInvite,
                    message: .onboardingReturningInviteMessage,
                    systemImage: "person.2",
                    action: onInvite
                )
                .accessibilityIdentifier("onboarding.returning.invite")
                ChoiceCard(
                    title: .onboardingReturningSignIn,
                    message: .onboardingReturningSignInMessage,
                    systemImage: "person.crop.circle",
                    action: onSignIn
                )
                .accessibilityIdentifier("onboarding.returning.signIn")
            }
            .padding(.top, 8)
            if isSpaced {
                Spacer()
            }
            Button(.commonBack, action: onBack)
        }
    }
}
