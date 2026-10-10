import SwiftUI

extension View {
    // Notices kept for the next time the app is open: an announcement, a session that ended, a removal from sharing.
    func launchNotices() -> some View {
        modifier(LaunchNotices())
    }
}

private struct LaunchNotices: ViewModifier {
    @Environment(AppStatusStore.self) private var appStatus
    @Environment(AppPreferences.self) private var preferences

    func body(content: Content) -> some View {
        @Bindable var preferences = preferences
        content
            .alert(
                Text(.announcementTitle),
                isPresented: announcement,
                presenting: appStatus.pendingNotice
            ) { notice in
                Button(.commonOk) {
                    appStatus.markNoticeShown(notice)
                }
            } message: { notice in
                Text(notice.message.text(for: Bundle.main.preferredLocalizations.first) ?? "")
            }
            .alert(Text(.sessionEndedTitle), isPresented: $preferences.hasPendingSessionEndedNotice) {
                Button(.commonOk) {}
            } message: {
                Text(.sessionEndedIosMessage)
            }
            .alert(Text(.removedTitle), isPresented: $preferences.hasPendingRemovedNotice) {
                Button(.commonOk) {}
            } message: {
                Text(.removedIosMessage)
            }
    }

    private var announcement: Binding<Bool> {
        Binding {
            appStatus.pendingNotice != nil
        } set: { _ in }
    }
}
