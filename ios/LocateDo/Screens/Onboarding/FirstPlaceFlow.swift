import CoreLocation
import SwiftData
import SwiftUI

// Onboarding's first place, asked the other way round from adding one: the kind of store, what to do there, and only
// then the store itself, picked from the ones of that kind nearby. Location is asked right before the map needs it.
// Each step carries the store it is about, since state set together with a push is not yet there when the pushed
// screen is built.
struct FirstPlaceFlow: View {
    private enum Step: Hashable {
        case name
        case todos(FirstStore)
        case privacy(FirstStore?)
        case store(FirstStore)
        case done(placeName: String, kind: StoreKind)
    }

    let onFinish: (StoreKind?) -> Void

    @Environment(LocalWrites.self) private var writes
    @Environment(GeofenceMonitor.self) private var geofence
    @Environment(Navigator.self) private var navigator
    @Environment(AppPreferences.self) private var preferences
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @Query(sort: \PlaceCategory.sortOrder) private var categories: [PlaceCategory]
    @State private var todosStore: FirstStore?
    @State private var todos: [DraftTodo] = []
    @State private var todoDraft = ""
    @State private var path: [Step] = []

    var body: some View {
        NavigationStack(path: $path) {
            StoreKindStep { kind in
                if kind == .other {
                    path = [.name]
                } else {
                    path = [.todos(FirstStore(kind: kind))]
                }
            } onLater: {
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

    private var needsSetup: Bool {
        let permissions = ReminderSetup.Permissions(
            location: locationProvider.authorizationStatus,
            preciseLocation: locationProvider.hasPreciseLocation,
            notifications: notifier.authorizationStatus
        )
        return !ReminderSetup.missing(permissions).isEmpty
    }

    @ViewBuilder
    private func destination(_ step: Step) -> some View {
        switch step {
        case .name:
            StoreNameStep { name in
                path.append(.todos(FirstStore(kind: .other, customName: name)))
            }
        case .todos(let store):
            FirstTodosStep(
                store: store,
                todos: $todos,
                draft: $todoDraft,
                remaining: writes.remaining(.openTodos)
            ) {
                path.append(needsLocation ? .privacy(store) : .store(store))
            }
            .onAppear {
                startTodos(for: store)
            }
        case .privacy(let store):
            PrivacyStep(reason: store == nil ? .firstPlaceLaterPermissions : .firstPlacePermissions) {
                guard let store else {
                    onFinish(nil)
                    return
                }
                path.removeLast()
                path.append(.store(store))
            }
        case .store(let store):
            LocationPicker(
                initialCoordinate: nil,
                closesOnPick: false,
                entry: .onboarding,
                title: store.storeTitle,
                initialSearch: store.searchTerm,
                showsCancel: false
            ) { pick in
                save(pick, store: store)
            }
        case .done(let placeName, let kind):
            FirstPlaceDoneStep(placeName: placeName, needsSetup: needsSetup) {
                onFinish(kind)
            }
        }
    }

    // To-dos written for one store do not carry over to another picked after going back.
    private func startTodos(for store: FirstStore) {
        if store != todosStore {
            todos = []
            todoDraft = ""
        }
        todosStore = store
    }

    private var todoTitles: [String] {
        (todos.map(\.title) + [todoDraft])
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }

    // Any other store is guessed from what was picked, as adding a place does; the kinds are all shopping.
    private func category(for pick: PlacePick, kind: StoreKind) -> BuiltinCategory {
        if kind == .other {
            return pick.suggestion ?? .shopping
        }
        return .shopping
    }

    private func save(_ pick: PlacePick, store: FirstStore) {
        let place = Place(
            name: pick.name ?? store.name,
            latitude: pick.coordinate.latitude,
            longitude: pick.coordinate.longitude,
            radiusMeters: preferences.defaultRadiusMeters,
            category: categories.first { $0.builtin == category(for: pick, kind: store.kind) }
        )
        _ = writes.add(
            place,
            source: pick.source,
            todoTitles: todoTitles,
            suggestedCategory: pick.suggestion,
            entry: .onboarding
        )
        navigator.addedPlaceName = place.name
        Task {
            await geofence.sync()
        }
        // Replaces the steps, so going back cannot save the place twice.
        path = [.done(placeName: place.name, kind: store.kind)]
    }
}
