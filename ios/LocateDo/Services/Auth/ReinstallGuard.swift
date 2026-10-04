import Foundation
import OSLog

// The Keychain outlives the app, so a reinstall would start signed in with an empty store.
nonisolated enum ReinstallGuard {
    static let launchedKey = "hasLaunchedBefore"

    static func clearStaleSession(defaults: UserDefaults, store: any SessionStoring, hasCompletedOnboarding: Bool) {
        if defaults.bool(forKey: launchedKey) {
            return
        }
        if !hasCompletedOnboarding {
            do {
                try store.clear()
            } catch {
                Logger(subsystem: "com.locatedo.LocateDo", category: "auth")
                    .error("Could not clear the session left by a previous install: \(error, privacy: .public)")
                return
            }
        }
        defaults.set(true, forKey: launchedKey)
    }
}
