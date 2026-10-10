import MapKit
import SwiftData
import SwiftUI
import TipKit

struct PlaceScreen: View {
    private enum Address {
        case loading
        case found(String)
        case unavailable
    }

    @Environment(Navigator.self) private var navigator
    @Environment(LocalWrites.self) private var writes
    @Environment(TodoUndo.self) private var undo
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(GeofenceMonitor.self) private var geofence
    @Environment(\.dismiss) private var dismiss

    let place: Place
    @Query private var todos: [Todo]
    @State private var address: Address = .loading
    private let checkOffTip = CheckOffTip()

    init(place: Place) {
        self.place = place
        let placeID = place.id
        _todos = Query(filter: #Predicate<Todo> { $0.place?.id == placeID }, sort: \Todo.createdAt)
    }

    var body: some View {
        List {
            Section {
                PlaceMap(place: place)
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
                TipView(checkOffTip)
                if todos.isEmpty {
                    Text(.placeDetailNoTodos)
                        .foregroundStyle(.secondary)
                }
                todoRows(todos.open)
            } header: {
                Text(.placeDetailTodosLabel)
            }
            if !todos.completedNewestFirst.isEmpty {
                Section {
                    todoRows(todos.completedNewestFirst)
                } header: {
                    HStack {
                        Text(.todoCompletedSection)
                        Spacer()
                        DeleteCompletedButton(todos: todos.completedNewestFirst)
                    }
                }
            }
        }
        .syncRefreshable()
        .trackScreen(.placeDetail)
        .navigationTitle(place.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button(.commonEdit, systemImage: "pencil") {
                        navigator.present(.editPlace(place))
                    }
                    Button(.placeDetailDelete, systemImage: "trash", role: .destructive, action: confirmDelete)
                    #if DEBUG || STAGING
                    PlaceDebugActions(place: place)
                    #endif
                } label: {
                    Label(.commonMore, systemImage: "ellipsis.circle")
                }
                .accessibilityIdentifier("place.menu")
            }
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

    private func todoRows(_ rows: [Todo]) -> some View {
        ForEach(rows) { todo in
            TodoRow(todo: todo)
        }
        .onDelete { offsets in
            undo.offer(writes.delete(offsets.map { rows[$0] }, via: .swipe))
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

    private func confirmDelete() {
        navigator.confirm(Confirmation(
            title: String(localized: .placeDetailDeleteConfirmTitle(place.name)),
            message: String(localized: .placeDetailDeleteConfirmMessage),
            actionTitle: .commonDelete,
            action: deletePlace
        ))
    }

    private func deletePlace() {
        dismiss()
        writes.delete(place)
        Task {
            await geofence.sync()
        }
    }
}

private struct PlaceMap: View {
    let place: Place

    var body: some View {
        let region = MKCoordinateRegion(
            center: place.coordinate,
            latitudinalMeters: place.radiusMeters * 4,
            longitudinalMeters: place.radiusMeters * 4
        )
        Map(position: .constant(.region(region))) {
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
}
