import SwiftUI

struct UpdateRequiredView: View {
    static let appStoreURL = URL(string: "https://apps.apple.com/app/id6818942148")!

    var body: some View {
        ContentUnavailableView {
            Label(.updateTitle, systemImage: "arrow.down.app")
        } description: {
            Text(.updateMessage)
        } actions: {
            Link(destination: Self.appStoreURL) {
                Text(.updateOpenAppStore)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
    }
}

#Preview {
    UpdateRequiredView()
}
