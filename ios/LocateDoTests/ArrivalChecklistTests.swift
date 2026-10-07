import Foundation
import Testing

@testable import LocateDo

struct ArrivalChecklistTests {
    @Test func itemsAndCategorySurviveTheNotificationPayload() throws {
        let checklist = ArrivalChecklist(
            items: [.init(id: UUID(), title: "Milk"), .init(id: UUID(), title: "Detergent")],
            categoryIcon: "cart",
            categoryColor: "orange"
        )

        let read = try #require(ArrivalChecklist(userInfo: checklist.userInfo))

        #expect(read == checklist)
    }

    @Test func aPlaceWithoutACategoryHasNone() throws {
        let checklist = ArrivalChecklist(items: [.init(id: UUID(), title: "Milk")])

        let read = try #require(ArrivalChecklist(userInfo: checklist.userInfo))

        #expect(read.categoryIcon == nil)
        #expect(read.categoryColor == nil)
    }

    @Test func malformedItemsAreSkipped() throws {
        let id = UUID()
        let read = try #require(ArrivalChecklist(userInfo: [ArrivalChecklist.itemsKey: [
            ["id": "not-a-uuid", "title": "Milk"],
            ["id": UUID().uuidString],
            ["id": id.uuidString, "title": "Bread"]
        ]]))

        #expect(read.items == [.init(id: id, title: "Bread")])
    }

    @Test func notificationsWithoutItemsHaveNoChecklist() {
        #expect(ArrivalChecklist(userInfo: ["placeID": UUID().uuidString]) == nil)
        #expect(ArrivalChecklist(userInfo: [ArrivalChecklist.itemsKey: [[String: String]]()]) == nil)
    }
}
