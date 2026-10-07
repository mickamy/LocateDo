import Foundation
import SwiftProtobuf
import Testing

@testable import LocateDo

struct InitialUploadTests {
    @Test func mapsCategoriesPlacesAndTodos() {
        let shopping = PlaceCategory(builtin: .shopping, sortOrder: 0)
        let custom = PlaceCategory(name: "Gym", icon: "dumbbell", color: "teal", sortOrder: 4)
        let place = Place(name: "Store", latitude: 35.5, longitude: 139.25, radiusMeters: 150, category: shopping)
        let open = Todo(title: "Milk", place: place)
        let done = Todo(title: "Bread", place: place)
        done.complete(at: Date(timeIntervalSince1970: 1_000))
        let householdID = UUID()

        let request = InitialUpload.request(
            householdID: householdID,
            categories: [shopping, custom],
            places: [place],
            todos: [open, done]
        )

        #expect(request.id == householdID.uuidString.lowercased())
        #expect(request.categories.map(\.builtin) == [.shopping, .unspecified])
        #expect(request.categories[0].hasName == false)
        #expect(request.categories[1].name == "Gym")
        #expect(request.categories[1].icon == "dumbbell")

        let sentPlace = request.places[0]
        #expect(sentPlace.id == place.id.uuidString.lowercased())
        #expect(sentPlace.lat == 35.5)
        #expect(sentPlace.lng == 139.25)
        #expect(sentPlace.radiusM == 150)
        #expect(sentPlace.categoryID == shopping.id.uuidString.lowercased())

        #expect(request.todos.map(\.todo.title) == ["Milk", "Bread"])
        #expect(request.todos.allSatisfy { $0.todo.placeID == sentPlace.id })
        #expect(request.todos[0].hasCompletedAt == false)
        #expect(request.todos[1].completedAt.date == Date(timeIntervalSince1970: 1_000))
    }

    @Test func uncategorizedPlacesOmitTheCategory() {
        let place = Place(name: "Post office", latitude: 35.0, longitude: 139.0)
        let request = InitialUpload.request(householdID: UUID(), categories: [], places: [place], todos: [])
        #expect(request.places[0].hasCategoryID == false)
    }
}

struct SignInNonceTests {
    @Test func makesDistinctHexNoncesWithinTheServerLimits() {
        let first = SignInNonce.make()
        let second = SignInNonce.make()
        #expect(first != second)
        #expect(first.count == 64)
        #expect(first.allSatisfy { $0.isHexDigit })
    }

    @Test func hashesWithSHA256() {
        #expect(SignInNonce.sha256("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
}
