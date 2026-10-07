import Foundation
import Testing

@testable import LocateDo

struct ArrivalChecklistTests {
    @Test func itemsSurviveTheNotificationPayload() throws {
        let checklist = ArrivalChecklist(items: [
            .init(id: UUID(), title: "Milk"),
            .init(id: UUID(), title: "Detergent")
        ])

        let read = try #require(ArrivalChecklist(userInfo: [ArrivalChecklist.itemsKey: checklist.encodedItems]))

        #expect(read == checklist)
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

    @Test func theActionCarriesTheCheckedIDs() {
        let ids = [UUID(), UUID()]

        let identifier = ArrivalChecklist.actionIdentifier(checking: ids)

        #expect(ArrivalChecklist.checkedIDs(inAction: identifier) == ids)
    }

    @Test func otherActionsCheckNothing() {
        #expect(ArrivalChecklist.checkedIDs(inAction: "com.apple.UNNotificationDefaultActionIdentifier") == nil)
        #expect(ArrivalChecklist.checkedIDs(inAction: "complete:") == nil)
        #expect(ArrivalChecklist.checkedIDs(inAction: "complete:nope") == nil)
    }
}
