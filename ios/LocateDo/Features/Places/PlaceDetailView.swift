import SwiftUI

struct PlaceDetailView: View {
    let place: Place
    @State private var isEditing = false

    var body: some View {
        Text(place.name)
            .navigationTitle(place.name)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button(.commonEdit) {
                        isEditing = true
                    }
                }
            }
            .sheet(isPresented: $isEditing) {
                PlaceEditorView(place: place)
            }
    }
}
