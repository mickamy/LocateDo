import Foundation
import Testing

@testable import LocateDo

struct WatchCheckOffTests {
    @Test func survivesTheMessage() throws {
        let checkOff = WatchCheckOff(todoIDs: [UUID(), UUID()], source: .notification)

        let read = try #require(WatchCheckOff(message: checkOff.message))

        #expect(read == checkOff)
    }

    @Test func malformedIDsAreSkipped() throws {
        let id = UUID()
        let read = try #require(WatchCheckOff(message: [
            WatchCheckOff.todoIDsKey: ["not-a-uuid", id.uuidString],
            WatchCheckOff.sourceKey: "app"
        ]))

        #expect(read.todoIDs == [id])
        #expect(read.source == .app)
    }

    @Test func messagesWithoutIDsOrSourceAreNotCheckOffs() {
        #expect(WatchCheckOff(message: [WatchCheckOff.sourceKey: "app"]) == nil)
        #expect(WatchCheckOff(message: [WatchCheckOff.todoIDsKey: [UUID().uuidString]]) == nil)
        #expect(WatchCheckOff(message: [
            WatchCheckOff.todoIDsKey: [UUID().uuidString],
            WatchCheckOff.sourceKey: "phone"
        ]) == nil)
        #expect(WatchCheckOff(message: [
            WatchCheckOff.todoIDsKey: ["not-a-uuid"],
            WatchCheckOff.sourceKey: "app"
        ]) == nil)
    }
}
