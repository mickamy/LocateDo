import CoreLocation
import MapKit
import SwiftData
import SwiftUI

// Where, what it is called, and how close counts. Editing also picks the category here; adding has its own step.
struct PlaceDetailsForm: View {
    @Query(sort: \PlaceCategory.sortOrder) private var categories: [PlaceCategory]
    @Binding var draft: PlaceDraft
    let isEditing: Bool
    let chooseOnMap: () -> Void

    var body: some View {
        Form {
            Section {
                if let coordinate = draft.coordinate {
                    LocationPreview(coordinate: coordinate, radiusMeters: draft.radiusMeters)
                } else {
                    Text(.placeEditorNoLocation)
                        .foregroundStyle(.secondary)
                }
                Button(.placeEditorChooseOnMap, systemImage: "map", action: chooseOnMap)
            } header: {
                Text(.placeEditorLocationLabel)
            }
            Section {
                TextField(text: $draft.name, prompt: Text(.placeEditorNamePlaceholder)) {
                    Text(.placeEditorNameLabel)
                }
                .submitLabel(.done)
            } header: {
                Text(.placeEditorNameLabel)
            }
            Section {
                Slider(value: $draft.radiusMeters, in: Place.radiusRange, step: 50) {
                    Text(.placeEditorRadiusLabel)
                }
                .accessibilityValue(Text(DistanceFormatting.string(meters: draft.radiusMeters)))
                Text(DistanceFormatting.string(meters: draft.radiusMeters))
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .foregroundStyle(.secondary)
                    .accessibilityHidden(true)
            } header: {
                Text(.placeEditorRadiusLabel)
            }
            if isEditing {
                categorySection
            }
        }
        .trackScreen(.placeEditor, parameters: screenParameters)
        .navigationTitle(Text(isEditing ? .placeEditorTitleEdit : .placeEditorTitleNew))
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: categories) {
            if let category = draft.category, !categories.contains(category) {
                draft.category = nil
            }
        }
    }

    private var categorySection: some View {
        Section {
            Picker(selection: $draft.category) {
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
                CategoriesScreen()
            } label: {
                Label(.categoryManage, systemImage: "slider.horizontal.3")
            }
        } header: {
            Text(.placeEditorCategoryLabel)
        }
    }

    private func categoryLabel(_ category: PlaceCategory?) -> some View {
        let style = CategoryStyle(category)
        return Label(style.name, systemImage: style.systemImage)
    }

    private var screenParameters: AnalyticsParameters {
        if isEditing {
            return [.mode: EditorMode.edit.rawValue]
        }
        return [.mode: EditorMode.new.rawValue, .step: "details"]
    }
}

private struct LocationPreview: View {
    let coordinate: CLLocationCoordinate2D
    let radiusMeters: Double

    var body: some View {
        let region = MKCoordinateRegion(
            center: coordinate,
            latitudinalMeters: radiusMeters * 4,
            longitudinalMeters: radiusMeters * 4
        )
        Map(position: .constant(.region(region))) {
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
}
