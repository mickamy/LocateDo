#if DEBUG
import Foundation

// The Watch side of ScreenshotSeed: the same places, without the to-do assigned to the partner, since the Watch only
// shows what is notified to me. The simulator has no paired iPhone, so the snapshot is made up here.
enum WatchScreenshotSeed {
    static let argument = "-SeedScreenshotData"
    static let arrivalArgument = "-ShowArrivalNotification"

    static var isRequested: Bool {
        ProcessInfo.processInfo.arguments.contains(argument)
    }

    static var showsArrival: Bool {
        ProcessInfo.processInfo.arguments.contains(arrivalArgument)
    }

    private static var japanese: Bool {
        Bundle.main.preferredLocalizations.first == "ja"
    }

    static var snapshot: WatchSnapshot {
        WatchSnapshot(places: places.map { entry in
            WatchSnapshot.Place(
                id: UUID(),
                name: entry.name,
                categoryIcon: entry.icon,
                categoryColor: entry.color,
                todos: entry.todos.map { WatchSnapshot.Todo(id: UUID(), title: $0) }
            )
        })
    }

    static var arrival: (title: String, checklist: ArrivalChecklist) {
        let place = places[0]
        let checklist = ArrivalChecklist(
            items: place.todos.map { ArrivalChecklist.Item(id: UUID(), title: $0) },
            categoryIcon: place.icon,
            categoryColor: place.color
        )
        return (place.name, checklist)
    }

    private struct Entry {
        let name: String
        let icon: String
        let color: String
        let todos: [String]
    }

    private static var places: [Entry] {
        if japanese {
            return [
                Entry(name: "スーパー", icon: "cart", color: "green", todos: ["牛乳", "食パン"]),
                Entry(name: "ドラッグストア", icon: "cart", color: "green", todos: ["日焼け止め", "歯みがき粉"]),
                Entry(name: "ホームセンター", icon: "house", color: "orange", todos: ["電球"]),
                Entry(name: "会社", icon: "briefcase", color: "blue", todos: ["経費の書類を出す"])
            ]
        }
        return [
            Entry(name: "Grocery store", icon: "cart", color: "green", todos: ["Milk", "Avocados"]),
            Entry(name: "Pharmacy", icon: "cart", color: "green", todos: ["Sunscreen", "Toothpaste"]),
            Entry(name: "Hardware store", icon: "house", color: "orange", todos: ["Light bulbs"]),
            Entry(name: "Office", icon: "briefcase", color: "blue", todos: ["Hand in the expense report"])
        ]
    }
}
#endif
