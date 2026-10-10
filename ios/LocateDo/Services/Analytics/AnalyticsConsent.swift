import Foundation
import Observation

@Observable
final class AnalyticsConsent {
    enum Source: String {
        case onboarding
        case settings
    }

    @ObservationIgnored var onChanged: (Bool?) -> Void = { _ in }

    private let preferences: AppPreferences
    private(set) var requiresConsent: Bool
    private var isRegionKnown: Bool

    init(preferences: AppPreferences, region: String? = ConsentRegion.currentRegion) {
        self.preferences = preferences
        if let cached = preferences.analyticsConsentRequired {
            requiresConsent = cached
            isRegionKnown = true
        } else {
            requiresConsent = ConsentRegion.requiresConsent(storefront: nil, region: region)
            isRegionKnown = false
        }
    }

    // nil until it is known whether to send: before the first region check, or in scope without an answer.
    var decision: Bool? {
        if let answer = preferences.analyticsConsent {
            return answer
        }
        if !isRegionKnown || requiresConsent {
            return nil
        }
        return true
    }

    var isSending: Bool {
        decision == true
    }

    // Kept after a move out of scope, so an earlier answer can still be changed.
    var showsSetting: Bool {
        requiresConsent || preferences.analyticsConsent != nil
    }

    var needsAnswer: Bool {
        requiresConsent && preferences.analyticsConsent == nil
    }

    func resolveRegion(storefront: String?, region: String?) {
        let previous = decision
        requiresConsent = ConsentRegion.requiresConsent(storefront: storefront, region: region)
        isRegionKnown = true
        preferences.analyticsConsentRequired = requiresConsent
        if decision != previous {
            onChanged(decision)
        }
    }

    func set(_ isOn: Bool, source: Source) {
        let previousDecision = decision
        let previousAnswer = preferences.analyticsConsent
        preferences.analyticsConsent = isOn
        if decision != previousDecision {
            onChanged(decision)
        }
        if isOn, previousAnswer != true {
            Analytics.log(.analyticsConsentGranted, parameters: [.source: source.rawValue])
        }
    }
}
