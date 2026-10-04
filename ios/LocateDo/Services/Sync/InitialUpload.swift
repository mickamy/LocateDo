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
        request.id = ProtoInput.id(householdID)
        request.categories = categories.map(ProtoInput.category)
        request.places = places.map(ProtoInput.place)
        request.todos = todos.compactMap(initialTodo)
        return request
    }

    static func initialTodo(_ todo: Todo) -> Locatedo_Household_V1_InitialTodo? {
        guard let input = ProtoInput.todo(todo) else {
            return nil
        }
        var initial = Locatedo_Household_V1_InitialTodo()
        initial.todo = input
        if let completedAt = todo.completedAt {
            initial.completedAt = Google_Protobuf_Timestamp(date: completedAt)
        }
        return initial
    }
}
