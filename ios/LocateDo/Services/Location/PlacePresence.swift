import Foundation

nonisolated enum PlacePresence {
    static let minimumStay: TimeInterval = 5 * 60

    enum Report {
        case inside
        case outside
    }

    enum Event: Equatable {
        case arrival
        case departure(stay: TimeInterval)
        case shortStay(stay: TimeInterval)
    }

    struct Outcome: Equatable {
        var enteredAt: Date?
        var event: Event?
    }

    static func next(_ report: Report, enteredAt: Date?, isFirstReport: Bool, now: Date) -> Outcome {
        switch report {
        case .inside:
            return Outcome(enteredAt: enteredAt ?? now, event: .arrival)
        case .outside:
            var outcome = Outcome()
            if isFirstReport {
                return outcome
            }
            guard let enteredAt else {
                return outcome
            }
            let stay = now.timeIntervalSince(enteredAt)
            if stay >= minimumStay {
                outcome.event = .departure(stay: stay)
            } else {
                outcome.event = .shortStay(stay: stay)
            }
            return outcome
        }
    }
}
