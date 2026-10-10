import SwiftUI

extension View {
    // Presents the navigator's sheet at this level, and its confirmation when nothing is above. Each sheet presents
    // the next one from inside, which is how sheets stack.
    func presentsSheets(from level: Int, onClosed: @escaping () -> Void = {}) -> some View {
        modifier(SheetPresenting(level: level, onClosed: onClosed))
    }
}

private struct SheetPresenting: ViewModifier {
    @Environment(Navigator.self) private var navigator
    let level: Int
    let onClosed: () -> Void

    func body(content: Content) -> some View {
        content
            .sheet(item: navigator.sheet(at: level), onDismiss: onClosed) { sheet in
                SheetContent(sheet: sheet)
                    .presentsSheets(from: level + 1)
                    .coversScreen()
            }
            .confirmationDialog(
                Text(navigator.confirmation?.title ?? ""),
                isPresented: isConfirming,
                titleVisibility: .visible,
                presenting: navigator.confirmation
            ) { confirmation in
                Button(confirmation.actionTitle, role: .destructive, action: confirmation.action)
            } message: { confirmation in
                if let message = confirmation.message {
                    Text(message)
                }
            }
    }

    private var isConfirming: Binding<Bool> {
        Binding {
            navigator.confirmation != nil && navigator.sheets.count == level
        } set: { isPresented in
            if !isPresented {
                navigator.confirmation = nil
                onClosed()
            }
        }
    }
}
