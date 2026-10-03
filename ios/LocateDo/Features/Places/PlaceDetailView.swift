import MapKit
import SwiftData
import SwiftUI

struct PlaceDetailView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(GeofenceMonitor.self) private var geofence
    @Environment(ArrivalNotifier.self) private var notifier

    let place: Place
    @Query private var todos: [Todo]
    @State private var address: String?
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
                    if let address {
                        Text(address)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    if let location = locationProvider.location {
                        Text(.placeDetailDistance(DistanceFormatting.string(meters: location.distance(from: place.location))))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
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
            address = await Geocoding.lookUp(place.coordinate)?.address
        }
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
