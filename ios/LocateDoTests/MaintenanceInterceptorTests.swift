import Connect
import Foundation
import Testing

@testable import LocateDo

struct MaintenanceInterceptorTests {
    @Test func failsEveryCallAsUnavailableDuringMaintenance() async {
        let gate = MaintenanceGate()
        gate.update(AppStatusDocument.Maintenance(
            startsAt: Date(timeIntervalSinceNow: -60),
            endsAt: Date(timeIntervalSinceNow: 3_600),
            message: nil
        ))

        let result = await intercept(with: gate)

        guard case .failure(let error) = result else {
            Issue.record("The request went through during maintenance")
            return
        }
        #expect(error.code == .unavailable)
    }

    @Test func letsCallsThroughOutsideMaintenance() async {
        let gate = MaintenanceGate()
        gate.update(AppStatusDocument.Maintenance(
            startsAt: Date(timeIntervalSinceNow: 60),
            endsAt: Date(timeIntervalSinceNow: 3_600),
            message: nil
        ))

        let result = await intercept(with: gate)

        guard case .success = result else {
            Issue.record("The request was blocked outside maintenance")
            return
        }
    }

    private func intercept(
        with gate: MaintenanceGate
    ) async -> Result<HTTPRequest<Locatedo_Sync_V1_PullRequest>, ConnectError> {
        let interceptor = MaintenanceInterceptor(gate: gate)
        let request = HTTPRequest(
            url: URL(string: "https://api.locatedo.com/locatedo.sync.v1.SyncService/Pull")!,
            headers: [:],
            message: Locatedo_Sync_V1_PullRequest(),
            method: .post,
            trailers: nil,
            idempotencyLevel: .unknown
        )
        return await withCheckedContinuation { continuation in
            interceptor.handleUnaryRequest(request) { result in
                continuation.resume(returning: result)
            }
        }
    }
}
