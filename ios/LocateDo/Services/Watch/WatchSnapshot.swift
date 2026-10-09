import Foundation

// Shared with the Watch app, which shows what iPhone last sent and never writes.
nonisolated struct WatchSnapshot: Codable, Equatable {
    struct Place: Codable, Hashable, Identifiable {
        let id: UUID
        let name: String
        let categoryIcon: String?
        let categoryColor: String?
        let todos: [Todo]
    }

    struct Todo: Codable, Hashable, Identifiable {
        let id: UUID
        let title: String
    }

    static let contextKey = "snapshot"

    let places: [Place]

    init(places: [Place]) {
        self.places = places
    }

    init?(applicationContext: [String: Any]) {
        guard let data = applicationContext[Self.contextKey] as? Data,
              let snapshot = try? JSONDecoder().decode(Self.self, from: data) else {
            return nil
        }
        self = snapshot
    }

    var applicationContext: [String: Any] {
        guard let data = try? JSONEncoder().encode(self) else {
            return [:]
        }
        return [Self.contextKey: data]
    }

    func removing(_ todoIDs: Set<UUID>) -> WatchSnapshot {
        var kept: [Place] = []
        for place in places {
            let todos = place.todos.filter { !todoIDs.contains($0.id) }
            if todos.isEmpty {
                continue
            }
            kept.append(Place(
                id: place.id,
                name: place.name,
                categoryIcon: place.categoryIcon,
                categoryColor: place.categoryColor,
                todos: todos
            ))
        }
        return WatchSnapshot(places: kept)
    }
}
