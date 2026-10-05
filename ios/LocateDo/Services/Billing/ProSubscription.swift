import Foundation

nonisolated struct ProSubscription: Equatable, Sendable {
    enum Term: Hashable, Sendable {
        case annual
        case monthly
    }

    let term: Term?
    let expiresAt: Date?
    let willRenew: Bool
    let isTrial: Bool
    let hasBillingIssue: Bool

    static func term(forProductID productID: String) -> Term? {
        if productID.hasSuffix(".annual") {
            return .annual
        }
        if productID.hasSuffix(".monthly") {
            return .monthly
        }
        return nil
    }
}

nonisolated enum PurchaseOutcome: Equatable, Sendable {
    case cancelled
    case completed(ProSubscription?)
}
