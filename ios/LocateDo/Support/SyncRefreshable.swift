import SwiftUI

private struct SyncRefreshable: ViewModifier {
    @Environment(Authenticator.self) private var authenticator
    @Environment(SyncEngine.self) private var sync

    func body(content: Content) -> some View {
        if authenticator.isSignedIn {
            content.refreshable {
                await sync.sync()
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
