import SwiftUI

extension View {
    func floatingAddButton(
        _ title: LocalizedStringResource,
        isShown: Bool = true,
        action: @escaping () -> Void
    ) -> some View {
        safeAreaInset(edge: .bottom, alignment: .trailing) {
            if isShown {
                button(title, action: action)
            }
        }
    }

    private func button(_ title: LocalizedStringResource, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: "plus")
                .font(.body.weight(.semibold))
                .foregroundStyle(.white)
                .contentTransition(.opacity)
                .padding(.horizontal, 20)
                .frame(height: 48)
                .glassEffect(.regular.tint(.accentColor).interactive(), in: .capsule)
        }
        .buttonStyle(.plain)
        .animation(.snappy, value: String(localized: title))
        .padding(.trailing, 20)
        .padding(.bottom, 20)
    }
}
