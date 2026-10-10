import Observation
import SwiftUI

// One delete at a time can be undone, for a few seconds, from a banner at the bottom of the screen.
@Observable
final class TodoUndo {
    struct Offer: Identifiable {
        let id = UUID()
        let message: String
        let deleted: [DeletedTodo]
    }

    static let duration: Duration = .seconds(4)

    private(set) var offer: Offer?
    @ObservationIgnored private var expiry: Task<Void, Never>?

    func offer(_ deleted: [DeletedTodo]) {
        guard let first = deleted.first else {
            return
        }
        let offer = Offer(message: String(localized: .todoDeleted(first.title)), deleted: deleted)
        withAnimation {
            self.offer = offer
        }
        expiry?.cancel()
        expiry = Task { [weak self] in
            try? await Task.sleep(for: Self.duration)
            guard !Task.isCancelled, self?.offer?.id == offer.id else {
                return
            }
            withAnimation {
                self?.offer = nil
            }
        }
    }

    func take() -> [DeletedTodo] {
        let deleted = offer?.deleted ?? []
        expiry?.cancel()
        withAnimation {
            offer = nil
        }
        return deleted
    }
}
