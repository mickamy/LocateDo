import Connect
import Foundation
import SwiftProtobuf
import Synchronization

@testable import LocateDo

nonisolated final class FakeWriteServices: Locatedo_Place_V1_PlaceServiceClientInterface,
    Locatedo_Todo_V1_TodoServiceClientInterface,
    Locatedo_Category_V1_CategoryServiceClientInterface {
    private struct State {
        var sent: [String] = []
        var householdIDs: [String] = []
        var failures: [Code] = []
    }

    private let state = Mutex(State())

    var sent: [String] {
        state.withLock { $0.sent }
    }

    var householdIDs: [String] {
        state.withLock { $0.householdIDs }
    }

    func fail(with codes: [Code]) {
        state.withLock { $0.failures = codes }
    }

    func putPlace(
        request: Locatedo_Place_V1_PutPlaceRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Place_V1_PutPlaceResponse> {
        respond("putPlace", householdID: request.householdID)
    }

    func deletePlace(
        request: Locatedo_Place_V1_DeletePlaceRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Place_V1_DeletePlaceResponse> {
        respond("deletePlace")
    }

    func putTodo(
        request: Locatedo_Todo_V1_PutTodoRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Todo_V1_PutTodoResponse> {
        respond("putTodo", householdID: request.householdID)
    }

    func setTodoCompletion(
        request: Locatedo_Todo_V1_SetTodoCompletionRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Todo_V1_SetTodoCompletionResponse> {
        respond("setTodoCompletion")
    }

    func deleteTodo(
        request: Locatedo_Todo_V1_DeleteTodoRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Todo_V1_DeleteTodoResponse> {
        respond("deleteTodo")
    }

    func putCategory(
        request: Locatedo_Category_V1_PutCategoryRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Category_V1_PutCategoryResponse> {
        respond("putCategory", householdID: request.householdID)
    }

    func deleteCategory(
        request: Locatedo_Category_V1_DeleteCategoryRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Category_V1_DeleteCategoryResponse> {
        respond("deleteCategory")
    }

    private func respond<Output: ProtobufMessage>(
        _ name: String,
        householdID: String? = nil
    ) -> ResponseMessage<Output> {
        let failure = state.withLock { state -> Code? in
            state.sent.append(name)
            if let householdID {
                state.householdIDs.append(householdID)
            }
            if state.failures.isEmpty {
                return nil
            }
            return state.failures.removeFirst()
        }
        if let failure {
            return ResponseMessage(result: .failure(ConnectError(code: failure, message: nil)))
        }
        return ResponseMessage(result: .success(Output()))
    }
}

nonisolated final class FakeSyncService: Locatedo_Sync_V1_SyncServiceClientInterface {
    private struct State {
        var pages: [Locatedo_Sync_V1_PullResponse] = []
        var failAfter: Int?
        var failureCode = Code.unavailable
        var cursors: [Int64] = []
        var householdIDs: [String] = []
    }

    private let state = Mutex(State())

    var cursors: [Int64] {
        state.withLock { $0.cursors }
    }

    var householdIDs: [String] {
        state.withLock { $0.householdIDs }
    }

    func respond(with pages: [Locatedo_Sync_V1_PullResponse]) {
        state.withLock { $0.pages = pages }
    }

    func fail(afterPages count: Int, with code: Code = .unavailable) {
        state.withLock { state in
            state.failAfter = count
            state.failureCode = code
        }
    }

    func pull(
        request: Locatedo_Sync_V1_PullRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Sync_V1_PullResponse> {
        state.withLock { state in
            state.cursors.append(request.cursor)
            state.householdIDs.append(request.householdID)
            if let failAfter = state.failAfter, state.cursors.count > failAfter {
                return ResponseMessage(result: .failure(ConnectError(code: state.failureCode, message: nil)))
            }
            if state.pages.isEmpty {
                var empty = Locatedo_Sync_V1_PullResponse()
                empty.cursor = request.cursor
                return ResponseMessage(result: .success(empty))
            }
            return ResponseMessage(result: .success(state.pages.removeFirst()))
        }
    }
}
