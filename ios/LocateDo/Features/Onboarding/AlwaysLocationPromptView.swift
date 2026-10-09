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
    @State private var contentHeight: CGFloat = 480

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "location.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            Text(.alwaysPromptTitle)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Text(.alwaysPromptDescription)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            PrivacyNote(text: .alwaysPromptPrivacy)
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
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.size.height
        } action: { height in
            contentHeight = height
        }
        .presentationDetents([.height(contentHeight)])
        .presentationDragIndicator(.visible)
        .onDisappear {
            Analytics.log(.alwaysPromptAnswered, parameters: [
                .result: answer.rawValue,
                .durationS: max(Int(Date().timeIntervalSince(shownAt)), 0)
            ])
        }
    }
}
