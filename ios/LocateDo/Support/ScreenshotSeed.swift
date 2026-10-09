#if DEBUG
import Foundation
import SwiftData
import UserNotifications

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

    private struct Household {
        let me: String
        let partner: String
        let assigned: String
        let completed: String
        // The server's completion notice for the completed to-do.
        let completionNotice: String
    }

    private static func household(japanese: Bool) -> Household {
        if japanese {
            return Household(me: "たかし", partner: "ゆき", assigned: "卵", completed: "トイレットペーパー",
                             completionNotice: "ゆきが「トイレットペーパー」を完了しました")
        }
        return Household(me: "Sam", partner: "Alex", assigned: "Eggs", completed: "Paper towels",
                         completionNotice: "Alex checked off \"Paper towels\"")
    }

    static func scheduleCompletionNotice(after delay: TimeInterval) async {
        let center = UNUserNotificationCenter.current()
        center.removeAllDeliveredNotifications()
        let content = UNMutableNotificationContent()
        content.body = household(japanese: Bundle.main.preferredLocalizations.first == "ja").completionNotice
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: delay, repeats: false)
        let request = UNNotificationRequest(identifier: "screenshot-completion", content: content, trigger: trigger)
        try? await center.add(request)
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
        for membership in try context.fetch(FetchDescriptor<Membership>()) {
            context.delete(membership)
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
            if index == 0 {
                addHousehold(household(japanese: japanese), at: place, in: context)
            }
        }
        try context.save()
    }

    // Two members, so the grocery store shows an assignee and who checked something off.
    private static func addHousehold(_ household: Household, at place: Place, in context: ModelContext) {
        let now = Date.now
        let partnerID = UUID.v7()
        context.insert(Membership(userID: .v7(), role: .owner, displayName: household.me, joinedAt: now,
                                  updatedAt: now))
        context.insert(Membership(userID: partnerID, role: .member, displayName: household.partner, joinedAt: now,
                                  updatedAt: now))
        place.todos.first { $0.title == household.assigned }?.assigneeID = partnerID
        let completed = Todo(title: household.completed, place: place)
        context.insert(completed)
        place.todos.append(completed)
        completed.complete(by: partnerID, at: now.addingTimeInterval(-600))
    }

    // Chosen so the grocery store, the place the screenshots open, sits on a public landmark and its looked-up address
    // is not someone's home: Tokyo Station and San Francisco City Hall.
    static func center(japanese: Bool) -> (Double, Double) {
        if japanese {
            return (35.67824, 139.76712)
        }
        return (37.77627, -122.41924)
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
