import SwiftUI

private let buttonHeight: CGFloat = 48
private let edgeSpacing: CGFloat = 20

extension View {
    // Lists underneath get room to scroll their last row above the button, including those on pushed screens.
    func floatingAddArea<Content: View>(isShown: Bool = true, @ViewBuilder content: () -> Content) -> some View {
        let content = content()
        return contentMargins(.bottom, isShown ? buttonHeight + edgeSpacing : 0, for: .scrollContent)
            .overlay(alignment: .bottomTrailing) {
                if isShown {
                    content
                        .padding(.trailing, edgeSpacing)
                        .padding(.bottom, edgeSpacing)
                }
            }
    }
}

struct FloatingAddButton: View {
    let title: LocalizedStringResource
    let action: () -> Void

    init(_ title: LocalizedStringResource, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: "plus")
                .font(.body.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 20)
                .frame(height: buttonHeight)
                .glassEffect(.regular.tint(.accentColor).interactive(), in: .capsule)
        }
        .buttonStyle(.plain)
    }
}

struct FloatingAddMenu<Items: View>: View {
    @ViewBuilder let items: () -> Items

    var body: some View {
        Menu {
            items()
        } label: {
            Label(.commonAdd, systemImage: "plus")
                .labelStyle(.iconOnly)
                .font(.title2)
                .foregroundStyle(.white)
                .frame(width: buttonHeight, height: buttonHeight)
                .glassEffect(.regular.tint(.accentColor).interactive(), in: .circle)
        }
        .menuOrder(.fixed)
        .buttonStyle(.plain)
    }
}
