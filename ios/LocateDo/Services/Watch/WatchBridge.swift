import Foundation
import Observation
import OSLog
import SwiftData
import UIKit
import WatchConnectivity

@Observable
final class WatchBridge: NSObject, WCSessionDelegate {
    @ObservationIgnored var snapshot: () -> WatchSnapshot = { WatchSnapshot(places: []) }
    @ObservationIgnored var onCheckOff: (WatchCheckOff) async -> Void = { _ in }
    // For the debug section: what happened last, since the Watch side cannot be inspected from here.
    private(set) var lastEvent: String?

    @ObservationIgnored private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "watch")
    @ObservationIgnored private var lastSent: WatchSnapshot?
    @ObservationIgnored private var observers: [any NSObjectProtocol] = []

    // Read once the session has activated; before that it says false.
    var hasWatchApp: Bool {
        guard WCSession.isSupported() else {
            return false
        }
        let session = WCSession.default
        return session.activationState == .activated && session.isPaired && session.isWatchAppInstalled
    }

    var state: String {
        guard WCSession.isSupported() else {
            return "unsupported"
        }
        let session = WCSession.default
        let activation = switch session.activationState {
        case .activated: "activated"
        case .inactive: "inactive"
        case .notActivated: "not activated"
        @unknown default: "unknown"
        }
        return "\(activation), paired \(session.isPaired), installed \(session.isWatchAppInstalled), "
            + "reachable \(session.isReachable)"
    }

    func start() {
        guard WCSession.isSupported(), observers.isEmpty else {
            return
        }
        let center = NotificationCenter.default
        observers = [
            center.addObserver(forName: ModelContext.didSave, object: nil, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated {
                    self?.send()
                }
            },
            center.addObserver(
                forName: UIApplication.didBecomeActiveNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                MainActor.assumeIsolated {
                    self?.send()
                }
            }
        ]
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    func send(force: Bool = false) {
        let session = WCSession.default
        guard session.activationState == .activated, session.isPaired, session.isWatchAppInstalled else {
            record("Not sent: \(state)")
            return
        }
        let snapshot = snapshot()
        if snapshot == lastSent && !force {
            return
        }
        do {
            try session.updateApplicationContext(snapshot.applicationContext)
            lastSent = snapshot
            let todos = snapshot.places.reduce(0) { $0 + $1.todos.count }
            record("Sent \(snapshot.places.count) places, \(todos) to-dos")
        } catch {
            logger.error("Could not send the snapshot to the Watch: \(error, privacy: .public)")
            record("Send failed: \(error.localizedDescription)")
        }
    }

    private func record(_ event: String) {
        logger.notice("\(event, privacy: .public)")
        lastEvent = "\(Date.now.formatted(date: .omitted, time: .standard)) \(event)"
    }

    private nonisolated func receive(_ message: [String: Any]) {
        guard let checkOff = WatchCheckOff(message: message) else {
            return
        }
        Task { @MainActor in
            record("Received a check-off of \(checkOff.todoIDs.count) from the Watch \(checkOff.source.rawValue)")
            await onCheckOff(checkOff)
        }
    }

    nonisolated func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: (any Error)?
    ) {
        let failure = error?.localizedDescription
        Task { @MainActor in
            if let failure {
                record("Activation failed: \(failure)")
            }
            send()
        }
    }

    nonisolated func sessionWatchStateDidChange(_ session: WCSession) {
        Task { @MainActor in
            lastSent = nil
            send()
        }
    }

    nonisolated func sessionDidBecomeInactive(_ session: WCSession) {}

    // Switching to another Watch deactivates the session, which must be activated again for the new one.
    nonisolated func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }

    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        receive(message)
    }

    nonisolated func session(
        _ session: WCSession,
        didReceiveMessage message: [String: Any],
        replyHandler: @escaping ([String: Any]) -> Void
    ) {
        guard WatchSnapshot.isRequest(message) else {
            receive(message)
            replyHandler([:])
            return
        }
        let reply = Reply(handler: replyHandler)
        Task { @MainActor in
            let snapshot = snapshot()
            let todos = snapshot.places.reduce(0) { $0 + $1.todos.count }
            record("Replied to the Watch with \(snapshot.places.count) places, \(todos) to-dos")
            reply.handler(snapshot.applicationContext)
        }
    }

    nonisolated func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        receive(userInfo)
    }
}

// WatchConnectivity calls the reply handler from any thread; the snapshot is made on the main actor.
private nonisolated final class Reply: @unchecked Sendable {
    let handler: ([String: Any]) -> Void

    init(handler: @escaping ([String: Any]) -> Void) {
        self.handler = handler
    }
}

extension CompletionVia {
    init(_ source: WatchCheckOff.Source) {
        switch source {
        case .app:
            self = .watch
        case .notification:
            self = .watchAction
        }
    }
}
