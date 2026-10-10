import MapKit
import Observation

// Suggestions while typing, stores near the user first; addresses only when no store matches, since a place in this
// app is usually a store, but home or the office is an address.
@Observable
final class PlaceSearchCompleter: NSObject, MKLocalSearchCompleterDelegate {
    private(set) var completions: [MKLocalSearchCompletion] = []

    @ObservationIgnored private let completer = MKLocalSearchCompleter()
    @ObservationIgnored private var fragment = ""

    override init() {
        super.init()
        completer.delegate = self
        completer.resultTypes = .pointOfInterest
    }

    func update(_ query: String, near center: CLLocationCoordinate2D?) {
        fragment = query.trimmingCharacters(in: .whitespaces)
        if fragment.isEmpty {
            completer.cancel()
            completions = []
            return
        }
        if let center {
            completer.region = MKCoordinateRegion(center: center, latitudinalMeters: 10_000, longitudinalMeters: 10_000)
        }
        completer.resultTypes = .pointOfInterest
        completer.queryFragment = fragment
    }

    nonisolated func completerDidUpdateResults(_: MKLocalSearchCompleter) {
        MainActor.assumeIsolated {
            if completer.results.isEmpty, completer.resultTypes == .pointOfInterest, !fragment.isEmpty {
                completer.resultTypes = [.pointOfInterest, .address]
                completer.queryFragment = fragment
                return
            }
            completions = completer.results
        }
    }

    nonisolated func completer(_: MKLocalSearchCompleter, didFailWithError error: any Error) {
        MainActor.assumeIsolated {
            completions = []
        }
    }
}
