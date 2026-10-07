import SwiftData
import SwiftUI

struct TodoRow: View {
    @Environment(LocalWrites.self) private var writes
    @Query(sort: \Membership.joinedAt) private var memberships: [Membership]
    let todo: Todo
    @State private var paywall: PaywallTrigger?

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
                if let detail {
                    Text(detail)
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
        .sheet(item: $paywall) { trigger in
            PaywallView(trigger: trigger)
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

    private var completer: Membership? {
        guard todo.isCompleted, memberships.count > 1 else {
            return nil
        }
        return memberships.first { $0.userID == todo.completerID }
    }

    private var detail: String? {
        if let completer {
            return String(localized: .todoCompletedBy(completer.shownName))
        }
        return assignee?.shownName
    }

    private var assigneeSelection: Binding<UUID?> {
        Binding {
            todo.assigneeID
        } set: { assigneeID in
            writes.setAssignee(assigneeID, of: todo)
        }
    }

    private var accessibilityTitle: String {
        guard let detail else {
            return todo.title
        }
        return "\(todo.title), \(detail)"
    }

    private var status: LocalizedStringResource {
        if todo.isCompleted {
            return .todoFilterDone
        }
        return .todoFilterOpen
    }

    private func toggle() {
        withAnimation {
            if let limit = writes.toggleCompletion(todo) {
                paywall = limit.trigger
            }
        }
    }
}
