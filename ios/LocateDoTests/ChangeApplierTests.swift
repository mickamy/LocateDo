import Foundation
import SwiftData
import SwiftProtobuf
import Testing

@testable import LocateDo

struct ChangeApplierTests {
    private static let created = Date(timeIntervalSince1970: 1_700_000_000)
    private static let updated = Date(timeIntervalSince1970: 1_800_000_000)

    @Test func insertsRowsEvenWhenAPlaceArrivesBeforeItsCategory() throws {
        let context = try makeContext()
        let categoryID = UUID.v7(now: Self.created)
        let placeID = UUID.v7(now: Self.created)
        let todoID = UUID.v7(now: Self.created)

        let outcome = try ChangeApplier.apply(
            [
                .place(Self.place(placeID, categoryID: categoryID, version: 2)),
                .todo(Self.todo(todoID, placeID: placeID, version: 4)),
                .category(Self.category(categoryID, name: "Gym", version: 3))
            ],
            reset: false,
            to: context
        )

        #expect(outcome.placesChanged)
        let place = try #require(try fetch(Place.self, in: context).first { $0.id == placeID })
        #expect(place.name == "Store")
        #expect(place.radiusMeters == 150)
        #expect(place.category?.name == "Gym")
        #expect(place.createdAt == Self.created)
        #expect(place.updatedAt == Self.updated)
        let todo = try #require(place.todos.first)
        #expect(todo.id == todoID)
        #expect(todo.createdAt == Self.created)
        #expect(todo.completedAt == nil)
    }

    @Test func updatesExistingRowsAndKeepsLocalOnlyFields() throws {
        let context = try makeContext()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        place.lastNotifiedAt = Self.created
        let todo = Todo(title: "Milk", place: place)
        context.insert(place)
        context.insert(todo)
        try context.save()

        var renamed = Self.place(place.id, version: 5)
        renamed.name = "Supermarket"
        var completed = Self.todo(todo.id, placeID: place.id, version: 6)
        completed.title = "Oat milk"
        completed.completedAt = Google_Protobuf_Timestamp(date: Self.updated)
        _ = try ChangeApplier.apply([.place(renamed), .todo(completed)], reset: false, to: context)

        #expect(place.name == "Supermarket")
        #expect(place.lastNotifiedAt == Self.created)
        #expect(todo.title == "Oat milk")
        #expect(todo.completedAt == Self.updated)
        #expect(try fetch(Place.self, in: context).count == 1)
    }

    @Test func unknownCategoryLeavesThePlaceUncategorizedAndOrphanTodosAreSkipped() throws {
        let context = try makeContext()
        let placeID = UUID.v7()

        _ = try ChangeApplier.apply(
            [
                .place(Self.place(placeID, categoryID: UUID.v7(), version: 1)),
                .todo(Self.todo(UUID.v7(), placeID: UUID.v7(), version: 2))
            ],
            reset: false,
            to: context
        )

        #expect(try fetch(Place.self, in: context).first?.category == nil)
        #expect(try fetch(Todo.self, in: context).isEmpty)
    }

    @Test func theHighestVersionOfARowWins() throws {
        let context = try makeContext()
        let deletedID = UUID.v7()
        let recreatedID = UUID.v7()

        _ = try ChangeApplier.apply(
            [
                .place(Self.place(deletedID, version: 1)),
                .deletion(Self.deletion(.place, deletedID, version: 2)),
                .deletion(Self.deletion(.place, recreatedID, version: 3)),
                .place(Self.place(recreatedID, version: 4))
            ],
            reset: false,
            to: context
        )

        #expect(try fetch(Place.self, in: context).map(\.id) == [recreatedID])
    }

    @Test func deletingAPlaceRemovesItsTodosAndMissingRowsAreIgnored() throws {
        let context = try makeContext()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        context.insert(place)
        context.insert(Todo(title: "Milk", place: place))
        try context.save()

        let outcome = try ChangeApplier.apply(
            [
                .deletion(Self.deletion(.place, place.id, version: 7)),
                .deletion(Self.deletion(.todo, UUID.v7(), version: 8))
            ],
            reset: false,
            to: context
        )
        try context.save()

        #expect(outcome.placesChanged)
        #expect(try fetch(Place.self, in: context).isEmpty)
        #expect(try fetch(Todo.self, in: context).isEmpty)
    }

    @Test func todoChangesAloneDoNotTouchPlaces() throws {
        let context = try makeContext()
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0)
        context.insert(place)
        try context.save()

        let outcome = try ChangeApplier.apply(
            [.todo(Self.todo(UUID.v7(), placeID: place.id, version: 1))],
            reset: false,
            to: context
        )

