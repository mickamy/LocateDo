import Observation
import SwiftUI

@Observable
private final class ScrollInteraction {
    var isInteracting = false

    func released() async {
        while isInteracting && !Task.isCancelled {
            try? await Task.sleep(for: .milliseconds(50))
        }
    }
}

private struct SyncRefreshable: ViewModifier {
    private static let spinAfterRelease: Duration = .seconds(1)

    @Environment(Authenticator.self) private var authenticator
    @Environment(SyncEngine.self) private var sync
    @State private var interaction = ScrollInteraction()

    func body(content: Content) -> some View {
        if authenticator.isSignedIn {
            content
                .onScrollPhaseChange { _, phase in
                    interaction.isInteracting = phase == .interacting
                }
                .refreshable {
                    async let synced: Void = sync.sync()
                    // The refresh starts while the finger is still pulling; keep spinning for a moment after release.
                    await interaction.released()
                    try? await Task.sleep(for: Self.spinAfterRelease)
                    await synced
                }
        } else {
            content
        }
    }
}

extension View {
    func syncRefreshable() -> some View {
        modifier(SyncRefreshable())
    }

    // Sized from outside the scroll view: containerRelativeFrame follows the refresh inset and leaves the offset stuck.
    // The bottom padding lifts the content a little above the true center, which otherwise reads as too low.
    func syncRefreshableEmptyState() -> some View {
        GeometryReader { proxy in
            ScrollView {
                padding(.bottom, proxy.size.height * 0.15)
                    .frame(width: proxy.size.width, height: proxy.size.height)
            }
            .syncRefreshable()
        }
    }
}
