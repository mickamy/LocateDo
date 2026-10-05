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
    private var serviceSession: CLServiceSession?
    private var eventLoop: Task<Void, Never>?
    private var authorizationWatch: Task<Void, Never>?
    @ObservationIgnored var onArrival: () async -> Void = {}
    @ObservationIgnored var currentUserID: () -> UUID? = { nil }

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
            guard let record = await monitor.record(for: identifier) else {
                logger.notice("Missing record for \(identifier, privacy: .public); removing")
                await monitor.remove(identifier)
                continue
            }
            if let region = GeofenceRegion(identifier: identifier, condition: record.condition) {
                current[identifier] = region
            } else {
                let type = String(describing: Swift.type(of: record.condition))
                logger.notice("Unreadable record \(identifier, privacy: .public) (\(type, privacy: .public)); removing")
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
        logger.notice("Synced \(desired.count) regions (+\(changes.add.count) -\(changes.remove.count))")
        if !changes.add.isEmpty || !changes.remove.isEmpty {
            let added = changes.add.map(\.identifier).joined(separator: ",")
            let removed = changes.remove.joined(separator: ",")
            logger.notice("Added [\(added, privacy: .public)] removed [\(removed, privacy: .public)]")
        }
    }

    private func run() async {
        await authorizationDecided()
        updateServiceSession(for: locationProvider.authorizationStatus)
        authorizationWatch = Task { [locationProvider] in
            for await status in Observations({ locationProvider.authorizationStatus }) {
                updateServiceSession(for: status)
            }
        }
        let monitor = await CLMonitor("LocateDoPlaces")
        self.monitor = monitor
        let monitored = await monitor.identifiers.count
        logger.notice("Monitor started with \(monitored) regions")
        if ProcessInfo.processInfo.arguments.contains("-resetGeofences") {
            for identifier in await monitor.identifiers {
                await monitor.remove(identifier)
            }
            logger.notice("Removed all regions (-resetGeofences)")
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

    // CLMonitor opens an implicit service session that prompts for When In Use, which would preempt onboarding.
    private func authorizationDecided() async {
        if locationProvider.authorizationStatus != .notDetermined {
            return
        }
        for await status in Observations({ [locationProvider] in locationProvider.authorizationStatus })
        where status != .notDetermined {
            return
        }
    }

    // Creating a session that requires Always prompts when the status is undetermined, which would preempt onboarding.
    private func updateServiceSession(for status: CLAuthorizationStatus) {
        guard status == .authorizedAlways else {
            serviceSession?.invalidate()
            serviceSession = nil
            return
        }
        if serviceSession == nil {
            serviceSession = CLServiceSession(authorization: .always)
            logger.notice("Service session started")
        }
    }

    private func handle(_ event: CLMonitor.Event) async {
        let id = event.identifier
        let state = Self.describe(event.state)
        let flags = Self.diagnostics(event)
        logger.notice("Event \(id, privacy: .public) \(state, privacy: .public) [\(flags, privacy: .public)]")
        guard event.state == .satisfied, let placeID = UUID(uuidString: event.identifier) else {
            return
        }
        await arrived(at: placeID)
        await onArrival()
    }

    #if DEBUG || STAGING
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
            logger.notice("No place for \(placeID.uuidString, privacy: .public)")
            return
        }
        let openTodos = NotificationPolicy.notifiableTodos(place.openTodos, for: currentUserID())
        let now = Date()
        let shouldNotify = NotificationPolicy.shouldNotify(
            openTodoCount: openTodos.count,
            lastNotifiedAt: place.lastNotifiedAt,
            now: now
        )
        guard shouldNotify else {
            logger.notice("Skipped notification for \(place.name, privacy: .public): \(openTodos.count) open todos")
            return
        }
        await notifier.notifyArrival(at: place, todoTitles: openTodos.map(\.title))
        place.lastNotifiedAt = now
        try? context.save()
        Analytics.log(.arrivalNotified, parameters: ["open_todos": openTodos.count])
        logger.notice("Notified arrival at \(place.name, privacy: .public)")
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
        return active.isEmpty ? "none" : active.joined(separator: ", ")
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
