import Foundation
import SwiftProtobuf

nonisolated enum InitialUpload {
    static func request(
        householdID: UUID,
        categories: [PlaceCategory],
        places: [Place],
        todos: [Todo]
    ) -> Locatedo_Household_V1_CreateHouseholdRequest {
        var request = Locatedo_Household_V1_CreateHouseholdRequest()
        request.id = householdID.uuidString.lowercased()
        request.categories = categories.map(categoryInput)
        request.places = places.map(placeInput)
        request.todos = todos.compactMap(initialTodo)
        return request
    }

    static func categoryInput(_ category: PlaceCategory) -> Locatedo_Category_V1_CategoryInput {
        var input = Locatedo_Category_V1_CategoryInput()
        input.id = category.id.uuidString.lowercased()
        input.builtin = builtin(category.builtin)
        if let name = category.name {
            input.name = name
        }
        input.icon = category.icon
        input.color = category.color
        input.sortOrder = Int32(category.sortOrder)
        return input
    }

    static func placeInput(_ place: Place) -> Locatedo_Place_V1_PlaceInput {
        var input = Locatedo_Place_V1_PlaceInput()
        input.id = place.id.uuidString.lowercased()
        input.name = place.name
        input.lat = place.latitude
        input.lng = place.longitude
        input.radiusM = Int32(place.radiusMeters.rounded())
        if let category = place.category {
            input.categoryID = category.id.uuidString.lowercased()
        }
        input.sortOrder = Int32(place.sortOrder)
        return input
    }

    static func initialTodo(_ todo: Todo) -> Locatedo_Household_V1_InitialTodo? {
        guard let place = todo.place else {
            return nil
        }
        var input = Locatedo_Todo_V1_TodoInput()
        input.id = todo.id.uuidString.lowercased()
        input.placeID = place.id.uuidString.lowercased()
        input.title = todo.title
        if let assigneeID = todo.assigneeID {
            input.assigneeID = assigneeID.uuidString.lowercased()
        }
        var initial = Locatedo_Household_V1_InitialTodo()
        initial.todo = input
        if let completedAt = todo.completedAt {
            initial.completedAt = Google_Protobuf_Timestamp(date: completedAt)
        }
        return initial
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
