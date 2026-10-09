import Foundation
import Observation
import OSLog
import WatchConnectivity

@Observable
final class WatchStore: NSObject, WCSessionDelegate {
    // The notification interface is made by the system, so it reaches the app's store through here.
    static let shared = WatchStore()

    private(set) var snapshot: WatchSnapshot?

    private var received: WatchSnapshot?
    // Kept out of every snapshot until one arrives without them, which means iPhone has written them.
    private var checkedOff: Set<UUID> = []
    // A check-off made while the session is still activating, for example from a notification, waits here.
    @ObservationIgnored private var unsent: [[String: Any]] = []
    @ObservationIgnored private nonisolated let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "watch")

    func start() {
        guard WCSession.isSupported() else {
            return
        }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    func checkOff(_ todoIDs: [UUID], source: WatchCheckOff.Source) {
        checkedOff.formUnion(todoIDs)
        refresh()
        deliver(WatchCheckOff(todoIDs: todoIDs, source: source).message)
    }

    private func deliver(_ message: [String: Any]) {
        let session = WCSession.default
        guard session.activationState == .activated else {
            unsent.append(message)
            return
        }
        guard session.isReachable else {
            session.transferUserInfo(message)
            return
        }
        session.sendMessage(message, replyHandler: nil) { [logger] error in
            logger.notice("Queuing a check-off after sending failed: \(error, privacy: .public)")
            session.transferUserInfo(message)
        }
    }

    private func activated(with snapshot: WatchSnapshot?) {
        if let snapshot {
            receive(snapshot)
        }
        let messages = unsent
        unsent = []
        for message in messages {
            deliver(message)
        }
    }

    private func receive(_ snapshot: WatchSnapshot) {
        let present = Set(snapshot.places.flatMap { $0.todos.map(\.id) })
        checkedOff.formIntersection(present)
        received = snapshot
        refresh()
    }

    private func refresh() {
        snapshot = received?.removing(checkedOff)
    }

    nonisolated func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: (any Error)?
    ) {
        let snapshot = WatchSnapshot(applicationContext: session.receivedApplicationContext)
        Task { @MainActor in
            activated(with: snapshot)
        }
    }

    nonisolated func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        guard let snapshot = WatchSnapshot(applicationContext: applicationContext) else {
            return
        }
        Task { @MainActor in
            receive(snapshot)
        }
    }
}
