#if DEBUG
import Foundation
import SwiftData

// Replaces local data with a tidy example for App Store screenshots and the review video.
enum ScreenshotSeed {
    static let argument = "-SeedScreenshotData"

    private struct Entry {
        let name: String
        let builtin: BuiltinCategory
        let latitudeOffset: Double
        let longitudeOffset: Double
        let todos: [String]
    }

    static func replaceIfRequested(in context: ModelContext, arguments: [String] = ProcessInfo.processInfo.arguments) {
        guard arguments.contains(argument) else {
            return
        }
        do {
            try replace(in: context, japanese: Bundle.main.preferredLocalizations.first == "ja")
        } catch {
            fatalError("Could not seed screenshot data: \(error)")
        }
    }

    static func replace(in context: ModelContext, japanese: Bool) throws {
        // Batch deletes cannot follow the category relationship, so rows go one by one.
        for todo in try context.fetch(FetchDescriptor<Todo>()) {
            context.delete(todo)
        }
        for place in try context.fetch(FetchDescriptor<Place>()) {
            context.delete(place)
        }
        for write in try context.fetch(FetchDescriptor<PendingWrite>()) {
            context.delete(write)
        }
        let categories = try context.fetch(FetchDescriptor<PlaceCategory>())
        let (latitude, longitude) = center(japanese: japanese)
        for (index, entry) in entries(japanese: japanese).enumerated() {
            let place = Place(
                name: entry.name,
                latitude: latitude + entry.latitudeOffset,
                longitude: longitude + entry.longitudeOffset,
                radiusMeters: 150,
                category: categories.first { $0.builtin == entry.builtin },
                sortOrder: index
            )
            context.insert(place)
            for title in entry.todos {
                let todo = Todo(title: title, place: place)
                context.insert(todo)
                place.todos.append(todo)
            }
        }
        try context.save()
    }

    static func center(japanese: Bool) -> (Double, Double) {
        if japanese {
            return (35.6437, 139.6710)
        }
        return (37.3230, -122.0322)
    }

    private static func entries(japanese: Bool) -> [Entry] {
        if japanese {
            return [
                Entry(name: "スーパー", builtin: .shopping, latitudeOffset: 0.0030, longitudeOffset: 0,
                      todos: ["牛乳", "卵", "食パン"]),
                Entry(name: "ドラッグストア", builtin: .shopping, latitudeOffset: -0.0045, longitudeOffset: 0.0040,
                      todos: ["日焼け止め", "歯みがき粉"]),
                Entry(name: "ホームセンター", builtin: .life, latitudeOffset: 0.0110, longitudeOffset: -0.0060,
                      todos: ["電球"]),
                Entry(name: "会社", builtin: .work, latitudeOffset: -0.0150, longitudeOffset: 0.0100,
                      todos: ["経費の書類を出す"])
            ]
        }
        return [
            Entry(name: "Grocery store", builtin: .shopping, latitudeOffset: 0.0030, longitudeOffset: 0,
                  todos: ["Milk", "Eggs", "Avocados"]),
            Entry(name: "Pharmacy", builtin: .shopping, latitudeOffset: -0.0045, longitudeOffset: 0.0040,
                  todos: ["Sunscreen", "Toothpaste"]),
            Entry(name: "Hardware store", builtin: .life, latitudeOffset: 0.0110, longitudeOffset: -0.0060,
                  todos: ["Light bulbs"]),
            Entry(name: "Office", builtin: .work, latitudeOffset: -0.0150, longitudeOffset: 0.0100,
                  todos: ["Hand in the expense report"])
        ]
    }
}
#endif
