import SwiftUI

struct SharingButton<Label: View>: View {
    enum Source: String {
        case home
        case settings
    }

    let source: Source
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
