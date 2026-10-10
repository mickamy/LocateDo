import Foundation
import Observation

@Observable
final class AnalyticsConsent {
    enum Source: String {
        case onboarding
        case settings
    }

    @ObservationIgnored var onChanged: (Bool) -> Void = { _ in }

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

    // Nothing is sent before the first region check, so a storefront in the EEA or the UK is never missed.
    var isSending: Bool {
        if let answer = preferences.analyticsConsent {
            return answer
        }
        return isRegionKnown && !requiresConsent
    }

    var needsAnswer: Bool {
        requiresConsent && preferences.analyticsConsent == nil
    }

    func resolveRegion(storefront: String?, region: String?) {
        let wasSending = isSending
        requiresConsent = ConsentRegion.requiresConsent(storefront: storefront, region: region)
        isRegionKnown = true
        preferences.analyticsConsentRequired = requiresConsent
        if isSending != wasSending {
            onChanged(isSending)
        }
    }

    func set(_ isOn: Bool, source: Source) {
        let wasSending = isSending
        let previous = preferences.analyticsConsent
        preferences.analyticsConsent = isOn
        if isSending != wasSending {
            onChanged(isSending)
        }
        if isOn, previous != true {
            Analytics.log(.analyticsConsentGranted, parameters: [.source: source.rawValue])
        }
    }
}
