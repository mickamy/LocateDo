import Foundation
import Observation

@Observable
final class CompletionNotices {
    @ObservationIgnored var onChanged: () async -> Void = {}

    private let preferences: AppPreferences

    init(preferences: AppPreferences) {
        self.preferences = preferences
    }

    var isOn: Bool {
        preferences.completionNotices
    }

    @discardableResult
    func set(_ isOn: Bool) -> Task<Void, Never>? {
        guard isOn != preferences.completionNotices else {
            return nil
        }
        preferences.completionNotices = isOn
        var value = "off"
        if isOn {
            value = "on"
        }
        Analytics.log(.completionNoticesChanged, parameters: [.to: value])
        let onChanged = onChanged
        return Task {
            await onChanged()
        }
    }
}
