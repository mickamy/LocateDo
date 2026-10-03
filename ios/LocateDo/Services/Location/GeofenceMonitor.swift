import CoreLocation
import Observation
import OSLog
import SwiftData

@Observable
final class GeofenceMonitor {
    private let container: ModelContainer
    private let notifier: ArrivalNotifier
    private let locationProvider: LocationProvider
    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "geofence")
    private var monitor: CLMonitor?
    private var eventLoop: Task<Void, Never>?

    init(container: ModelContainer, notifier: ArrivalNotifier, locationProvider: LocationProvider) {
        self.container = container
        self.notifier = notifier
        self.locationProvider = locationProvider
    }

    func start() {
        if eventLoop != nil {
            return
        }
        eventLoop = Task {
            await run()
        }
    }

    func sync() async {
        guard let monitor else {
            return
        }
        let places = (try? container.mainContext.fetch(FetchDescriptor<Place>())) ?? []
        let desired = GeofencePlan.regions(for: places, near: locationProvider.location)

        var current: [String: GeofenceRegion] = [:]
        for identifier in await monitor.identifiers {
            if let record = await monitor.record(for: identifier),
               let region = GeofenceRegion(identifier: identifier, condition: record.condition) {
                current[identifier] = region
            } else {
                await monitor.remove(identifier)
            }
        }

        let changes = GeofencePlan.changes(from: current, to: desired)
        for identifier in changes.remove {
            await monitor.remove(identifier)
        }
        for region in changes.add {
            if current[region.identifier] != nil {
                await monitor.remove(region.identifier)
            }
            let condition = CLMonitor.CircularGeographicCondition(center: region.center, radius: region.radiusMeters)
            await monitor.add(condition, identifier: region.identifier, assuming: .unsatisfied)
        }
        logger.info("Synced \(desired.count) regions (+\(changes.add.count) -\(changes.remove.count))")
    }

    private func run() async {
        let monitor = await CLMonitor("LocateDoPlaces")
        self.monitor = monitor
        if ProcessInfo.processInfo.arguments.contains("-resetGeofences") {
            for identifier in await monitor.identifiers {
                await monitor.remove(identifier)
            }
            logger.info("Removed all regions (-resetGeofences)")
        }
        await sync()
        do {
            for try await event in await monitor.events {
                await handle(event)
            }
        } catch {
            logger.error("Event stream ended: \(error, privacy: .public)")
        }
    }

    private func handle(_ event: CLMonitor.Event) async {
        logger.info("Event \(event.identifier, privacy: .public) \(Self.describe(event.state), privacy: .public)")
        if event.state == .unmonitored {
            logger.error("Unmonitored: \(Self.diagnostics(event), privacy: .public)")
        }
        guard event.state == .satisfied, let placeID = UUID(uuidString: event.identifier) else {
            return
        }
        await arrived(at: placeID)
    }

    #if DEBUG
    func simulateArrival(at place: Place) async {
        place.lastNotifiedAt = nil
        await arrived(at: place.id)
    }
    #endif

    private func arrived(at placeID: UUID) async {
        let context = container.mainContext
        var descriptor = FetchDescriptor<Place>(predicate: #Predicate { $0.id == placeID })
        descriptor.fetchLimit = 1
        guard let place = try? context.fetch(descriptor).first else {
            return
        }
        let openTodos = place.openTodos
        let now = Date()
        let shouldNotify = NotificationPolicy.shouldNotify(
            openTodoCount: openTodos.count,
            lastNotifiedAt: place.lastNotifiedAt,
            now: now
        )
        guard shouldNotify else {
            logger.info("Skipped notification for \(place.name, privacy: .public)")
            return
        }
        await notifier.notifyArrival(at: place, todoTitles: openTodos.map(\.title))
        place.lastNotifiedAt = now
        try? context.save()
        logger.info("Notified arrival at \(place.name, privacy: .public)")
    }

    private static func diagnostics(_ event: CLMonitor.Event) -> String {
        let flags: [(String, Bool)] = [
            ("authorizationDenied", event.authorizationDenied),
            ("authorizationDeniedGlobally", event.authorizationDeniedGlobally),
            ("authorizationRestricted", event.authorizationRestricted),
            ("authorizationRequestInProgress", event.authorizationRequestInProgress),
            ("insufficientlyInUse", event.insufficientlyInUse),
            ("accuracyLimited", event.accuracyLimited),
            ("conditionUnsupported", event.conditionUnsupported),
            ("conditionLimitExceeded", event.conditionLimitExceeded),
            ("persistenceUnavailable", event.persistenceUnavailable),
            ("serviceSessionRequired", event.serviceSessionRequired)
        ]
        let active = flags.filter(\.1).map(\.0)
        return active.isEmpty ? "no diagnostic flags" : active.joined(separator: ", ")
    }

    private static func describe(_ state: CLMonitor.Event.State) -> String {
        switch state {
        case .unknown: "unknown"
        case .satisfied: "satisfied"
        case .unsatisfied: "unsatisfied"
        case .unmonitored: "unmonitored"
        @unknown default: "state(\(state.rawValue))"
        }
    }
}
