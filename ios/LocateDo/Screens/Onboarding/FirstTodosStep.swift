import SwiftUI

// What to do at the kind of store picked, with the notification it will make growing above as it is typed.
struct FirstTodosStep: View {
    let kind: StoreKind
    @Binding var todos: [DraftTodo]
    @Binding var draft: String
    let remaining: Int?
    let onNext: () -> Void

    private var ideasLeft: [String] {
        let added = Set(todos.map(\.title))
        return kind.ideas.map { String(localized: $0) }.filter { !added.contains($0) }
    }

    var body: some View {
        Form {
            DraftTodosSection(
                placeName: String(localized: kind.name),
                title: kind.todosTitle,
                todos: $todos,
                draft: $draft,
                remaining: remaining,
                placeholder: .placeEditorTodoExampleShopping
            )
            if !ideasLeft.isEmpty {
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
