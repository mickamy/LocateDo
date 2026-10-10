import SwiftData
import SwiftUI

struct TodoEditorView: View {
    @Environment(LocalWrites.self) private var writes
    @Environment(TodoUndo.self) private var undo
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \Place.sortOrder) private var places: [Place]
    @Query(sort: \Membership.joinedAt) private var memberships: [Membership]

    @State private var title = ""
    @State private var place: Place?
    @State private var assigneeID: UUID?
    @State private var remindsOnLeave = false
    @FocusState private var isTitleFocused: Bool
    @State private var paywall: PaywallTrigger?
    @State private var deletesOnDisappear = false
    private let editing: Todo?

    init(place: Place? = nil) {
        editing = nil
        _place = State(initialValue: place)
    }

    init(editing todo: Todo) {
        editing = todo
        _title = State(initialValue: todo.title)
        _place = State(initialValue: todo.place)
        _assigneeID = State(initialValue: todo.assigneeID)
        _remindsOnLeave = State(initialValue: todo.placeEvent == .departure)
    }

    private var placeEvent: PlaceEvent {
        if remindsOnLeave {
            return .departure
        }
        return .arrival
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
                Section {
                    Toggle(isOn: $remindsOnLeave) {
                        Text(.todoEditorRemindOnLeave)
                    }
                }
                if editing != nil {
                    Section {
                        Button(.commonDelete, role: .destructive) {
                            deletesOnDisappear = true
                            dismiss()
                        }
                    }
                }
            }
            .trackScreen(.todoEditor, parameters: [.mode: editing == nil ? "add" : "edit"])
            .navigationTitle(Text(editing == nil ? .todoEditorTitle : .todoEditorEditTitle))
            .sheet(item: $paywall) { trigger in
                PaywallView(trigger: trigger)
            }
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
                if editing == nil {
                    isTitleFocused = true
                }
            }
            // Deleted only once the sheet is gone, so nothing on screen reads the deleted to-do.
            .onDisappear {
                if deletesOnDisappear {
                    delete()
                }
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
        let title = title.trimmingCharacters(in: .whitespaces)
        if let editing {
            writes.update(editing, title: title, place: place, assigneeID: assigneeID, placeEvent: placeEvent)
            dismiss()
            return
        }
        let todo = Todo(title: title, place: place, placeEvent: placeEvent)
        todo.assigneeID = assigneeID
        if let limit = writes.add(todo) {
            paywall = limit.trigger
            return
        }
        dismiss()
    }

    private func delete() {
        guard let editing else {
            return
        }
        undo.offer(writes.delete([editing], via: .editor))
        dismiss()
    }
}
