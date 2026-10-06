import Foundation

struct PaywallAnalytics {
    enum RestoreResult: String {
        case restored
        case nothing
        case failed
    }

    let trigger: PaywallTrigger

    func shown() {
        Analytics.log(.paywallShown, parameters: [.trigger: trigger.rawValue])
    }

    func dismissed(openedAt: Date, now: Date = .now) {
        Analytics.log(.paywallDismissed, parameters: [
            .trigger: trigger.rawValue,
            .durationS: max(Int(now.timeIntervalSince(openedAt)), 0)
        ])
    }

    func purchaseStarted(plan: String) {
        Analytics.log(.purchaseStarted, parameters: [.trigger: trigger.rawValue, .plan: plan])
    }

    func purchased(plan: String) {
        Analytics.log(.paywallPurchased, parameters: [
            .trigger: trigger.rawValue,
            .plan: plan,
            .daysSinceInstall: InstallDate.daysSinceInstall(defaults: .standard, now: .now)
        ])
    }

    func purchaseCancelled(plan: String) {
        Analytics.log(.purchaseCancelled, parameters: [.trigger: trigger.rawValue, .plan: plan])
    }

    func purchaseFailed(plan: String, error: any Error) {
        Analytics.log(.purchaseFailed, parameters: [
            .trigger: trigger.rawValue,
            .plan: plan,
            .reason: Self.reason(for: error)
        ])
    }

    func restoreCompleted(_ result: RestoreResult, error: (any Error)? = nil) {
        var parameters: AnalyticsParameters = [.result: result.rawValue]
        if let error {
            parameters[.reason] = Self.reason(for: error)
        }
        Analytics.log(.restoreCompleted, parameters: parameters)
    }

    static func reason(for error: any Error) -> String {
        let error = error as NSError
        return "\(error.domain):\(error.code)"
    }
}
