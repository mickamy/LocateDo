import SwiftUI

extension View {
    // For sheets and dialogs shown from screens inside Home, so Home holds its waiting requests until they close.
    func tracksPresentation(_ isPresented: Bool) -> some View {
        modifier(PresentationTracking(isPresented: isPresented))
    }
}

private struct PresentationTracking: ViewModifier {
    @Environment(AppRouter.self) private var router
    let isPresented: Bool
    @State private var isCounted = false

    func body(content: Content) -> some View {
        content
            .onChange(of: isPresented, initial: true) {
                count(isPresented)
            }
            .onDisappear {
                count(false)
            }
    }

    private func count(_ isOpen: Bool) {
        if isOpen == isCounted {
            return
        }
        isCounted = isOpen
        if isOpen {
            router.presentationsInsideHome += 1
        } else {
            router.presentationsInsideHome -= 1
        }
    }
}