        #expect(!outcome.placesChanged)
    }

    @Test func resetReplacesSyncedDataButKeepsTheQueue() throws {
        let context = try makeContext()
        let local = Place(name: "Local", latitude: 35.0, longitude: 139.0)
        context.insert(local)
        context.insert(Todo(title: "Milk", place: local))
        try PendingWrite.enqueue(.put(local), in: context)
        try context.save()
        let serverCategory = UUID.v7()

        let outcome = try ChangeApplier.apply(
            [
                .category(Self.category(serverCategory, builtin: .shopping, version: 1)),
                .place(Self.place(UUID.v7(), categoryID: serverCategory, version: 2))
            ],
            reset: true,
            to: context
        )
        try context.save()

        #expect(outcome.placesChanged)
        let categories = try fetch(PlaceCategory.self, in: context)
        #expect(categories.map(\.id) == [serverCategory])
        #expect(categories.first?.builtin == .shopping)
        #expect(categories.first?.name == nil)
        #expect(try fetch(Place.self, in: context).map(\.name) == ["Store"])
        #expect(try fetch(Todo.self, in: context).isEmpty)
        #expect(try fetch(PendingWrite.self, in: context).count == 1)
    }

    @Test func membershipsAreUpsertedAndDeletedByUserID() throws {
        let context = try makeContext()
        let owner = UUID.v7()
        let member = UUID.v7()

        _ = try ChangeApplier.apply(
            [
                .membership(Self.membership(owner, role: .owner, name: "Taro", version: 1)),
                .membership(Self.membership(member, role: .member, name: "Hanako", version: 2))
            ],
            reset: false,
            to: context
        )
        _ = try ChangeApplier.apply(
            [
                .membership(Self.membership(owner, role: .owner, name: "Taro Yamada", version: 3)),
                .deletion(Self.deletion(.membership, member, version: 4))
            ],
            reset: false,
            to: context
        )
        try context.save()

        let memberships = try fetch(Membership.self, in: context)
        #expect(memberships.map(\.userID) == [owner])
        #expect(memberships.first?.role == .owner)
        #expect(memberships.first?.displayName == "Taro Yamada")
    }

    private func makeContext() throws -> ModelContext {
        ModelContext(try AppModelContainer.make(inMemory: true))
    }

    private func fetch<Model: PersistentModel>(_ type: Model.Type, in context: ModelContext) throws -> [Model] {
        try context.fetch(FetchDescriptor<Model>())
    }

    private static func category(
        _ id: UUID,
        builtin: Locatedo_Category_V1_BuiltinCategory = .unspecified,
        name: String? = nil,
        version: Int64
    ) -> Locatedo_Category_V1_Category {
        var category = Locatedo_Category_V1_Category()
        category.id = ProtoInput.id(id)
        category.builtin = builtin
        if let name {
            category.name = name
        }
        category.icon = "cart"
        category.color = "green"
        category.updatedAt = Google_Protobuf_Timestamp(date: updated)
        category.version = version
        return category
    }

    private static func place(_ id: UUID, categoryID: UUID? = nil, version: Int64) -> Locatedo_Place_V1_Place {
        var place = Locatedo_Place_V1_Place()
        place.id = ProtoInput.id(id)
        place.name = "Store"
        place.lat = 35.5
        place.lng = 139.25
        place.radiusM = 150
        if let categoryID {
            place.categoryID = ProtoInput.id(categoryID)
        }
        place.updatedAt = Google_Protobuf_Timestamp(date: updated)
        place.version = version
        return place
    }

    private static func todo(_ id: UUID, placeID: UUID, version: Int64) -> Locatedo_Todo_V1_Todo {
        var todo = Locatedo_Todo_V1_Todo()
        todo.id = ProtoInput.id(id)
        todo.placeID = ProtoInput.id(placeID)
        todo.title = "Milk"
        todo.updatedAt = Google_Protobuf_Timestamp(date: updated)
        todo.version = version
        return todo
    }

    private static func membership(
        _ userID: UUID,
        role: Locatedo_Household_V1_Role,
        name: String,
        version: Int64
    ) -> Locatedo_Household_V1_Membership {
        var membership = Locatedo_Household_V1_Membership()
        membership.userID = ProtoInput.id(userID)
        membership.role = role
        membership.displayName = name
        membership.joinedAt = Google_Protobuf_Timestamp(date: created)
        membership.updatedAt = Google_Protobuf_Timestamp(date: updated)
        membership.version = version
        return membership
    }

    private static func deletion(
        _ kind: Locatedo_Sync_V1_EntityKind,
        _ id: UUID,
        version: Int64
    ) -> Locatedo_Sync_V1_Deletion {
        var deletion = Locatedo_Sync_V1_Deletion()
        deletion.kind = kind
        deletion.id = ProtoInput.id(id)
        deletion.version = version
        return deletion
    }
}

private extension Locatedo_Sync_V1_Change {
    static func membership(_ value: Locatedo_Household_V1_Membership) -> Self {
        Self.with { $0.membership = value }
    }

    static func category(_ value: Locatedo_Category_V1_Category) -> Self {
        Self.with { $0.category = value }
    }

    static func place(_ value: Locatedo_Place_V1_Place) -> Self {
        Self.with { $0.place = value }
    }

    static func todo(_ value: Locatedo_Todo_V1_Todo) -> Self {
        Self.with { $0.todo = value }
    }

    static func deletion(_ value: Locatedo_Sync_V1_Deletion) -> Self {
        Self.with { $0.deletion = value }
    }
}
