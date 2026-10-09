import Observation
import SwiftUI

// One delete at a time can be undone, for a few seconds, from a banner over the tabs.
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

struct TodoUndoBanner: View {
    @Environment(TodoUndo.self) private var undo
    @Environment(LocalWrites.self) private var writes

    var body: some View {
        if let offer = undo.offer {
            HStack(spacing: 12) {
                Text(offer.message)
                    .lineLimit(2)
                Spacer(minLength: 0)
                Button(.commonUndo) {
                    writes.restore(undo.take())
                }
                .fontWeight(.semibold)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .glassEffect(.regular, in: .capsule)
            .padding(.horizontal, 16)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .id(offer.id)
        }
    }
}
