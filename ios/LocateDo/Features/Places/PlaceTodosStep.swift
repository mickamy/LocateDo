import SwiftUI

// Adding a place, last screen: what to do there, asked right before saving since reminders need it. Saving with none
// is fine.
struct PlaceTodosStep: View {
    let placeName: String
    let category: PlaceCategory?
    @Binding var todos: [DraftTodo]
    @Binding var draft: String
    let remaining: Int?
    let onSave: () -> Void

    var body: some View {
        Form {
            Section {
                Text(.placeEditorTodosTitle(placeName))
                    .font(.title3.bold())
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }
            NewPlaceTodosSection(todos: $todos, draft: $draft, remaining: remaining, placeholder: placeholder)
        }
        .navigationTitle(Text(.placeEditorTodosLabel))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(.commonSave, action: onSave)
            }
        }
        .trackScreen(.placeEditor, parameters: [.mode: EditorMode.new.rawValue, .step: "todos"])
    }

    private var placeholder: LocalizedStringResource {
        switch category?.builtin {
        case .shopping: .placeEditorTodoExampleShopping
        case .life: .placeEditorTodoExampleLife
        case .work: .placeEditorTodoExampleWork
        case .other, nil: .placeEditorTodoPlaceholder
        }
    }
}
