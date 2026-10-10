import Observation
import SwiftUI

// The one place that moves between screens and presents over them. Sheets stack, each presented by the one below it.
// Requests from outside the screens (notifications, links, sync) wait while anything is presented, and go out once
// the bottom sheet has closed.
@Observable
final class Navigator {
    var path: [Route] = []
    private(set) var sheets: [Sheet] = []
    var confirmation: Confirmation?
    // A notification names a place by id; the screens resolve it once the place is there, which a pull may still bring.
    var pendingPlaceID: UUID?
    @ObservationIgnored private var waiting: [Sheet] = []
    // Set when a new place is saved, so the reminder setup it may need is offered once the sheets close.
    @ObservationIgnored var didAddPlace = false

    var isPresenting: Bool {
        !sheets.isEmpty || confirmation != nil
    }

    var hasWaiting: Bool {
        !waiting.isEmpty || pendingPlaceID != nil
    }

    func push(_ route: Route) {
        path.append(route)
    }

    func present(_ sheet: Sheet) {
        sheets.append(sheet)
    }

    func dismissAll() {
        sheets.removeAll()
    }

    func confirm(_ confirmation: Confirmation) {
        self.confirmation = confirmation
    }

    func request(_ sheet: Sheet) {
        if isPresenting {
            waiting.append(sheet)
            return
        }
        present(sheet)
    }

    func presentWaiting() {
        guard !isPresenting, !waiting.isEmpty else {
            return
        }
        present(waiting.removeFirst())
    }

    func open(placeID: UUID) {
        pendingPlaceID = placeID
    }

    func show(_ place: Place) {
        pendingPlaceID = nil
        closeSettings()
        path = [.place(place)]
    }

    func openAllTodos() {
        closeSettings()
        path = [.allTodos]
    }

    // What a notification opens replaces Settings, the one sheet left open while just looking around.
    private func closeSettings() {
        if case .settings = sheets.first {
            dismissAll()
        }
    }

    func sheet(at level: Int) -> Binding<Sheet?> {
        Binding {
            if self.sheets.indices.contains(level) {
                return self.sheets[level]
            }
            return nil
        } set: { sheet in
            if sheet == nil, self.sheets.indices.contains(level) {
                self.sheets.removeSubrange(level...)
            }
        }
    }
}
