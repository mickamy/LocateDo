import SwiftUI

struct UndoBanner: View {
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
