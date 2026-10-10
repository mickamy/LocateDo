import CoreLocation
import MapKit
import SwiftData
import SwiftUI

// A new place is added in one stack: the map, then details, category, and to-dos. An existing one is edited in the
// details form alone, with the map in a sheet.
struct PlaceEditorView: View {
    private enum Step: Hashable {
        case details
        case category
        case todos
    }

    @Environment(LocalWrites.self) private var writes
    @Environment(\.dismiss) private var dismiss
    @Environment(GeofenceMonitor.self) private var geofence
    @Environment(AppRouter.self) private var router
    @Query private var savedPlaces: [Place]
    @Query(sort: \PlaceCategory.sortOrder) private var categories: [PlaceCategory]

    let place: Place?
    let asksForTodos: Bool
    var onSave: ((Place) -> Void)?
    @State private var name: String
    @State private var coordinate: CLLocationCoordinate2D?
    @State private var source: PlaceSource?
    @State private var radiusMeters: Double
    @State private var category: PlaceCategory?
    @State private var isPickingLocation = false
    @State private var paywall: PaywallTrigger?
    @State private var todos: [DraftTodo] = []
    @State private var todoDraft = ""
    @State private var suggestion: BuiltinCategory?
    @State private var hasChosenCategory = false
    @State private var path: [Step] = []
    @State private var pickedName: String?
    @State private var duplicate: Place?

