import SwiftUI

struct PlaceTodosView: View {
    let place: WatchSnapshot.Place

    @Environment(WatchStore.self) private var store

    var body: some View {
        List {
            WatchChecklist(todos: place.todos) { id in
                store.checkOff([id], source: .app)
            }
        }
        .navigationTitle(place.name)
    }
}

// The rows stay where they are once checked, so the list does not shift under the next tap.
// Only rows, so the app can put them in a List and the notification, which already scrolls, in a stack.
struct WatchChecklist: View {
    let todos: [WatchSnapshot.Todo]
    let onCheck: (UUID) -> Void

    @State private var checked: Set<UUID> = []

    var body: some View {
        ForEach(todos) { todo in
            row(todo, isChecked: checked.contains(todo.id))
        }
        if !todos.isEmpty && checked.count == todos.count {
            Label(.watchAllDone, systemImage: "checkmark.seal.fill")
                .foregroundStyle(.tint)
                .listRowBackground(Color.clear)
        }
    }

    private func row(_ todo: WatchSnapshot.Todo, isChecked: Bool) -> some View {
        Button {
            guard !isChecked else {
                return
            }
            checked.insert(todo.id)
            onCheck(todo.id)
        } label: {
            HStack(spacing: 8) {
                Image(systemName: isChecked ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(isChecked ? Color.accentColor : Color.secondary)
                    .contentTransition(.symbolEffect(.replace))
                Text(todo.title)
                    .strikethrough(isChecked)
                    .foregroundStyle(isChecked ? Color.secondary : Color.primary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityHint(Text(.watchCompleteHint))
        .accessibilityAddTraits(isChecked ? .isSelected : [])
        .sensoryFeedback(.success, trigger: isChecked)
    }
}
