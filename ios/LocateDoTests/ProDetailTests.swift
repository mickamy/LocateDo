import Foundation
import Testing

@testable import LocateDo

struct ProDetailTests {
    private static let date = Date(timeIntervalSince1970: 1_800_000_000)

    @Test func readsTheTermFromTheProductID() {
        #expect(ProSubscription.term(forProductID: "com.locatedo.LocateDo.pro.annual") == .annual)
        #expect(ProSubscription.term(forProductID: "com.locatedo.LocateDo.pro.monthly") == .monthly)
        #expect(ProSubscription.term(forProductID: "test_product") == nil)
    }

    @Test func aRenewingSubscriptionShowsItsTermAndNextRenewal() {
        let details = ProDetail.details(subscription: subscription(willRenew: true), plan: .pro)

        #expect(details == [.term(.annual, isTrial: false), .renews(Self.date)])
    }

    @Test func aTrialSaysSo() {
        let details = ProDetail.details(subscription: subscription(willRenew: true, isTrial: true), plan: .free)

        #expect(details == [.term(.annual, isTrial: true), .renews(Self.date)])
    }

    @Test func turningOffAutoRenewShowsWhenItEnds() {
        let details = ProDetail.details(subscription: subscription(willRenew: false), plan: .pro)

        #expect(details == [.term(.annual, isTrial: false), .ends(Self.date), .autoRenewOff])
    }

    @Test func aBillingIssueIsFlagged() {
        let details = ProDetail.details(subscription: subscription(willRenew: true, hasBillingIssue: true), plan: .pro)

        #expect(details.last == .billingIssue)
    }

    @Test func anUnknownProductLeavesTheTermOut() {
        let unknown = ProSubscription(
            term: nil,
            expiresAt: Self.date,
            willRenew: true,
            isTrial: false,
            hasBillingIssue: false
        )

        #expect(ProDetail.details(subscription: unknown, plan: .pro) == [.renews(Self.date)])
    }

    @Test func withoutASubscriptionOnlyTheHouseholdPlanCounts() {
        #expect(ProDetail.details(subscription: nil, plan: .pro) == [.household])
        #expect(ProDetail.details(subscription: nil, plan: .free).isEmpty)
        #expect(ProDetail.details(subscription: nil, plan: nil).isEmpty)
    }

    private func subscription(
        willRenew: Bool,
        isTrial: Bool = false,
        hasBillingIssue: Bool = false
    ) -> ProSubscription {
        ProSubscription(
            term: .annual,
            expiresAt: Self.date,
            willRenew: willRenew,
            isTrial: isTrial,
            hasBillingIssue: hasBillingIssue
        )
    }
}
