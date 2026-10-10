import CoreLocation
import Observation

@Observable
final class LocationProvider {
    private(set) var location: CLLocation?
    private(set) var authorizationStatus: CLAuthorizationStatus
    private(set) var hasPreciseLocation: Bool

    private let manager = CLLocationManager()
    private var updates: Task<Void, Never>?

    init() {
        authorizationStatus = manager.authorizationStatus
        hasPreciseLocation = manager.accuracyAuthorization == .fullAccuracy
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
                    refreshAuthorizationStatus()
                    if let location = update.location {
                        self.location = location
                    }
                }
            } catch {
                self?.updates = nil
            }
        }
    }

    func requestAlwaysAuthorization() {
        if manager.authorizationStatus == .authorizedWhenInUse {
            manager.requestAlwaysAuthorization()
        }
    }

    func refreshAuthorizationStatus() {
        authorizationStatus = manager.authorizationStatus
        hasPreciseLocation = manager.accuracyAuthorization == .fullAccuracy
    }

    func stop() {
        updates?.cancel()
        updates = nil
    }
}
