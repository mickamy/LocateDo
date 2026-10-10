import GoogleSignIn
import SwiftUI

struct RootView: View {
    @Environment(Navigator.self) private var navigator
    @Environment(AppPreferences.self) private var preferences
    @Environment(AccountManager.self) private var account
    @Environment(AppStatusStore.self) private var appStatus
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if account.isResettingLocalData {
                ProgressView()
                    .task {
                        account.screenDidClear()
                    }
            } else if appStatus.requiresUpdate {
                UpdateRequiredScreen()
                    .task(id: scenePhase) {
                        await refreshAppStatus()
                    }
            } else if preferences.hasCompletedOnboarding {
                MainScreen()
            } else {
                OnboardingScreen()
                    .task(id: scenePhase) {
                        await refreshAppStatus()
                    }
            }
        }
        .onOpenURL { url in
            if GIDSignIn.sharedInstance.handle(url) {
                return
            }
            if let token = InviteLink.token(from: url.absoluteString) {
                navigator.request(.invite(PendingInvite(token: token)))
            }
        }
    }

    private func refreshAppStatus() async {
        guard scenePhase == .active else {
            return
        }
        await appStatus.refresh()
    }
}
