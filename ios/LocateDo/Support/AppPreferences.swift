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
        static let promotionsConsent = "promotionsConsent"
        static let receivedArrivalNotification = "receivedArrivalNotification"
        static let shownPromotionsPrompt = "shownPromotionsPrompt"
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

    var promotionsConsent: Bool {
        didSet { defaults.set(promotionsConsent, forKey: Key.promotionsConsent) }
    }

    var hasReceivedArrivalNotification: Bool {
        didSet { defaults.set(hasReceivedArrivalNotification, forKey: Key.receivedArrivalNotification) }
    }

    var hasShownPromotionsPrompt: Bool {
        didSet { defaults.set(hasShownPromotionsPrompt, forKey: Key.shownPromotionsPrompt) }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        hasCompletedOnboarding = defaults.bool(forKey: Key.completedOnboarding)
        hasPromptedAlwaysLocation = defaults.bool(forKey: Key.promptedAlwaysLocation)
        hasPendingSessionEndedNotice = defaults.bool(forKey: Key.pendingSessionEndedNotice)
        hasPendingRemovedNotice = defaults.bool(forKey: Key.pendingRemovedNotice)
        promotionsConsent = defaults.bool(forKey: Key.promotionsConsent)
        hasReceivedArrivalNotification = defaults.bool(forKey: Key.receivedArrivalNotification)
        hasShownPromotionsPrompt = defaults.bool(forKey: Key.shownPromotionsPrompt)
        let storedRadius = defaults.double(forKey: Key.defaultRadiusMeters)
        defaultRadiusMeters = Place.radiusRange.contains(storedRadius) ? storedRadius : Place.defaultRadiusMeters
    }

    func reset() {
        hasCompletedOnboarding = false
        hasPromptedAlwaysLocation = false
        defaultRadiusMeters = Place.defaultRadiusMeters
    }
}
