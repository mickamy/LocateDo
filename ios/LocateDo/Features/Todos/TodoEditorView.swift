import SwiftData
import SwiftUI

struct TodoEditorView: View {
    @Environment(LocalWrites.self) private var writes
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \Place.sortOrder) private var places: [Place]
    @Query(sort: \Membership.joinedAt) private var memberships: [Membership]

    @State private var title = ""
    @State private var place: Place?
    @State private var assigneeID: UUID?
    @FocusState private var isTitleFocused: Bool

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
                    .focused($isTitleFocused)
                    .submitLabel(.done)
                    .onSubmit(saveIfPossible)
                }
                Section {
                    Picker(selection: $place) {
                        ForEach(places) { candidate in
                            Text(candidate.name)
                                .tag(Optional(candidate))
                        }
                    } label: {
                        Text(.todoEditorPlaceLabel)
                    }
                    if memberships.count > 1 {
                        AssigneePicker(memberships: memberships, selection: $assigneeID)
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
            .onAppear {
                if place == nil {
                    place = places.first
                }
                isTitleFocused = true
            }
        }
    }

    private func saveIfPossible() {
        if canSave {
            save()
        }
    }

    private func save() {
        guard let place else {
            return
        }
        let todo = Todo(title: title.trimmingCharacters(in: .whitespaces), place: place)
        todo.assigneeID = assigneeID
        writes.add(todo)
        dismiss()
    }
}
