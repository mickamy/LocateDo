import SwiftUI

struct DraftTodo: Identifiable {
    let id = UUID()
    var title: String
}

// To-dos typed while adding a place, so the place has something to remind about from the start.
struct NewPlaceTodosSection: View {
    @Binding var todos: [DraftTodo]
    @Binding var draft: String
    let remaining: Int?
    let placeholder: LocalizedStringResource
    @FocusState private var isDraftFocused: Bool

    private var left: Int? {
        guard let remaining else {
            return nil
        }
        return max(remaining - todos.count, 0)
    }

    var body: some View {
        Section {
            ForEach($todos) { $todo in
                TextField(text: $todo.title) {
                    Text(.placeEditorTodosLabel)
                }
            }
            .onDelete { offsets in
                todos.remove(atOffsets: offsets)
            }
            if left != 0 {
                TextField(text: $draft, prompt: Text(placeholder)) {
                    Text(.placeEditorTodosLabel)
                }
                .focused($isDraftFocused)
                .submitLabel(.next)
                .onSubmit(addDraft)
                .accessibilityIdentifier("placeEditor.todoDraft")
            }
        } footer: {
            VStack(alignment: .leading, spacing: 4) {
                Text(.placeEditorTodosFooter)
                if let left {
                    Text(.placeEditorTodosRemaining(left))
                }
            }
        }
        .onAppear {
            isDraftFocused = left != 0
        }
    }

    private func addDraft() {
        let title = draft.trimmingCharacters(in: .whitespaces)
        guard !title.isEmpty else {
            return
        }
        todos.append(DraftTodo(title: title))
        draft = ""
        isDraftFocused = left != 0
    }
}
