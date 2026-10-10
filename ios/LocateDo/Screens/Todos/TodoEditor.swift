import SwiftData
import SwiftUI

struct TodoEditor: View {
    private enum PlaceChoice: Hashable {
        case existing(Place)
        case new
    }

    @Environment(Navigator.self) private var navigator
    @Environment(LocalWrites.self) private var writes
    @Environment(TodoUndo.self) private var undo
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \Place.sortOrder) private var places: [Place]
    @Query(sort: \Membership.joinedAt) private var memberships: [Membership]

    private let editing: Todo?
    private let entry: ScreenEntry?
    @State private var title: String
    @State private var place: Place?
    @State private var assigneeID: UUID?
    @State private var remindsOnLeave: Bool
    // A place made from the picker here; saving a to-do at it lands on that place.
    @State private var newPlace: Place?
    @State private var deletesOnDisappear = false
    @State private var placePreset: PlacePreset?
    @State private var presetPlaceID: UUID?
    @FocusState private var isTitleFocused: Bool

    init(adding place: Place?, entry: ScreenEntry) {
        editing = nil
        self.entry = entry
        _title = State(initialValue: "")
        _place = State(initialValue: place)
        _remindsOnLeave = State(initialValue: false)
        if let place {
            _presetPlaceID = State(initialValue: place.id)
            if entry == .homeMenu {
                _placePreset = State(initialValue: .nearest)
            } else {
                _placePreset = State(initialValue: .place)
            }
        }
    }

    init(editing todo: Todo) {
        editing = todo
        entry = nil
        _title = State(initialValue: todo.title)
        _place = State(initialValue: todo.place)
        _assigneeID = State(initialValue: todo.assigneeID)
        _remindsOnLeave = State(initialValue: todo.placeEvent == .departure)
    }

    private var trimmedTitle: String {
        title.trimmingCharacters(in: .whitespaces)
    }

    private var canSave: Bool {
        !trimmedTitle.isEmpty && place != nil
    }

    private var placeEvent: PlaceEvent {
        if remindsOnLeave {
            return .departure
        }
        return .arrival
    }

    private var mode: EditorMode {
        EditorMode(editing: editing)
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
                    .onSubmit {
                        if canSave {
                            save()
                        }
                    }
                }
                Section {
                    placePicker
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
            .trackScreen(
                .todoEditor,
                parameters: [.mode: mode.rawValue],
                opening: entry?.parameters ?? [:]
            )
            .navigationTitle(Text(editing == nil ? .todoEditorTitle : .todoEditorEditTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(.commonCancel) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(.commonSave, action: save)
                        .disabled(!canSave)
                }
            }
            .onAppear {
                if place == nil {
                    place = places.first
                }
                if editing == nil {
                    recordPresetIfNeeded()
                    isTitleFocused = true
                }
            }
            // Deleted only once the sheet is gone, so nothing on screen reads the deleted to-do.
            .onDisappear {
                if deletesOnDisappear, let editing {
                    undo.offer(writes.delete([editing], via: .editor))
                }
            }
        }
    }

    private var placePicker: some View {
        Picker(selection: placeChoice) {
            ForEach(places) { candidate in
                Text(candidate.name)
                    .tag(Optional(PlaceChoice.existing(candidate)))
            }
            Divider()
            Label(.todoEditorNewPlace, systemImage: "plus")
                .tag(Optional(PlaceChoice.new))
        } label: {
            Text(.todoEditorPlaceLabel)
        }
    }

    private var placeChoice: Binding<PlaceChoice?> {
        Binding {
            place.map(PlaceChoice.existing)
        } set: { choice in
            switch choice {
            case .existing(let picked):
                place = picked
            case .new:
                navigator.present(.addPlace(AddPlace(
                    entry: .todoEditor,
                    forTodo: { added in
                        place = added
                        newPlace = added
                    },
                    pickExisting: { existing in
                        place = existing
                    }
                )))
            case nil:
                break
            }
        }
    }

    private func save() {
        guard let place else {
            return
        }
        if let editing {
            writes.update(editing, title: trimmedTitle, place: place, assigneeID: assigneeID, placeEvent: placeEvent)
            dismiss()
            return
        }
        let todo = Todo(title: trimmedTitle, place: place, placeEvent: placeEvent)
        todo.assigneeID = assigneeID
        if let limit = writes.add(todo, origin: origin(savingAt: place)) {
            navigator.present(.paywall(limit.trigger))
            return
        }
        dismiss()
        if let newPlace, newPlace.id == place.id {
            navigator.push(.place(newPlace, entry: .newPlace))
        }
    }

    // A place handed in was set in init; otherwise the first place, if any, was just filled in.
    private func recordPresetIfNeeded() {
        guard placePreset == nil else {
            return
        }
        presetPlaceID = place?.id
        if place == nil {
            placePreset = .noPlace
        } else {
            placePreset = .first
        }
    }

    private func origin(savingAt place: Place) -> TodoAddOrigin? {
        guard let entry, let placePreset else {
            return nil
        }
        return TodoAddOrigin(entry: entry, placePreset: placePreset, placeChanged: place.id != presetPlaceID)
    }
}
