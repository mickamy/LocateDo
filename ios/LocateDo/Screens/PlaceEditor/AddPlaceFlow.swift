import CoreLocation
import SwiftData
import SwiftUI

// A new place in one stack: the map, then details, category, and to-dos. From the to-do screen it stops at the
// category and hands the place back.
struct AddPlaceFlow: View {
    private enum Step: Hashable {
        case details
        case category
        case todos
    }

    @Environment(Navigator.self) private var navigator
    @Environment(LocalWrites.self) private var writes
    @Environment(GeofenceMonitor.self) private var geofence
    @Environment(\.dismiss) private var dismiss
    @Query private var savedPlaces: [Place]
    @Query(sort: \PlaceCategory.sortOrder) private var categories: [PlaceCategory]

    let request: AddPlace
    @State private var draft: PlaceDraft
    @State private var todos: [DraftTodo] = []
    @State private var todoDraft = ""
    @State private var path: [Step] = []
    @State private var duplicate: Place?

    init(request: AddPlace, defaultRadiusMeters: Double) {
        self.request = request
        _draft = State(initialValue: PlaceDraft(radiusMeters: defaultRadiusMeters))
    }

    private var asksForTodos: Bool {
        request.forTodo == nil
    }

    var body: some View {
        NavigationStack(path: $path) {
            LocationPicker(initialCoordinate: draft.coordinate, closesOnPick: false) { pick in
                draft.apply(pick, categories: categories)
                continueAfterPick(at: pick.coordinate)
            }
            .navigationDestination(for: Step.self, destination: destination)
        }
        .alert(
            Text(.placeDuplicateTitle(duplicate?.name ?? "")),
            isPresented: isAskingAboutDuplicate,
            presenting: duplicate
        ) { existing in
            Button(.placeDuplicateOpen) {
                answerDuplicate(.open)
                dismiss()
                pickExisting(existing)
            }
            Button(.placeDuplicateAdd) {
                answerDuplicate(.add)
                path = [.details]
            }
            Button(.commonCancel, role: .cancel) {
                answerDuplicate(.cancel)
            }
        } message: { _ in
            Text(.placeDuplicateMessage)
        }
    }

    @ViewBuilder
    private func destination(_ step: Step) -> some View {
        switch step {
        case .details:
            PlaceDetailsForm(draft: $draft, isEditing: false) {
                path.removeAll()
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(.placeEditorNext) {
                        path.append(.category)
                    }
                    .disabled(!draft.canSave)
                }
            }
        case .category:
            PlaceCategoryStep(draft: $draft, isLastStep: !asksForTodos) {
                if asksForTodos {
                    path.append(.todos)
                } else {
                    save()
                }
            }
        case .todos:
            PlaceTodosStep(
                placeName: draft.trimmedName,
                category: draft.category,
                todos: $todos,
                draft: $todoDraft,
                remaining: writes.remaining(.openTodos),
                onSave: save
            )
        }
    }

    // A pick on a place already saved asks first: its to-dos most likely belong there.
    private func continueAfterPick(at coordinate: CLLocationCoordinate2D) {
        if let existing = PlaceDuplicate.nearest(to: coordinate, among: savedPlaces) {
            duplicate = existing
            return
        }
        path = [.details]
    }

    private var isAskingAboutDuplicate: Binding<Bool> {
        Binding {
            duplicate != nil
        } set: { isPresented in
            if !isPresented {
                duplicate = nil
            }
        }
    }

    private func answerDuplicate(_ choice: PlaceDuplicateChoice) {
        Analytics.log(.placeDuplicatePrompted, parameters: [.choice: choice.rawValue])
    }

    // From Home the saved place opens; from the to-do screen it becomes the to-do's place.
    private func pickExisting(_ existing: Place) {
        if let pickExisting = request.pickExisting {
            pickExisting(existing)
            return
        }
        navigator.show(existing)
    }

    private var todoTitles: [String] {
        (todos.map(\.title) + [todoDraft])
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }

    private func save() {
        guard let coordinate = draft.coordinate else {
            return
        }
        let place = Place(
            name: draft.trimmedName,
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            radiusMeters: draft.radiusMeters,
            category: draft.category ?? categories.first { $0.builtin == .other }
        )
        let limit = writes.add(place, source: draft.source, todoTitles: todoTitles, suggestedCategory: draft.suggestion)
        if let limit {
            navigator.present(.paywall(limit.trigger))
            return
        }
        navigator.didAddPlace = true
        request.forTodo?(place)
        dismiss()
        Task {
            await geofence.sync()
        }
    }
}
