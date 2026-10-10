import SwiftData
import SwiftUI

struct EditPlaceForm: View {
    @Environment(Navigator.self) private var navigator
    @Environment(LocalWrites.self) private var writes
    @Environment(GeofenceMonitor.self) private var geofence
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \PlaceCategory.sortOrder) private var categories: [PlaceCategory]

    let place: Place
    @State private var draft: PlaceDraft

    init(place: Place) {
        self.place = place
        _draft = State(initialValue: PlaceDraft(editing: place))
    }

    var body: some View {
        NavigationStack {
            PlaceDetailsForm(draft: $draft, isEditing: true, chooseOnMap: chooseOnMap)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(.commonCancel) {
                            dismiss()
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(.commonSave, action: save)
                            .disabled(!draft.canSave)
                    }
                }
        }
    }

    private func chooseOnMap() {
        navigator.present(.pickLocation(PickLocation(initialCoordinate: draft.coordinate) { pick in
            draft.apply(pick, categories: categories)
        }))
    }

    private func save() {
        guard let coordinate = draft.coordinate else {
            return
        }
        place.name = draft.trimmedName
        place.latitude = coordinate.latitude
        place.longitude = coordinate.longitude
        place.radiusMeters = draft.radiusMeters
        place.category = draft.category
        writes.update(place)
        dismiss()
        Task {
            await geofence.sync()
        }
    }
}
