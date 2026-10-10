import SwiftData
import SwiftUI

struct TodoRow: View {
    @Environment(LocalWrites.self) private var writes
    @Environment(TodoUndo.self) private var undo
    @Query(sort: \Membership.joinedAt) private var memberships: [Membership]
    let todo: Todo
    @State private var paywall: PaywallTrigger?
    @State private var isEditing = false

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
            Button {
                isEditing = true
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(todo.title)
                            .strikethrough(todo.isCompleted)
                            .foregroundStyle(todo.isCompleted ? .secondary : .primary)
                        if todo.placeEvent == .departure {
                            Image(systemName: "figure.walk.departure")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    if let detail {
                        Text(detail)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .contextMenu {
            Button(.commonEdit, systemImage: "pencil") {
                isEditing = true
            }
            if memberships.count > 1 {
                Menu {
                    AssigneePicker(memberships: memberships, selection: assigneeSelection)
                        .pickerStyle(.inline)
                } label: {
                    Label(.todoAssigneeChange, systemImage: "person.crop.circle")
                }
            }
            Button(.commonDelete, systemImage: "trash", role: .destructive) {
                undo.offer(writes.delete([todo], via: .menu))
            }
        }
        .sheet(item: $paywall) { trigger in
            PaywallView(trigger: trigger)
        }
        .sheet(isPresented: $isEditing) {
            TodoEditorView(editing: todo)
        }
        .tracksPresentation(isEditing || paywall != nil)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityTitle)
        .accessibilityValue(Text(status))
        .accessibilityAddTraits(.isButton)
        .accessibilityAction {
            toggle()
        }
        .accessibilityAction(named: Text(.commonEdit)) {
            isEditing = true
        }
        .accessibilityAction(named: Text(.commonDelete)) {
            undo.offer(writes.delete([todo], via: .menu))
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
        var parts = [todo.title]
        if todo.placeEvent == .departure {
            parts.append(String(localized: .todoEditorRemindOnLeave))
        }
        if let detail {
            parts.append(detail)
        }
        return parts.joined(separator: ", ")
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
