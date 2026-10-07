import Foundation
import Observation
import UserNotifications

@Observable
final class PromotionsConsent {
    enum Source: String {
        case firstArrival = "first_arrival"
        case settings
    }

    enum Answer: String {
        case accepted
        case declined
        case dismissed
    }

    static let quietPeriodAfterArrivalOpened: TimeInterval = 30 * 60

    @ObservationIgnored var onChanged: () async -> Void = {}

    private let preferences: AppPreferences

    init(preferences: AppPreferences) {
        self.preferences = preferences
    }

    var isOn: Bool {
        preferences.promotionsConsent
    }

    @discardableResult
    func set(_ isOn: Bool, source: Source) -> Task<Void, Never>? {
        guard isOn != preferences.promotionsConsent else {
            return nil
        }
        preferences.promotionsConsent = isOn
        var value = "off"
        if isOn {
            value = "on"
        }
        Analytics.log(.promotionsConsentChanged, parameters: [.to: value, .source: source.rawValue])
        Analytics.setUserProperty(DailyState.flag(isOn), for: .promotionsConsent)
        let onChanged = onChanged
        return Task {
            await onChanged()
        }
    }

    func arrivalNotified() {
        preferences.hasReceivedArrivalNotification = true
    }

    func shouldPrompt(notificationAuth: UNAuthorizationStatus, lastArrivalOpenedAt: Date?, now: Date) -> Bool {
        guard preferences.hasReceivedArrivalNotification,
              !preferences.hasShownPromotionsPrompt,
              !preferences.promotionsConsent,
              DailyState.NotificationAuth(notificationAuth) == .authorized else {
            return false
        }
        if let lastArrivalOpenedAt, now.timeIntervalSince(lastArrivalOpenedAt) < Self.quietPeriodAfterArrivalOpened {
            return false
        }
        return true
    }

    func promptShown(daysSinceInstall: Int, notificationAuth: UNAuthorizationStatus) {
        preferences.hasShownPromotionsPrompt = true
        Analytics.log(.promotionsPromptShown, parameters: [
            .daysSinceInstall: daysSinceInstall,
            .notificationAuth: DailyState.NotificationAuth(notificationAuth).rawValue
        ])
    }

    @discardableResult
    func answerPrompt(_ answer: Answer, after duration: TimeInterval) -> Task<Void, Never>? {
        Analytics.log(.promotionsPromptAnswered, parameters: [
            .result: answer.rawValue,
            .durationS: max(Int(duration), 0)
        ])
        guard answer == .accepted else {
            return nil
        }
        return set(true, source: .firstArrival)
    }
}
