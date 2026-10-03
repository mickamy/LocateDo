import SwiftUI

struct AlwaysLocationPromptView: View {
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "location.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(.tint)
            Text(.alwaysPromptTitle)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
            Text(.alwaysPromptDescription)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Spacer(minLength: 0)
            Button {
                locationProvider.requestAlwaysAuthorization()
                dismiss()
            } label: {
                Text(.alwaysPromptAllow)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            Button(.alwaysPromptLater) {
                dismiss()
            }
        }
        .padding(32)
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }
}
