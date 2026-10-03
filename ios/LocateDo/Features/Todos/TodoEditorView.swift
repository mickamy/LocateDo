import SwiftData
import SwiftUI

struct TodoEditorView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \Place.sortOrder) private var places: [Place]

    @State private var title = ""
    @State private var place: Place?

    init(place: Place? = nil) {
        _place = State(initialValue: place)
    }

    private var canSave: Bool {
        !title.trimmingCharacters(in: .whitespaces).isEmpty && place != nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(text: $title, prompt: Text(.todoEditorTitlePlaceholder)) {
                        Text(.todoEditorTitleLabel)
                    }
                }
                Section {
                    Picker(selection: $place) {
                        ForEach(places) { candidate in
                            Label(candidate.name, systemImage: candidate.category.systemImage)
                                .tag(Optional(candidate))
                        }
                    } label: {
                        Text(.todoEditorPlaceLabel)
                    }
                }
            }
            .navigationTitle(Text(.todoEditorTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(.commonCancel) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(.commonSave) {
                        save()
                    }
                    .disabled(!canSave)
                }
            }
        }
    }

    private func save() {
        guard let place else {
            return
        }
        let todo = Todo(title: title.trimmingCharacters(in: .whitespaces), place: place)
        modelContext.insert(todo)
        place.todos.append(todo)
        dismiss()
    }
}
