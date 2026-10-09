import SwiftUI

// Deletes what was already checked off, after a confirmation and without Undo.
struct DeleteCompletedButton: View {
    @Environment(LocalWrites.self) private var writes
    let todos: [Todo]
    @State private var isConfirming = false

    var body: some View {
        Button(.todoDeleteCompleted, role: .destructive) {
            isConfirming = true
        }
        .font(.subheadline)
        .textCase(nil)
        .confirmationDialog(
            Text(.todoDeleteCompletedConfirm(todos.count)),
            isPresented: $isConfirming,
            titleVisibility: .visible
        ) {
            Button(.commonDelete, role: .destructive) {
                withAnimation {
                    _ = writes.delete(todos, via: .completedBulk)
                }
            }
        }
    }
}
