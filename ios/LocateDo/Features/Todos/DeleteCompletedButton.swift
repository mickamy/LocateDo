import SwiftData
import SwiftUI

// Deletes what was already checked off, after a confirmation and without Undo. Deleting syncs, so a shared household
// loses them too, which the confirmation says.
struct DeleteCompletedButton: View {
    @Environment(LocalWrites.self) private var writes
    @Query private var memberships: [Membership]
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
        } message: {
            if memberships.count > 1 {
                Text(.todoDeleteCompletedSharedMessage)
            }
        }
    }
}
