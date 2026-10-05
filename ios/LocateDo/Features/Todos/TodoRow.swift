import SwiftData
import SwiftUI

struct TodoRow: View {
    @Environment(LocalWrites.self) private var writes
    @Query(sort: \Membership.joinedAt) private var memberships: [Membership]
    let todo: Todo

    var body: some View {
        HStack(spacing: 12) {
            Button {
                toggle()
            } label: {
                Image(systemName: todo.isCompleted ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(todo.isCompleted ? Color.accentColor : Color.secondary)
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 2) {
                Text(todo.title)
                    .strikethrough(todo.isCompleted)
                    .foregroundStyle(todo.isCompleted ? .secondary : .primary)
                if let assignee {
                    Text(assignee.shownName)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .contextMenu {
            if memberships.count > 1 {
                Menu {
                    AssigneePicker(memberships: memberships, selection: assigneeSelection)
                        .pickerStyle(.inline)
                } label: {
                    Label(.todoAssigneeChange, systemImage: "person.crop.circle")
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityTitle)
        .accessibilityValue(Text(status))
        .accessibilityAddTraits(.isButton)
        .accessibilityAction {
            toggle()
        }
    }

    private var assignee: Membership? {
        memberships.first { $0.userID == todo.assigneeID }
    }

    private var assigneeSelection: Binding<UUID?> {
        Binding {
            todo.assigneeID
        } set: { assigneeID in
            writes.setAssignee(assigneeID, of: todo)
        }
    }

    private var accessibilityTitle: String {
        guard let assignee else {
            return todo.title
        }
        return "\(todo.title), \(assignee.shownName)"
    }

    private var status: LocalizedStringResource {
        if todo.isCompleted {
            return .todoFilterDone
        }
        return .todoFilterOpen
    }

    private func toggle() {
        withAnimation {
            writes.toggleCompletion(todo)
        }
    }
}