    init(
        place: Place? = nil,
        defaultRadiusMeters: Double = Place.defaultRadiusMeters,
        asksForTodos: Bool = true,
        onSave: ((Place) -> Void)? = nil
    ) {
        self.place = place
        self.asksForTodos = asksForTodos
        self.onSave = onSave
        _name = State(initialValue: place?.name ?? "")
        _coordinate = State(initialValue: place?.coordinate)
        _radiusMeters = State(initialValue: place?.radiusMeters ?? defaultRadiusMeters)
        _category = State(initialValue: place?.category)
    }

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty && coordinate != nil
    }

    var body: some View {
        NavigationStack(path: $path) {
            root
                .navigationDestination(for: Step.self, destination: destination)
        }
        .sheet(isPresented: $isPickingLocation) {
            picker(isEmbedded: false)
        }
        .sheet(item: $paywall) { trigger in
            PaywallView(trigger: trigger)
        }
        .alert(
            Text(.placeDuplicateTitle(duplicate?.name ?? "")),
            isPresented: isAskingAboutDuplicate,
            presenting: duplicate
        ) { existing in
            Button(.placeDuplicateOpen) {
                answerDuplicate(.open)
                dismiss()
                // From the to-do screen, the saved place is picked for the to-do instead of opened.
                if asksForTodos {
                    router.open(placeID: existing.id)
                } else {
                    onSave?(existing)
                }
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
    private var root: some View {
        if place == nil {
            picker(isEmbedded: true)
        } else {
            form
        }
    }

    private func picker(isEmbedded: Bool) -> some View {
        PlacePickerMapView(
            initialCoordinate: coordinate,
            isEmbedded: isEmbedded
        ) { picked, suggestedName, pickedFrom, guessed in
            coordinate = picked
            source = pickedFrom
            suggest(guessed)
            // A name typed by hand stays; one that came from the last pick follows the new one.
            if name.isEmpty || name == pickedName {
                name = suggestedName ?? ""
                pickedName = suggestedName
            }
            if isEmbedded {
                continueAfterPick(at: picked)
            }
        }
    }

    private var form: some View {
        Form {
            Section {
                if let coordinate {
                    locationPreview(coordinate)
                } else {
                    Text(.placeEditorNoLocation)
                        .foregroundStyle(.secondary)
                }
                Button(.placeEditorChooseOnMap, systemImage: "map") {
                    chooseOnMap()
                }
            } header: {
                Text(.placeEditorLocationLabel)
            }
            Section {
                TextField(text: $name, prompt: Text(.placeEditorNamePlaceholder)) {
                    Text(.placeEditorNameLabel)
                }
                .submitLabel(.done)
            } header: {
                Text(.placeEditorNameLabel)
            }
            Section {
                Slider(value: $radiusMeters, in: Place.radiusRange, step: 50) {
                    Text(.placeEditorRadiusLabel)
                }
                .accessibilityValue(Text(DistanceFormatting.string(meters: radiusMeters)))
                Text(DistanceFormatting.string(meters: radiusMeters))
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .foregroundStyle(.secondary)
                    .accessibilityHidden(true)
            } header: {
                Text(.placeEditorRadiusLabel)
            }
            if place != nil {
                Section {
                    Picker(selection: $category) {
                        ForEach(categories) { candidate in
                            categoryLabel(candidate)
                                .tag(Optional(candidate))
                        }
                        categoryLabel(nil)
                            .tag(PlaceCategory?.none)
                    } label: {
                        Text(.placeEditorCategoryLabel)
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                    NavigationLink {
                        CategoriesView()
                    } label: {
                        Label(.categoryManage, systemImage: "slider.horizontal.3")
                    }
                } header: {
                    Text(.placeEditorCategoryLabel)
                }
            }
        }
        .trackScreen(.placeEditor, parameters: screenParameters)
        .navigationTitle(Text(place == nil ? .placeEditorTitleNew : .placeEditorTitleEdit))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // A new place comes back to the map instead.
            if place != nil {
                ToolbarItem(placement: .cancellationAction) {
                    Button(.commonCancel) {
                        dismiss()
                    }
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                if place == nil {
                    Button(.placeEditorNext) {
                        path.append(.category)
                    }
                    .disabled(!canSave)
                } else {
                    Button(.commonSave) {
                        save()
                    }
                    .disabled(!canSave)
                }
            }
        }
        .onChange(of: categories) {
            if let category, !categories.contains(category) {
                self.category = nil
            }
        }
    }

    @ViewBuilder
    private func destination(_ step: Step) -> some View {
        switch step {
        case .details:
            form
        case .category:
            PlaceCategoryStep(
                category: $category,
                isSuggested: suggestion != nil && !hasChosenCategory,
                isLastStep: !asksForTodos,
                onChoose: { hasChosenCategory = true },
                onNext: nextAfterCategory
            )
        case .todos:
            PlaceTodosStep(
                placeName: name.trimmingCharacters(in: .whitespaces),
                category: category,
                todos: $todos,
                draft: $todoDraft,
                remaining: writes.remaining(.openTodos),
                onSave: save
            )
        }
    }
}

extension PlaceEditorView {
    private func save() {
        guard let coordinate else {
            return
        }
        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        let saved: Place
        if let place {
            place.name = trimmedName
            place.latitude = coordinate.latitude
            place.longitude = coordinate.longitude
            place.radiusMeters = radiusMeters
            place.category = category
            writes.update(place)
            saved = place
        } else {
            saved = Place(
                name: trimmedName,
                latitude: coordinate.latitude,
                longitude: coordinate.longitude,
                radiusMeters: radiusMeters,
                category: category ?? categories.first { $0.builtin == .other }
            )
            let limit = writes.add(saved, source: source, todoTitles: todoTitles, suggestedCategory: suggestion)
            if let limit {
                paywall = limit.trigger
                return
            }
        }
        onSave?(saved)
        dismiss()
        Task {
            await geofence.sync()
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

    private func nextAfterCategory() {
        if asksForTodos {
            path.append(.todos)
        } else {
            save()
        }
    }

    private func chooseOnMap() {
        if place == nil {
            path.removeAll()
        } else {
            isPickingLocation = true
        }
    }

    private var todoTitles: [String] {
        (todos.map(\.title) + [todoDraft])
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }

    private func categoryLabel(_ category: PlaceCategory?) -> some View {
        let style = CategoryStyle(category)
        return Label(style.name, systemImage: style.systemImage)
    }

    private func locationPreview(_ coordinate: CLLocationCoordinate2D) -> some View {
        let region = MKCoordinateRegion(
            center: coordinate,
            latitudinalMeters: radiusMeters * 4,
            longitudinalMeters: radiusMeters * 4
        )
        return Map(position: .constant(.region(region))) {
            Marker(coordinate: coordinate) {
                Text(.placePickerSelected)
            }
            MapCircle(center: coordinate, radius: radiusMeters)
                .foregroundStyle(.blue.opacity(0.15))
                .stroke(.blue, lineWidth: 1)
        }
        .frame(height: 160)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .listRowInsets(EdgeInsets())
    }

    private var screenParameters: AnalyticsParameters {
        let mode = EditorMode(editing: place)
        if mode == .new {
            return [.mode: mode.rawValue, .step: "details"]
        }
        return [.mode: mode.rawValue]
    }

    // A new pick guesses again, unless the category was already chosen by hand.
    private func suggest(_ guessed: BuiltinCategory?) {
        suggestion = guessed
        if hasChosenCategory {
            return
        }
        category = categories.first { $0.builtin == guessed && guessed != nil }
    }
}
