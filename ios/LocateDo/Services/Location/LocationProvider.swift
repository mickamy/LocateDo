import CoreLocation
import Observation

@Observable
final class LocationProvider {
    private(set) var location: CLLocation?
    private(set) var authorizationStatus: CLAuthorizationStatus

    private let manager = CLLocationManager()
    private var updates: Task<Void, Never>?

    init() {
        authorizationStatus = manager.authorizationStatus
    }

    func start() {
        if updates != nil {
            return
        }
        if manager.authorizationStatus == .notDetermined {
            manager.requestWhenInUseAuthorization()
        }
        updates = Task { [weak self] in
            do {
                for try await update in CLLocationUpdate.liveUpdates() {
                    guard let self else {
                        return
                    }
                    authorizationStatus = manager.authorizationStatus
                    if let location = update.location {
                        self.location = location
                    }
                }
            } catch {
                self?.updates = nil
            }
        }
    }

    var hasPreciseLocation: Bool {
        manager.accuracyAuthorization == .fullAccuracy
    }

    func requestAlwaysAuthorization() {
        if manager.authorizationStatus == .authorizedWhenInUse {
            manager.requestAlwaysAuthorization()
        }
    }

    func refreshAuthorizationStatus() {
        authorizationStatus = manager.authorizationStatus
    }

    func stop() {
        updates?.cancel()
        updates = nil
    }
}
