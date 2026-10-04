import MapKit
import SwiftData
import SwiftUI

struct PlaceDetailView: View {
    @Environment(LocalWrites.self) private var writes
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
                    Label(place.categoryStyle.name, systemImage: place.categoryStyle.systemImage)
                        .foregroundStyle(place.categoryStyle.tint)
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
        .syncRefreshable()
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
                    #if DEBUG || STAGING
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
                    Button {
                        Task {
                            await notifier.requestAuthorization()
                            await notifier.notifyArrival(at: place, todoTitles: place.openTodos.map(\.title), after: 10)
                        }
                    } label: {
                        Label {
                            Text(verbatim: "Simulate arrival in 10 s (debug)")
                        } icon: {
                            Image(systemName: "timer")
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
            EmptyView()
        case .found(let found):
            Text(found)
        case .unavailable:
            Text(CoordinateFormatting.string(place.coordinate))
        }
    }

    @ViewBuilder
    private var distanceText: some View {
        if let location = locationProvider.location {
            let distance = DistanceFormatting.string(meters: location.distance(from: place.location))
            Text(.placeDetailDistance(distance))
        }
    }

    private var map: some View {
        let region = MKCoordinateRegion(
            center: place.coordinate,
            latitudinalMeters: place.radiusMeters * 4,
            longitudinalMeters: place.radiusMeters * 4
        )
        return Map(position: .constant(.region(region))) {
            Marker(place.name, systemImage: place.categoryStyle.systemImage, coordinate: place.coordinate)
                .tint(place.categoryStyle.tint)
            MapCircle(center: place.coordinate, radius: place.radiusMeters)
                .foregroundStyle(.blue.opacity(0.15))
                .stroke(.blue, lineWidth: 1)
        }
        .frame(height: 200)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func delete(_ todos: [Todo], at offsets: IndexSet) {
        writes.delete(offsets.map { todos[$0] })
    }

    private func deletePlace() {
        dismiss()
        writes.delete(place)
        Task {
            await geofence.sync()
        }
    }
}
