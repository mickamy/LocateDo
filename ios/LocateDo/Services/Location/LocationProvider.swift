import CoreLocation
import Observation

@Observable
final class LocationProvider: NSObject, CLLocationManagerDelegate {
    private(set) var location: CLLocation?
    private(set) var authorizationStatus: CLAuthorizationStatus
    private(set) var hasPreciseLocation: Bool

    private let manager = CLLocationManager()
    private var updates: Task<Void, Never>?

    override init() {
        authorizationStatus = .notDetermined
        hasPreciseLocation = false
        super.init()
        manager.delegate = self
        refreshAuthorizationStatus()
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

    // A change made in Settings shows here once the app is back, which reading the status on return can miss.
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            self.refreshAuthorizationStatus()
        }
    }
}
