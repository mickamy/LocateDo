import MapKit
import SwiftData
import SwiftUI

struct PlaceDetailView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(GeofenceMonitor.self) private var geofence
    @Environment(ArrivalNotifier.self) private var notifier

    private enum Address {
        case loading
        case found(String)
        case unavailable
    }

    let place: Place
    @Query private var todos: [Todo]
    @State private var address: Address = .loading
    @State private var isEditing = false
    @State private var isAddingTodo = false
    @State private var isConfirmingDelete = false

    init(place: Place) {
        self.place = place
        let placeID = place.id
        _todos = Query(filter: #Predicate<Todo> { $0.place?.id == placeID }, sort: \Todo.createdAt)
    }

    var body: some View {
        List {
            Section {
                map
                    .listRowInsets(EdgeInsets())
                VStack(alignment: .leading, spacing: 4) {
                    Label(place.category.title, systemImage: place.category.systemImage)
                        .foregroundStyle(place.category.tint)
                    Group {
                        addressText
                        distanceText
                    }
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                }
            }
            Section {
                if todos.isEmpty {
                    Text(.placeDetailNoTodos)
                        .foregroundStyle(.secondary)
                }
                ForEach(todos.open) { todo in
                    TodoRow(todo: todo)
                }
                .onDelete { offsets in
                    delete(todos.open, at: offsets)
                }
                Button(.placeDetailAddTodo, systemImage: "plus") {
                    isAddingTodo = true
                }
            } header: {
                Text(.placeDetailTodosLabel)
            }
            if !todos.completedNewestFirst.isEmpty {
                Section {
                    ForEach(todos.completedNewestFirst) { todo in
                        TodoRow(todo: todo)
                    }
                    .onDelete { offsets in
                        delete(todos.completedNewestFirst, at: offsets)
                    }
                } header: {
                    Text(.todoCompletedSection)
                }
            }
        }
        .navigationTitle(place.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button(.commonEdit, systemImage: "pencil") {
                        isEditing = true
                    }
                    Button(.placeDetailDelete, systemImage: "trash", role: .destructive) {
                        isConfirmingDelete = true
                    }
                    #if DEBUG
                    Button {
                        Task {
                            await notifier.requestAuthorization()
                            await geofence.simulateArrival(at: place)
                        }
                    } label: {
                        Label {
                            Text(verbatim: "Simulate arrival (debug)")
                        } icon: {
                            Image(systemName: "location.fill.viewfinder")
                        }
                    }
                    #endif
                } label: {
                    Label(.commonMore, systemImage: "ellipsis.circle")
                }
            }
        }
        .confirmationDialog(
            Text(.placeDetailDeleteConfirmTitle(place.name)),
            isPresented: $isConfirmingDelete,
            titleVisibility: .visible
        ) {
            Button(.commonDelete, role: .destructive) {
                deletePlace()
            }
        } message: {
            Text(.placeDetailDeleteConfirmMessage)
        }
        .sheet(isPresented: $isEditing) {
            PlaceEditorView(place: place)
        }
        .sheet(isPresented: $isAddingTodo) {
            TodoEditorView(place: place)
        }
        .task(id: "\(place.latitude),\(place.longitude)") {
            address = .loading
            if let found = await Geocoding.lookUp(place.coordinate)?.address {
                address = .found(found)
            } else {
                address = .unavailable
            }
        }
    }

    @ViewBuilder
    private var addressText: some View {
        switch address {
        case .loading:
            Text(coordinateString)
                .redacted(reason: .placeholder)
                .accessibilityHidden(true)
        case .found(let found):
            Text(found)
        case .unavailable:
            Text(coordinateString)
        }
    }

    @ViewBuilder
    private var distanceText: some View {
        if let location = locationProvider.location {
            let distance = DistanceFormatting.string(meters: location.distance(from: place.location))
            Text(.placeDetailDistance(distance))
        } else if locationProvider.isAwaitingLocation {
            Text(.placeDetailDistance(DistanceFormatting.placeholder()))
                .redacted(reason: .placeholder)
                .accessibilityHidden(true)
        }
    }

    private var coordinateString: String {
        CoordinateFormatting.string(place.coordinate)
    }

    private var map: some View {
        let region = MKCoordinateRegion(
            center: place.coordinate,
            latitudinalMeters: place.radiusMeters * 4,
            longitudinalMeters: place.radiusMeters * 4
        )
        return Map(position: .constant(.region(region))) {
            Marker(place.name, systemImage: place.category.systemImage, coordinate: place.coordinate)
                .tint(place.category.tint)
            MapCircle(center: place.coordinate, radius: place.radiusMeters)
                .foregroundStyle(.blue.opacity(0.15))
                .stroke(.blue, lineWidth: 1)
        }
        .frame(height: 200)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func delete(_ todos: [Todo], at offsets: IndexSet) {
        for index in offsets {
            modelContext.delete(todos[index])
        }
    }

    private func deletePlace() {
        dismiss()
        modelContext.delete(place)
        try? modelContext.save()
        Task {
            await geofence.sync()
        }
    }
}
