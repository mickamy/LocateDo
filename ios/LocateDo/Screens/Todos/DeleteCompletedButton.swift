import SwiftData
import SwiftUI

// Deletes what was already checked off, after a confirmation and without Undo. Deleting syncs, so a shared household
// loses them too, which the confirmation says.
struct DeleteCompletedButton: View {
    @Environment(Navigator.self) private var navigator
    @Environment(LocalWrites.self) private var writes
    @Query private var memberships: [Membership]
    let todos: [Todo]

    var body: some View {
        Button(.todoDeleteCompleted, role: .destructive) {
            navigator.confirm(Confirmation(
                title: String(localized: .todoDeleteCompletedConfirm(todos.count)),
                message: sharedMessage,
                actionTitle: .commonDelete
            ) { [todos, writes] in
                withAnimation {
                    _ = writes.delete(todos, via: .completedBulk)
                }
            })
        }
        .font(.subheadline)
        .textCase(nil)
    }

    private var sharedMessage: String? {
        guard memberships.count > 1 else {
            return nil
        }
        return String(localized: .todoDeleteCompletedSharedMessage)
    }
}
