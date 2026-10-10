import SwiftUI

struct DraftTodo: Identifiable {
    let id = UUID()
    var title: String
}

// To-dos typed while adding a place, under a preview of the arrival notification they will make. "Add To-Do" turns
// into a field where Return adds the row and moves on to the next. Rows carry no circle, which would look checkable.
struct NewPlaceTodosSection: View {
    let placeName: String
    @Binding var todos: [DraftTodo]
    @Binding var draft: String
    let remaining: Int?
    let placeholder: LocalizedStringResource
    @State private var isTyping = false
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
                if isTyping {
                    HStack(spacing: 12) {
                        TextField(text: $draft, prompt: Text(placeholder)) {
                            Text(.placeEditorTodosLabel)
                        }
                        .focused($isDraftFocused)
                        .submitLabel(.next)
                        .onSubmit { addDraft() }
                        .accessibilityIdentifier("placeEditor.todoDraft")
                        // Return does the same; the button says that the row can be added and another typed.
                        Button(action: addFromButton) {
                            Image(systemName: "plus.circle.fill")
                                .font(.title2)
                        }
                        .buttonStyle(.borderless)
                        .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
                        .accessibilityLabel(Text(.placeDetailAddTodo))
                    }
                } else {
                    Button(.placeDetailAddTodo, systemImage: "plus") {
                        startTyping()
                    }
                }
            }
        } header: {
            VStack(alignment: .leading, spacing: 20) {
                preview
                Text(.placeEditorTodosTitle(placeName))
                    .font(.title3.bold())
                    .foregroundStyle(.primary)
            }
            .textCase(nil)
            .padding(.bottom, 8)
        } footer: {
            VStack(alignment: .leading, spacing: 4) {
                Text(.placeEditorTodosFooter)
                if let left {
                    Text(.placeEditorTodosRemaining(left))
                }
            }
        }
        // Text left half-typed when going back comes back open, so it is never hidden yet saved.
        .onAppear {
            if left != 0, todos.isEmpty || !draft.isEmpty {
                startTyping()
            }
        }
        // Text the keyboard commits after the field has closed must not linger, unseen, and get saved.
        .onChange(of: draft) {
            if !isTyping, !draft.isEmpty {
                draft = ""
            }
        }
    }

    // Follows the typing, with the same wording and cut-off as the real notification.
    private var preview: some View {
        let titles = (todos.map(\.title) + [draft])
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
        var message = String(localized: .placeEditorPreviewEmpty)
        if !titles.isEmpty {
            message = NotificationPolicy.body(todoTitles: titles)
        }
        return ArrivalNotificationCard(
            title: placeName,
            message: message,
            isMessageMuted: titles.isEmpty
        )
        .padding(.top, 8)
    }

    // The field appears first, then takes the focus, which a field not yet on screen cannot.
    private func startTyping() {
        isTyping = true
        Task {
            isDraftFocused = true
        }
    }

    // Leaving the field first commits text still being converted by the keyboard (Japanese input), which would
    // otherwise land in the emptied field after the row is added.
    private func addFromButton() {
        isDraftFocused = false
        Task {
            try? await Task.sleep(for: .milliseconds(100))
            addDraft(closesWhenEmpty: false)
        }
    }

    // Only an empty Return closes the field back into "Add To-Do"; the button never does.
    private func addDraft(closesWhenEmpty: Bool = true) {
        let title = draft.trimmingCharacters(in: .whitespaces)
        if title.isEmpty {
            if closesWhenEmpty {
                stopTyping()
            } else {
                refocus()
            }
            return
        }
        todos.append(DraftTodo(title: title))
        draft = ""
        if left == 0 {
            stopTyping()
            return
        }
        refocus()
    }

    private func stopTyping() {
        isTyping = false
        draft = ""
    }

    private func refocus() {
        Task {
            isDraftFocused = true
        }
    }
}
