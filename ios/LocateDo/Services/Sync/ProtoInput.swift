import Foundation

nonisolated enum ProtoInput {
    static func id(_ id: UUID) -> String {
        id.uuidString.lowercased()
    }

    static func category(_ category: PlaceCategory) -> Locatedo_Category_V1_CategoryInput {
        var input = Locatedo_Category_V1_CategoryInput()
        input.id = id(category.id)
        input.builtin = builtin(category.builtin)
        if let name = category.name {
            input.name = name
        }
        input.icon = category.icon
        input.color = category.color
        input.sortOrder = Int32(category.sortOrder)
        return input
    }

    static func place(_ place: Place) -> Locatedo_Place_V1_PlaceInput {
        var input = Locatedo_Place_V1_PlaceInput()
        input.id = id(place.id)
        input.name = place.name
        input.lat = place.latitude
        input.lng = place.longitude
        input.radiusM = Int32(place.radiusMeters.rounded())
        if let category = place.category {
            input.categoryID = id(category.id)
        }
        input.sortOrder = Int32(place.sortOrder)
        return input
    }

    static func todo(_ todo: Todo) -> Locatedo_Todo_V1_TodoInput? {
        guard let place = todo.place else {
            return nil
        }
        var input = Locatedo_Todo_V1_TodoInput()
        input.id = id(todo.id)
        input.placeID = id(place.id)
        input.title = todo.title
        if let assigneeID = todo.assigneeID {
            input.assigneeID = id(assigneeID)
        }
        input.trigger.event = placeEvent(todo.placeEvent)
        return input
    }

    private static func placeEvent(_ event: PlaceEvent) -> Locatedo_Todo_V1_PlaceEvent {
        switch event {
        case .arrival: .arrival
        case .departure: .departure
        }
    }

    private static func builtin(_ builtin: BuiltinCategory?) -> Locatedo_Category_V1_BuiltinCategory {
        switch builtin {
        case .shopping: .shopping
        case .work: .work
        case .life: .life
        case .other: .other
        case nil: .unspecified
        }
    }
}
