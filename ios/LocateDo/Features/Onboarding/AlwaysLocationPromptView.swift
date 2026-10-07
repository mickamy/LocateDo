import SwiftUI

struct AlwaysLocationPromptView: View {
    private enum Answer: String {
        case allow
        case later
        case dismissed
    }

    @Environment(LocationProvider.self) private var locationProvider
    @Environment(\.dismiss) private var dismiss
    @State private var shownAt = Date()
    @State private var answer: Answer = .dismissed

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "location.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            Text(.alwaysPromptTitle)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
            Text(.alwaysPromptDescription)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Spacer(minLength: 0)
            Button {
                answer = .allow
                locationProvider.requestAlwaysAuthorization()
                dismiss()
            } label: {
                Text(.alwaysPromptAllow)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            Button(.alwaysPromptLater) {
                answer = .later
                dismiss()
            }
        }
        .trackScreen(.alwaysLocationPrompt)
        .padding(32)
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
        .onDisappear {
            Analytics.log(.alwaysPromptAnswered, parameters: [
                .result: answer.rawValue,
                .durationS: max(Int(Date().timeIntervalSince(shownAt)), 0)
            ])
        }
    }
}
