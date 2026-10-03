import Foundation
import SwiftData

@Model
final class Todo {
    @Attribute(.unique) var id: UUID
    var title: String
    var place: Place?
    var completedAt: Date?
    var createdAt: Date
    var updatedAt: Date

    init(id: UUID = .v7(), title: String, place: Place, now: Date = .now) {
        self.id = id
        self.title = title
        self.place = place
        createdAt = now
        updatedAt = now
    }

    var isCompleted: Bool {
        completedAt != nil
    }

    func complete(at now: Date = .now) {
        completedAt = now
        updatedAt = now
    }

    func reopen(at now: Date = .now) {
        completedAt = nil
        updatedAt = now
    }
}
