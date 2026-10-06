import Foundation
import Observation
import OSLog

@Observable
final class Entitlements {
    private(set) var subscription: ProSubscription?

    private let source: (any EntitlementSource)?
    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "billing")
    @ObservationIgnored private var listener: Task<Void, Never>?

    init(source: (any EntitlementSource)?) {
        self.source = source
    }

    var hasEntitlement: Bool {
        subscription != nil
    }

    static func isPro(hasEntitlement: Bool, plan: Plan?) -> Bool {
        hasEntitlement || plan == .pro
    }

    func start() {
        guard let source, listener == nil else {
            return
        }
        listener = Task { [weak self] in
            for await subscription in source.updates() {
                self?.subscription = subscription
            }
        }
    }

    @discardableResult
    func logIn(userID: UUID) async -> Bool {
        guard let source else {
            return false
        }
        do {
            subscription = try await source.logIn(ProtoInput.id(userID))
            return true
        } catch {
            logger.error("RevenueCat logIn failed: \(error, privacy: .public)")
            return false
        }
    }

    func plans() async throws -> [PaywallPlan] {
        guard let source else {
            return []
        }
        return try await source.plans()
    }

    // Returns false when the buyer cancels.
    func purchase(_ kind: PaywallPlan.Kind) async throws -> Bool {
        guard let source else {
            return false
        }
        switch try await source.purchase(kind) {
        case .cancelled:
            return false
        case .completed(let subscription):
            self.subscription = subscription
            return true
        }
    }

    func restore() async throws {
        guard let source else {
            return
        }
        subscription = try await source.restore()
    }

    func logOut() async {
        guard let source else {
            return
        }
        do {
            subscription = try await source.logOut()
        } catch {
            logger.error("RevenueCat logOut failed: \(error, privacy: .public)")
        }
    }
}
