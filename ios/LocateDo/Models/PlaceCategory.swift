import Foundation
import SwiftData

@Model
final class PlaceCategory {
    @Attribute(.unique) var id: UUID
    var builtin: BuiltinCategory?
    var name: String?
    var icon: String
    var color: String
    var sortOrder: Int
    var updatedAt: Date

    @Relationship(deleteRule: .nullify, inverse: \Place.category)
    var places: [Place] = []

    init(
        id: UUID = .v7(),
        builtin: BuiltinCategory? = nil,
        name: String? = nil,
        icon: String,
        color: String,
        sortOrder: Int,
        now: Date = .now
    ) {
        self.id = id
        self.builtin = builtin
        self.name = name
        self.icon = icon
        self.color = color
        self.sortOrder = sortOrder
        updatedAt = now
    }

    convenience init(builtin: BuiltinCategory, sortOrder: Int) {
        self.init(builtin: builtin, icon: builtin.icon, color: builtin.color, sortOrder: sortOrder)
    }

    var displayName: String {
        if let name {
            return name
        }
        if let builtin {
            return String(localized: builtin.title)
        }
        return ""
    }

    static func insertBuiltinsIfEmpty(into context: ModelContext) throws {
        guard try context.fetchCount(FetchDescriptor<PlaceCategory>()) == 0 else {
            return
        }
        for (index, builtin) in BuiltinCategory.allCases.enumerated() {
            context.insert(PlaceCategory(builtin: builtin, sortOrder: index))
        }
        try context.save()
    }
}
