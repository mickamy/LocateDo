import Foundation
import SwiftData
import SwiftUI
import Testing

@testable import LocateDo

struct PlaceCategoryTests {
    @Test func newStoresStartWithTheBuiltinsInOrder() throws {
        let context = ModelContext(try AppModelContainer.make(inMemory: true))
        let categories = try context.fetch(FetchDescriptor<PlaceCategory>(sortBy: [SortDescriptor(\.sortOrder)]))

        #expect(categories.map(\.builtin) == BuiltinCategory.allCases)
        #expect(categories.map(\.icon) == ["cart", "briefcase", "house", "mappin"])
        #expect(categories.allSatisfy { $0.name == nil })
    }

    @Test func seedingDoesNothingWhenCategoriesExist() throws {
        let context = ModelContext(try AppModelContainer.make(inMemory: true))
        try PlaceCategory.insertBuiltinsIfEmpty(into: context)

        #expect(try context.fetchCount(FetchDescriptor<PlaceCategory>()) == BuiltinCategory.allCases.count)
    }

    @Test func deletingACategoryLeavesItsPlacesUncategorized() throws {
        let context = ModelContext(try AppModelContainer.make(inMemory: true))
        let shopping = try #require(
            try context.fetch(FetchDescriptor<PlaceCategory>()).first { $0.builtin == .shopping }
        )
        let place = Place(name: "Store", latitude: 35.0, longitude: 139.0, category: shopping)
        context.insert(place)
        try context.save()

        context.delete(shopping)
        try context.save()

        #expect(try context.fetch(FetchDescriptor<Place>()).count == 1)
        #expect(place.category == nil)
    }

    @Test func customNameOverridesTheBuiltinTitle() {
        let category = PlaceCategory(builtin: .shopping, sortOrder: 0)
        #expect(category.displayName == String(localized: BuiltinCategory.shopping.title))

        category.name = "Costco"
        #expect(category.displayName == "Costco")
    }

    @Test func styleFallsBackForUnknownKeysAndNoCategory() {
        let unknown = CategoryStyle(PlaceCategory(name: "Gym", icon: "dumbbell", color: "teal", sortOrder: 4))
        #expect(unknown.name == "Gym")
        #expect(unknown.systemImage == "mappin")
        #expect(unknown.tint == .gray)

        let none = CategoryStyle(nil)
        #expect(none.name == String(localized: .categoryNone))
        #expect(none.systemImage == "mappin")
    }
}
