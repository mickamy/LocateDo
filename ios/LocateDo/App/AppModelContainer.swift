import SwiftData

enum AppModelContainer {
    static let schema = Schema([Place.self, Todo.self, PlaceCategory.self, SyncState.self, PendingWrite.self])

    static func make(inMemory: Bool = false) throws -> ModelContainer {
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: inMemory)
        let container = try ModelContainer(for: schema, configurations: [configuration])
        try PlaceCategory.insertBuiltinsIfEmpty(into: ModelContext(container))
        return container
    }
}
