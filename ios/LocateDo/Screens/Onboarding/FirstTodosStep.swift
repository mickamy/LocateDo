import SwiftUI

// What to do at the kind of store picked, with the notification it will make growing above as it is typed.
struct FirstTodosStep: View {
    let store: FirstStore
    @Binding var todos: [DraftTodo]
    @Binding var draft: String
    let remaining: Int?
    let onNext: () -> Void

    // Ideas count against the free limit like typed rows, including one still being typed, so none is dropped on save.
    private var hasRoom: Bool {
        guard let remaining else {
            return true
        }
        let typing = draft.trimmingCharacters(in: .whitespaces).isEmpty ? 0 : 1
        return todos.count + typing < remaining
    }

    private var ideasLeft: [String] {
        let added = Set(todos.map(\.title))
        return store.kind.ideas.map { String(localized: $0) }.filter { !added.contains($0) }
    }

    // The first ideas, as the real notification would list them.
    private var example: String {
        let ideas = store.kind.ideas.prefix(3).map { String(localized: $0) }
        if ideas.isEmpty {
            return String(localized: .placeEditorTodoExampleShopping)
        }
        return String(localized: .placeEditorPreviewExample(NotificationPolicy.body(todoTitles: Array(ideas))))
    }

    var body: some View {
        Form {
            DraftTodosSection(
                placeName: store.name,
                title: store.todosTitle,
                example: example,
                todos: $todos,
                draft: $draft,
                remaining: remaining,
                placeholder: .placeEditorTodoExampleShopping
            )
            if hasRoom, !ideasLeft.isEmpty {
                Section {
                    ideas
                } header: {
                    Text(.firstPlaceIdeasTitle)
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(.placeEditorNext, action: onNext)
                    .accessibilityIdentifier("onboarding.todosNext")
            }
        }
        .onAppear {
            Analytics.logScreen(.onboarding, parameters: [.step: "todos"])
        }
    }

    private var ideas: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(ideasLeft, id: \.self) { idea in
                    Button {
                        todos.append(DraftTodo(title: idea))
                    } label: {
                        Label(idea, systemImage: "plus")
                            .font(.subheadline)
                    }
                    .buttonStyle(.bordered)
                    .buttonBorderShape(.capsule)
                }
            }
            .padding(.vertical, 4)
        }
        .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
    }
}
