import SwiftUI

private struct SyncRefreshable: ViewModifier {
    private static let minimumSpin: Duration = .seconds(1)

    @Environment(Authenticator.self) private var authenticator
    @Environment(SyncEngine.self) private var sync

    func body(content: Content) -> some View {
        if authenticator.isSignedIn {
            content.refreshable {
                async let synced: Void = sync.sync()
                try? await Task.sleep(for: Self.minimumSpin)
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

    func syncRefreshableEmptyState() -> some View {
        ScrollView {
            containerRelativeFrame([.horizontal, .vertical])
        }
        .syncRefreshable()
    }
}
