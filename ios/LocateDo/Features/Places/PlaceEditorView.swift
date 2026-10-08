import CoreLocation
import MapKit
import SwiftData
import SwiftUI

struct PlaceEditorView: View {
    @Environment(LocalWrites.self) private var writes
    @Environment(\.dismiss) private var dismiss
    @Environment(GeofenceMonitor.self) private var geofence
    @Query(sort: \PlaceCategory.sortOrder) private var categories: [PlaceCategory]

    let place: Place?
    var onSave: (() -> Void)?
    @State private var name: String
    @State private var coordinate: CLLocationCoordinate2D?
    @State private var source: PlaceSource?
    @State private var radiusMeters: Double
    @State private var category: PlaceCategory?
    @State private var isPickingLocation = false
    @FocusState private var isNameFocused: Bool
    @State private var paywall: PaywallTrigger?

    init(place: Place? = nil, defaultRadiusMeters: Double = Place.defaultRadiusMeters, onSave: (() -> Void)? = nil) {
        self.place = place
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
        NavigationStack {
            Form {
                Section {
                    TextField(text: $name, prompt: Text(.placeEditorNamePlaceholder)) {
                        Text(.placeEditorNameLabel)
                    }
                    .focused($isNameFocused)
                    .submitLabel(.done)
                }
                Section {
                    if let coordinate {
                        locationPreview(coordinate)
                    } else {
                        Text(.placeEditorNoLocation)
                            .foregroundStyle(.secondary)
                    }
                    Button(.placeEditorChooseOnMap, systemImage: "map") {
                        isPickingLocation = true
                    }
                } header: {
                    Text(.placeEditorLocationLabel)
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
            .trackScreen(.placeEditor, parameters: [.mode: EditorMode(editing: place).rawValue])
            .navigationTitle(Text(place == nil ? .placeEditorTitleNew : .placeEditorTitleEdit))
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
            .sheet(isPresented: $isPickingLocation) {
                PlacePickerMapView(initialCoordinate: coordinate) { picked, suggestedName, pickedFrom in
                    coordinate = picked
                    source = pickedFrom
                    if name.isEmpty, let suggestedName {
                        name = suggestedName
                    }
                }
            }
            .onChange(of: categories) {
                if let category, !categories.contains(category) {
                    self.category = nil
                }
            }
            .onAppear {
                if place == nil {
                    category = categories.first { $0.builtin == .other }
                    isNameFocused = true
                }
            }
        }
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

    private func save() {
        guard let coordinate else {
            return
        }
        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        if let place {
            place.name = trimmedName
            place.latitude = coordinate.latitude
            place.longitude = coordinate.longitude
            place.radiusMeters = radiusMeters
            place.category = category
            writes.update(place)
        } else {
            let limit = writes.add(Place(
                name: trimmedName,
                latitude: coordinate.latitude,
                longitude: coordinate.longitude,
                radiusMeters: radiusMeters,
                category: category
            ), source: source)
            if let limit {
                paywall = limit.trigger
                return
            }
        }
        onSave?()
        dismiss()
        Task {
            await geofence.sync()
        }
    }
}
