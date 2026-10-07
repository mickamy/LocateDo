import SwiftUI

struct MarketingConsentSheet: View {
    @Environment(MarketingConsent.self) private var consent
    @Environment(\.dismiss) private var dismiss
    @State private var shownAt = Date()
    @State private var answer: MarketingConsent.Answer = .dismissed

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "megaphone.fill")
                .font(.system(size: 56))
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            Text(.marketingPromptTitle)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
            Text(.marketingPromptMessage)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Spacer(minLength: 0)
            Button {
                close(with: .accepted)
            } label: {
                Text(.marketingPromptAccept)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .accessibilityIdentifier("marketingPrompt.accept")
            Button(.marketingPromptDecline) {
                close(with: .declined)
            }
            .accessibilityIdentifier("marketingPrompt.decline")
        }
        .padding(32)
        .presentationDetents([.medium])
        .onDisappear {
            consent.answerPrompt(answer, after: Date().timeIntervalSince(shownAt))
        }
    }

    private func close(with answer: MarketingConsent.Answer) {
        self.answer = answer
        dismiss()
    }
}
