import Foundation
import SwiftProtobuf

struct WriteSender {
    let places: any Locatedo_Place_V1_PlaceServiceClientInterface
    let todos: any Locatedo_Todo_V1_TodoServiceClientInterface
    let categories: any Locatedo_Category_V1_CategoryServiceClientInterface
    let authenticator: Authenticator

    func send(_ write: Write, householdID: String) async throws {
        switch write {
        case .putPlace(let input):
            let request = Locatedo_Place_V1_PutPlaceRequest.with {
                $0.householdID = householdID
                $0.place = input
            }
            let client = places
            _ = try await authenticator.authorized { await client.putPlace(request: request, headers: [:]) }
        case .deletePlace(let request):
            let client = places
            _ = try await authenticator.authorized { await client.deletePlace(request: request, headers: [:]) }
        case .putTodo(let input):
            let request = Locatedo_Todo_V1_PutTodoRequest.with {
                $0.householdID = householdID
                $0.todo = input
            }
            let client = todos
            _ = try await authenticator.authorized { await client.putTodo(request: request, headers: [:]) }
        case .setTodoCompletion(let request):
            let client = todos
            _ = try await authenticator.authorized { await client.setTodoCompletion(request: request, headers: [:]) }
        case .deleteTodo(let request):
            let client = todos
            _ = try await authenticator.authorized { await client.deleteTodo(request: request, headers: [:]) }
        case .putCategory(let input):
            let request = Locatedo_Category_V1_PutCategoryRequest.with {
                $0.householdID = householdID
                $0.category = input
            }
            let client = categories
            _ = try await authenticator.authorized { await client.putCategory(request: request, headers: [:]) }
        case .deleteCategory(let request):
            let client = categories
            _ = try await authenticator.authorized { await client.deleteCategory(request: request, headers: [:]) }
        }
    }
}
