import Foundation
import Network

final class NetworkMonitor {
    private let monitor = NWPathMonitor()
    private let onReconnect: () -> Void
    private var wasSatisfied: Bool?

    init(onReconnect: @escaping () -> Void) {
        self.onReconnect = onReconnect
    }

    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            let satisfied = path.status == .satisfied
            Task { @MainActor in
                self?.pathChanged(satisfied: satisfied)
            }
        }
        monitor.start(queue: DispatchQueue(label: "com.locatedo.LocateDo.network"))
    }

    func pathChanged(satisfied: Bool) {
        if satisfied && wasSatisfied == false {
            onReconnect()
        }
        wasSatisfied = satisfied
    }
}
