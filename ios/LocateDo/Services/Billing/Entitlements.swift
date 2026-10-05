import Foundation
import Observation
import OSLog

@Observable
final class Entitlements {
    private(set) var hasEntitlement = false

    private let source: (any EntitlementSource)?
    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "billing")
    @ObservationIgnored private var listener: Task<Void, Never>?

    init(source: (any EntitlementSource)?) {
        self.source = source
    }

    static func isPro(hasEntitlement: Bool, plan: Plan?) -> Bool {
        hasEntitlement || plan == .pro
    }

    func start() {
        guard let source, listener == nil else {
            return
        }
        listener = Task { [weak self] in
            for await hasPro in source.updates() {
                self?.hasEntitlement = hasPro
            }
        }
    }

    func logIn(userID: UUID) async {
        guard let source else {
            return
        }
        do {
            hasEntitlement = try await source.logIn(ProtoInput.id(userID))
        } catch {
            logger.error("RevenueCat logIn failed: \(error, privacy: .public)")
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
        guard let source, let hasPro = try await source.purchase(kind) else {
            return false
        }
        hasEntitlement = hasPro
        return true
    }

    func restore() async throws {
        guard let source else {
            return
        }
        hasEntitlement = try await source.restore()
    }

    func logOut() async {
        guard let source else {
            return
        }
        do {
            hasEntitlement = try await source.logOut()
        } catch {
            logger.error("RevenueCat logOut failed: \(error, privacy: .public)")
        }
    }
}
