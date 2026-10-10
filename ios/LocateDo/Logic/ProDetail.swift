import Foundation

enum ProDetail: Hashable {
    case term(ProSubscription.Term, isTrial: Bool)
    case renews(Date)
    case ends(Date)
    case autoRenewOff
    case billingIssue
    case household

    static func details(subscription: ProSubscription?, plan: Plan?) -> [ProDetail] {
        guard let subscription else {
            if plan == .pro {
                return [.household]
            }
            return []
        }
        var details: [ProDetail] = []
        if let term = subscription.term {
            details.append(.term(term, isTrial: subscription.isTrial))
        }
        if let expiresAt = subscription.expiresAt {
            if subscription.willRenew {
                details.append(.renews(expiresAt))
            } else {
                details.append(.ends(expiresAt))
            }
        }
        if !subscription.willRenew {
            details.append(.autoRenewOff)
        }
        if subscription.hasBillingIssue {
            details.append(.billingIssue)
        }
        return details
    }
}
