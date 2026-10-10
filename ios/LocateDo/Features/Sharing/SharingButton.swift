import SwiftUI

enum SharingSource: String {
    case home
    case settings
}

struct SharingButton<Label: View>: View {
    let source: SharingSource
    @ViewBuilder let label: () -> Label
    @State private var isPresented = false

    var body: some View {
        Button {
            Analytics.log(.shareTapped, parameters: [.source: source.rawValue])
            isPresented = true
        } label: {
            label()
        }
        .sheet(isPresented: $isPresented) {
            SharingView()
        }
    }
}
