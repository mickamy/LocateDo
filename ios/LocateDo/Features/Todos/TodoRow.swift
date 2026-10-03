import SwiftUI

struct TodoRow: View {
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
            Text(todo.title)
                .strikethrough(todo.isCompleted)
                .foregroundStyle(todo.isCompleted ? .secondary : .primary)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(todo.title)
        .accessibilityValue(Text(status))
        .accessibilityAddTraits(.isButton)
        .accessibilityAction {
            toggle()
        }
    }

    private var status: LocalizedStringResource {
        if todo.isCompleted {
            return .todoFilterDone
        }
        return .todoFilterOpen
    }

    private func toggle() {
        withAnimation {
            if todo.isCompleted {
                todo.reopen()
            } else {
                todo.complete()
            }
        }
    }
}
