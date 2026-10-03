import Foundation
import Observation

@Observable
final class AppPreferences {
    private enum Key {
        static let completedOnboarding = "completedOnboarding"
        static let promptedAlwaysLocation = "promptedAlwaysLocation"
        static let defaultRadiusMeters = "defaultRadiusMeters"
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

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        hasCompletedOnboarding = defaults.bool(forKey: Key.completedOnboarding)
        hasPromptedAlwaysLocation = defaults.bool(forKey: Key.promptedAlwaysLocation)
        let storedRadius = defaults.double(forKey: Key.defaultRadiusMeters)
        defaultRadiusMeters = Place.radiusRange.contains(storedRadius) ? storedRadius : Place.defaultRadiusMeters
    }
}
