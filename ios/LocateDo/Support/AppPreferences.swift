import Foundation
import Observation

@Observable
final class AppPreferences {
    private enum Key {
        static let completedOnboarding = "completedOnboarding"
        static let reminderSetupShownAt = "reminderSetupShownAt"
        static let reminderSetupShownCount = "reminderSetupShownCount"
        static let reminderSetupNever = "reminderSetupNever"
        static let requestedAlwaysLocation = "requestedAlwaysLocation"
        static let defaultRadiusMeters = "defaultRadiusMeters"
        static let pendingSessionEndedNotice = "pendingSessionEndedNotice"
        static let pendingRemovedNotice = "pendingRemovedNotice"
        static let promotionsConsent = "promotionsConsent"
        static let receivedArrivalNotification = "receivedArrivalNotification"
        static let shownPromotionsPrompt = "shownPromotionsPrompt"
        static let completionNotices = "completionNotices"
        static let analyticsConsent = "analyticsConsent"
        static let analyticsConsentRequired = "analyticsConsentRequired"
    }

    private let defaults: UserDefaults

    var hasCompletedOnboarding: Bool {
        didSet { defaults.set(hasCompletedOnboarding, forKey: Key.completedOnboarding) }
    }

    var reminderSetupShownAt: Date? {
        didSet { defaults.set(reminderSetupShownAt, forKey: Key.reminderSetupShownAt) }
    }

    var reminderSetupShownCount: Int {
        didSet { defaults.set(reminderSetupShownCount, forKey: Key.reminderSetupShownCount) }
    }

    var reminderSetupNever: Bool {
        didSet { defaults.set(reminderSetupNever, forKey: Key.reminderSetupNever) }
    }

    // iOS shows its "Always" prompt only once; after that the way there is Settings.
    var hasRequestedAlwaysLocation: Bool {
        didSet { defaults.set(hasRequestedAlwaysLocation, forKey: Key.requestedAlwaysLocation) }
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

    var completionNotices: Bool {
        didSet { defaults.set(completionNotices, forKey: Key.completionNotices) }
    }

    // nil until the person answers; set from Settings in any region.
    var analyticsConsent: Bool? {
        didSet { defaults.set(analyticsConsent, forKey: Key.analyticsConsent) }
    }

    // The last region check, so later launches know before StoreKit answers.
    var analyticsConsentRequired: Bool? {
        didSet { defaults.set(analyticsConsentRequired, forKey: Key.analyticsConsentRequired) }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        hasCompletedOnboarding = defaults.bool(forKey: Key.completedOnboarding)
        reminderSetupShownAt = defaults.object(forKey: Key.reminderSetupShownAt) as? Date
        reminderSetupShownCount = defaults.integer(forKey: Key.reminderSetupShownCount)
        reminderSetupNever = defaults.bool(forKey: Key.reminderSetupNever)
        hasRequestedAlwaysLocation = defaults.bool(forKey: Key.requestedAlwaysLocation)
        hasPendingSessionEndedNotice = defaults.bool(forKey: Key.pendingSessionEndedNotice)
        hasPendingRemovedNotice = defaults.bool(forKey: Key.pendingRemovedNotice)
        promotionsConsent = defaults.bool(forKey: Key.promotionsConsent)
        hasReceivedArrivalNotification = defaults.bool(forKey: Key.receivedArrivalNotification)
        hasShownPromotionsPrompt = defaults.bool(forKey: Key.shownPromotionsPrompt)
        completionNotices = defaults.object(forKey: Key.completionNotices) as? Bool ?? true
        analyticsConsent = defaults.object(forKey: Key.analyticsConsent) as? Bool
        analyticsConsentRequired = defaults.object(forKey: Key.analyticsConsentRequired) as? Bool
        let storedRadius = defaults.double(forKey: Key.defaultRadiusMeters)
        defaultRadiusMeters = Place.radiusRange.contains(storedRadius) ? storedRadius : Place.defaultRadiusMeters
    }

    func reset() {
        hasCompletedOnboarding = false
        reminderSetupShownAt = nil
        reminderSetupShownCount = 0
        reminderSetupNever = false
        defaultRadiusMeters = Place.defaultRadiusMeters
    }
}
