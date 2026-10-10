import Testing

@testable import LocateDo

struct AnalyticsQueueTests {
    @Test func holdsUntilTheAnswerIsKnown() {
        var queue = AnalyticsQueue()

        let sentEvent = queue.submit(.event(name: "first", parameters: [:]))
        let sentProperty = queue.submit(.userProperty(name: "plan", value: "free"))
        let released = queue.decide(nil)

        #expect(!sentEvent)
        #expect(!sentProperty)
        #expect(released.isEmpty)
        #expect(queue.entries.count == 2)
    }

    @Test func handsBackWhatWasHeldInOrderOnceGranted() {
        var queue = AnalyticsQueue()
        _ = queue.submit(.event(name: "first", parameters: [:]))
        _ = queue.submit(.event(name: "second", parameters: [:]))

        let held = queue.decide(true)
        let sentLater = queue.submit(.event(name: "third", parameters: [:]))

        #expect(names(held) == ["first", "second"])
        #expect(queue.entries.isEmpty)
        #expect(sentLater)
    }

    @Test func dropsWhatWasHeldOnceDeclined() {
        var queue = AnalyticsQueue()
        _ = queue.submit(.event(name: "first", parameters: [:]))

        let released = queue.decide(false)
        let sentLater = queue.submit(.event(name: "second", parameters: [:]))

        #expect(released.isEmpty)
        #expect(!sentLater)
        #expect(queue.entries.isEmpty)
    }

    @Test func holdsAgainWhenTheAnswerIsNoLongerKnown() {
        var queue = AnalyticsQueue()
        _ = queue.decide(true)

        _ = queue.decide(nil)
        let sent = queue.submit(.event(name: "first", parameters: [:]))

        #expect(!sent)
        #expect(queue.entries.count == 1)
    }

    @Test func keepsOnlyTheFirstEntriesUpToTheLimit() {
        var queue = AnalyticsQueue()
        for index in 0..<(AnalyticsQueue.limit + 5) {
            _ = queue.submit(.event(name: "event\(index)", parameters: [:]))
        }

        let held = queue.decide(true)

        #expect(held.count == AnalyticsQueue.limit)
        #expect(names(held).first == "event0")
    }

    private func names(_ entries: [AnalyticsQueue.Entry]) -> [String] {
        entries.compactMap { entry in
            if case let .event(name, _) = entry {
                return name
            }
            return nil
        }
    }
}
