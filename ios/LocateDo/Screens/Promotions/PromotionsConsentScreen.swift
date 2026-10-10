import SwiftUI

// Asked once: whether to get news and offers. It counts as shown only once it is on screen, since it can wait its
// turn behind other sheets.
struct PromotionsConsentScreen: View {
    @Environment(PromotionsConsent.self) private var consent
    @Environment(ArrivalNotifier.self) private var notifier
    @Environment(\.dismiss) private var dismiss
    @State private var shownAt = Date()
    @State private var answer: PromotionsConsent.Answer = .dismissed

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "megaphone.fill")
                .font(.system(size: 56))
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            Text(.promotionsPromptTitle)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
            Text(.promotionsPromptMessage)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Spacer(minLength: 0)
            Button {
                close(with: .accepted)
            } label: {
                Text(.promotionsPromptAccept)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .accessibilityIdentifier("promotionsPrompt.accept")
            Button(.promotionsPromptDecline) {
                close(with: .declined)
            }
            .accessibilityIdentifier("promotionsPrompt.decline")
        }
        .padding(32)
        .presentationDetents([.medium])
        .onAppear {
            shownAt = Date()
            consent.promptShown(
                daysSinceInstall: InstallDate.daysSinceInstall(defaults: .standard, now: shownAt),
                notificationAuth: notifier.authorizationStatus
            )
        }
        .onDisappear {
            consent.answerPrompt(answer, after: Date().timeIntervalSince(shownAt))
        }
    }

    private func close(with answer: PromotionsConsent.Answer) {
        self.answer = answer
        dismiss()
    }
}
