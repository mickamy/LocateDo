import Foundation
import Observation

@Observable
final class AppPreferences {
    private enum Key {
        static let completedOnboarding = "completedOnboarding"
        static let promptedAlwaysLocation = "promptedAlwaysLocation"
        static let defaultRadiusMeters = "defaultRadiusMeters"
        static let pendingSessionEndedNotice = "pendingSessionEndedNotice"
        static let pendingRemovedNotice = "pendingRemovedNotice"
    }

    private let defaults: UserDefaults

    var hasCompletedOnboarding: Bool {
        didSet { defaults.set(hasCompletedOnboarding, forKey: Key.completedOnboarding) }
    }

    var hasPromptedAlwaysLocation: Bool {
        didSet { defaults.set(hasPromptedAlwaysLocation, forKey: Key.promptedAlwaysLocation) }
    }

    var defaultRadiusMeters: Double {
        didSet { defaults.set(defaultRadiusMeters, forKey: Key.defaultRadiusMeters) }
    }

    var hasPendingSessionEndedNotice: Bool {
        didSet { defaults.set(hasPendingSessionEndedNotice, forKey: Key.pendingSessionEndedNotice) }
    }

    var hasPendingRemovedNotice: Bool {
        didSet { defaults.set(hasPendingRemovedNotice, forKey: Key.pendingRemovedNotice) }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        hasCompletedOnboarding = defaults.bool(forKey: Key.completedOnboarding)
        hasPromptedAlwaysLocation = defaults.bool(forKey: Key.promptedAlwaysLocation)
        hasPendingSessionEndedNotice = defaults.bool(forKey: Key.pendingSessionEndedNotice)
        hasPendingRemovedNotice = defaults.bool(forKey: Key.pendingRemovedNotice)
        let storedRadius = defaults.double(forKey: Key.defaultRadiusMeters)
        defaultRadiusMeters = Place.radiusRange.contains(storedRadius) ? storedRadius : Place.defaultRadiusMeters
    }

    func reset() {
        hasCompletedOnboarding = false
        hasPromptedAlwaysLocation = false
        defaultRadiusMeters = Place.defaultRadiusMeters
    }
}
