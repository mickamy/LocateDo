import CoreLocation
import SwiftData
import SwiftUI

// Onboarding's first place, asked the other way round from adding one: the kind of store, what to do there, and only
// then the store itself, picked from the ones of that kind nearby. Location is asked right before the map needs it.
struct FirstPlaceFlow: View {
    private enum Step: Hashable {
        case todos(StoreKind)
        case privacy(StoreKind?)
        case store(StoreKind)
    }

    let onFinish: (StoreKind?) -> Void

    @Environment(LocalWrites.self) private var writes
    @Environment(GeofenceMonitor.self) private var geofence
    @Environment(Navigator.self) private var navigator
    @Environment(AppPreferences.self) private var preferences
    @Environment(LocationProvider.self) private var locationProvider
    @Query(sort: \PlaceCategory.sortOrder) private var categories: [PlaceCategory]
    @State private var kind: StoreKind?
    @State private var todos: [DraftTodo] = []
    @State private var todoDraft = ""
    @State private var path: [Step] = []

    var body: some View {
        NavigationStack(path: $path) {
            StoreKindStep { picked in
                if picked != kind {
                    todos = []
                    todoDraft = ""
                }
                kind = picked
                path = [.todos(picked)]
            } onLater: {
                kind = nil
                if needsLocation {
                    path = [.privacy(nil)]
                } else {
                    onFinish(nil)
                }
            }
            .navigationDestination(for: Step.self, destination: destination)
        }
    }

    private var needsLocation: Bool {
        locationProvider.authorizationStatus == .notDetermined
    }

    @ViewBuilder
    private func destination(_ step: Step) -> some View {
        switch step {
        case .todos(let kind):
            FirstTodosStep(
                kind: kind,
                todos: $todos,
                draft: $todoDraft,
                remaining: writes.remaining(.openTodos)
            ) {
                path.append(needsLocation ? .privacy(kind) : .store(kind))
            }
        case .privacy(let kind):
            PrivacyStep(reason: kind == nil ? .firstPlaceLaterPermissions : .firstPlacePermissions) {
                guard let kind else {
                    onFinish(nil)
                    return
                }
                path = [.todos(kind), .store(kind)]
            }
        case .store(let kind):
            LocationPicker(
                initialCoordinate: nil,
                closesOnPick: false,
                entry: .onboarding,
                title: kind.storeTitle,
                initialSearch: kind.searchTerm,
                showsCancel: false
            ) { pick in
                save(pick, kind: kind)
            }
        }
    }

    private var todoTitles: [String] {
        (todos.map(\.title) + [todoDraft])
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }

    private func save(_ pick: PlacePick, kind: StoreKind) {
        let place = Place(
            name: pick.name ?? String(localized: kind.name),
            latitude: pick.coordinate.latitude,
            longitude: pick.coordinate.longitude,
            radiusMeters: preferences.defaultRadiusMeters,
            category: categories.first { $0.builtin == .shopping }
        )
        _ = writes.add(
            place,
            source: pick.source,
            todoTitles: todoTitles,
            suggestedCategory: pick.suggestion,
            entry: .onboarding
        )
        navigator.didAddPlace = true
        Task {
            await geofence.sync()
        }
        onFinish(kind)
    }
}
