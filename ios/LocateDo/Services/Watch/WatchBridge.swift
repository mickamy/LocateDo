import Foundation
import OSLog
import SwiftData
import UIKit
import WatchConnectivity

final class WatchBridge: NSObject, WCSessionDelegate {
    var snapshot: () -> WatchSnapshot = { WatchSnapshot(places: []) }
    var onCheckOff: (WatchCheckOff) async -> Void = { _ in }

    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "watch")
    private var lastSent: WatchSnapshot?
    private var observers: [any NSObjectProtocol] = []

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

    func send() {
        let session = WCSession.default
        guard session.activationState == .activated, session.isPaired, session.isWatchAppInstalled else {
            return
        }
        let snapshot = snapshot()
        if snapshot == lastSent {
            return
        }
        do {
            try session.updateApplicationContext(snapshot.applicationContext)
            lastSent = snapshot
        } catch {
            logger.error("Could not send the snapshot to the Watch: \(error, privacy: .public)")
        }
    }

    private nonisolated func receive(_ message: [String: Any]) {
        guard let checkOff = WatchCheckOff(message: message) else {
            return
        }
        Task { @MainActor in
            await onCheckOff(checkOff)
        }
    }

    nonisolated func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: (any Error)?
    ) {
        Task { @MainActor in
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
        receive(message)
        replyHandler([:])
    }

    nonisolated func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        receive(userInfo)
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
