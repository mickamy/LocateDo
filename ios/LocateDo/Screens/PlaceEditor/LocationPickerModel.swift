import CoreLocation
import MapKit
import Observation
import SwiftUI

// What the location picker has found and picked: a tapped point, a searched store, or a list of stores to choose from.
@Observable
final class LocationPickerModel {
    static let listDetent: PresentationDetent = .fraction(0.35)
    private static let storeTapRadius: CLLocationDistance = 20

    var position: MapCameraPosition
    var selection: PlacePick?
    // The pick to hand back once the card has closed; it keeps its pin meanwhile.
    var confirmed: PlacePick?
    var results: [MKMapItem] = []
    var detent: PresentationDetent = listDetent
    var cardHeight: CGFloat = 160
    let completer = PlaceSearchCompleter()
    @ObservationIgnored var currentLocation: () -> CLLocation? = { nil }

    init(initialCoordinate: CLLocationCoordinate2D?) {
        if let initialCoordinate {
            selection = PlacePick(coordinate: initialCoordinate, source: .map)
            position = .region(Self.region(around: initialCoordinate))
        } else {
            position = .userLocation(fallback: .automatic)
        }
    }

    var cardDetent: PresentationDetent {
        .height(cardHeight)
    }

    var pinned: PlacePick? {
        selection ?? confirmed
    }

    var isShowingSheet: Bool {
        selection != nil || !results.isEmpty
    }

    var searchCenter: CLLocationCoordinate2D? {
        selection?.coordinate ?? currentLocation()?.coordinate
    }

    func closeSheet() {
        selection = nil
        results = []
    }

    func clearSelection() {
        selection = nil
        detent = Self.listDetent
    }

    func confirm() {
        confirmed = selection
        closeSheet()
    }

    func selectCurrentLocation() {
        if let current = currentLocation() {
            select(current.coordinate, source: .currentLocation)
        }
    }

    func selectTapped(_ coordinate: CLLocationCoordinate2D) {
        select(coordinate, source: .map)
        Task {
            await lookUpStore(at: coordinate)
        }
    }

    func select(_ item: MKMapItem) {
        select(item.location.coordinate, source: .search, name: item.name, address: item.address?.shortAddress,
               suggestion: CategoryGuess.category(for: item.pointOfInterestCategory))
    }

    func cardHeightChanged(_ height: CGFloat) {
        cardHeight = height
        if selection != nil {
            detent = .height(height)
        }
    }

    func distance(to item: MKMapItem) -> CLLocationDistance? {
        currentLocation()?.distance(from: item.location)
    }

    // Stores first, as the suggestions are; addresses only when no store matches.
    func search(_ query: String) async {
        var items = await mapItems(for: query, types: .pointOfInterest, spanMeters: 20_000)
        if items.isEmpty {
            items = await mapItems(for: query, types: [.pointOfInterest, .address], spanMeters: 20_000)
        }
        show(items)
    }

    // A suggestion names one place, or a kind of place ("coffee") that has many.
    func pick(_ completion: MKLocalSearchCompletion) async {
        let response = try? await MKLocalSearch(request: MKLocalSearch.Request(completion: completion)).start()
        let items = response?.mapItems ?? []
        if items.count == 1, let item = items.first {
            results = []
            select(item)
            return
        }
        show(items)
    }

    // Close by, so the nearest stores of the kind come first rather than the best-known ones in the city.
    func searchNearby(_ kind: String) async {
        show(await mapItems(for: kind, types: .pointOfInterest, spanMeters: 5_000))
    }

    private func select(
        _ coordinate: CLLocationCoordinate2D,
        source: PlaceSource,
        name: String? = nil,
        address: String? = nil,
        suggestion: BuiltinCategory? = nil
    ) {
        selection = PlacePick(coordinate: coordinate, source: source, name: name, address: address,
                              suggestion: suggestion)
        detent = cardDetent
        withAnimation {
            position = .region(Self.region(around: coordinate))
        }
        if address == nil {
            Task {
                await reverseGeocode(coordinate)
            }
        }
    }

    // A tap on a store's label lands within a few meters of it; the nearest store there names the pick and guesses
    // its category.
    private func lookUpStore(at coordinate: CLLocationCoordinate2D) async {
        let request = MKLocalPointsOfInterestRequest(center: coordinate, radius: Self.storeTapRadius)
        let items = (try? await MKLocalSearch(request: request).start())?.mapItems ?? []
        let tapped = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        guard let store = items.min(by: { $0.location.distance(from: tapped) < $1.location.distance(from: tapped) }),
              var current = selection, current.isAt(coordinate) else {
            return
        }
        current.name = store.name
        current.suggestion = CategoryGuess.category(for: store.pointOfInterestCategory)
        selection = current
    }

    private func reverseGeocode(_ coordinate: CLLocationCoordinate2D) async {
        guard let geocoded = await Geocoding.lookUp(coordinate),
              var current = selection, current.isAt(coordinate) else {
            return
        }
        current.name = current.name ?? geocoded.name
        current.address = geocoded.address
        selection = current
    }

    private func mapItems(
        for query: String,
        types: MKLocalSearch.ResultType,
        spanMeters: CLLocationDistance
    ) async -> [MKMapItem] {
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        request.resultTypes = types
        if let center = searchCenter {
            request.region = MKCoordinateRegion(center: center, latitudinalMeters: spanMeters,
                                                longitudinalMeters: spanMeters)
        }
        let response = try? await MKLocalSearch(request: request).start()
        return response?.mapItems ?? []
    }

    private func show(_ items: [MKMapItem]) {
        results = items.sorted { (distance(to: $0) ?? .infinity) < (distance(to: $1) ?? .infinity) }
        selection = nil
        detent = Self.listDetent
    }

    private static func region(around coordinate: CLLocationCoordinate2D) -> MKCoordinateRegion {
        MKCoordinateRegion(center: coordinate, latitudinalMeters: 600, longitudinalMeters: 600)
    }
}
