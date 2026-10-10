import SwiftUI

// An onboarding page laid out with spacers when it fits, and scrolling without them on a small screen or with large
// text, rather than cutting its words short. The content puts its spacers in only when told it is spaced.
struct OnboardingPage<Content: View>: View {
    @ViewBuilder let content: (_ isSpaced: Bool) -> Content

    var body: some View {
        ViewThatFits(in: .vertical) {
            page(isSpaced: true)
            ScrollView {
                page(isSpaced: false)
            }
        }
    }

    private func page(isSpaced: Bool) -> some View {
        VStack(spacing: 20) {
            content(isSpaced)
        }
        .padding(32)
    }
}
