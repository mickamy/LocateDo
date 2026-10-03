import SwiftUI

struct TodoRow: View {
    let todo: Todo
    var showsPlace = false

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
                if showsPlace, let place = todo.place {
                    Text(place.name)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
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
