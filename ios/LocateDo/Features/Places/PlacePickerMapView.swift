import CoreLocation
import MapKit
import SwiftUI

struct PlacePickerMapView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(LocationProvider.self) private var locationProvider

    let onPick: (CLLocationCoordinate2D, String?, PlaceSource, BuiltinCategory?) -> Void

    @State private var position: MapCameraPosition
    @State private var query = ""
    @State private var isSearching = false
    @State private var completer = PlaceSearchCompleter()
    @State private var results: [MKMapItem] = []
    @State private var selection: Selection?
    @State private var confirmed: Selection?
    @State private var detent: PresentationDetent = Self.listDetent
    @State private var cardHeight: CGFloat = 160

    private static let listDetent: PresentationDetent = .fraction(0.35)

    private var cardDetent: PresentationDetent {
        .height(cardHeight)
    }

    private struct Selection {
        let coordinate: CLLocationCoordinate2D
        let source: PlaceSource
        var name: String?
        var address: String?
        var suggestion: BuiltinCategory?

        func isAt(_ other: CLLocationCoordinate2D) -> Bool {
            coordinate.latitude == other.latitude && coordinate.longitude == other.longitude
        }
    }

    init(
        initialCoordinate: CLLocationCoordinate2D?,
        onPick: @escaping (CLLocationCoordinate2D, String?, PlaceSource, BuiltinCategory?) -> Void
    ) {
        self.onPick = onPick
        if let initialCoordinate {
            _selection = State(initialValue: Selection(coordinate: initialCoordinate, source: .map))
            _position = State(initialValue: .region(Self.region(around: initialCoordinate)))
        } else {
            _position = State(initialValue: .userLocation(fallback: .automatic))
        }
    }

    private var isSheetPresented: Binding<Bool> {
        Binding(
            get: { selection != nil || !results.isEmpty },
            set: { presented in
                if !presented {
                    selection = nil
                    results = []
                }
            }
        )
    }

    var body: some View {
        NavigationStack {
            MapReader { proxy in
                Map(position: $position) {
                    UserAnnotation()
                    // The confirmed pick keeps its pin while the picker closes.
                    if let pinned = selection ?? confirmed {
                        Marker(coordinate: pinned.coordinate) {
                            Text(.placePickerSelected)
                        }
                    }
                }
                .onTapGesture { point in
                    if let tapped = proxy.convert(point, from: .local) {
                        selectTapped(tapped)
                    }
                }
            }
            .safeAreaInset(edge: .top) {
                nearbyKinds
            }
            .trackScreen(.placePicker)
            .searchable(text: $query, isPresented: $isSearching, prompt: Text(.placePickerSearchPlaceholder))
            .searchSuggestions {
                ForEach(completer.completions, id: \.self) { completion in
                    Button {
                        Task {
                            await pick(completion)
                        }
                    } label: {
                        VStack(alignment: .leading) {
                            Text(completion.title)
                                .foregroundStyle(.primary)
                            if !completion.subtitle.isEmpty {
                                Text(completion.subtitle)
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .onChange(of: query) {
                completer.update(query, near: searchCenter)
            }
            .onSubmit(of: .search) {
                Task {
                    await search()
                }
            }
            .sheet(isPresented: isSheetPresented, onDismiss: finishIfConfirmed) {
                Group {
                    if let selection {
                        selectionCard(selection)
                    } else {
                        resultList
                    }
                }
                .presentationDetents(selection == nil ? [Self.listDetent, .large] : [cardDetent], selection: $detent)
                .presentationBackgroundInteraction(.enabled)
                .presentationBackground(.thickMaterial)
                .presentationDragIndicator(.visible)
            }
            .navigationTitle(Text(.placePickerTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.thickMaterial, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(.commonCancel) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .bottomBar) {
                    Button(.placePickerUseCurrentLocation, systemImage: "location") {
                        if let current = locationProvider.location {
                            select(current.coordinate, source: .currentLocation, name: nil, address: nil)
                        }
                    }
                    .disabled(locationProvider.location == nil)
                }
            }
        }
    }

    private var resultList: some View {
        List(results, id: \.self) { item in
            Button {
                select(item.location.coordinate, source: .search, name: item.name, address: item.address?.shortAddress,
                       suggestion: CategoryGuess.category(for: item.pointOfInterestCategory))
            } label: {
                HStack {
                    VStack(alignment: .leading) {
                        Text(item.name ?? "")
                            .foregroundStyle(.primary)
                        if let address = item.address?.shortAddress {
                            Text(address)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                    if let meters = distance(to: item) {
                        Text(DistanceFormatting.string(meters: meters))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
    }

    private func selectionCard(_ selection: Selection) -> some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    if let name = selection.name {
                        Text(name)
                            .font(.headline)
                    } else {
                        Text(.placePickerSelected)
                            .font(.headline)
                    }
                    if let address = selection.address {
                        Text(address)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                Button {
                    self.selection = nil
                    detent = Self.listDetent
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title2)
                        .foregroundStyle(.secondary)
                }
                .accessibilityLabel(Text(.commonCancel))
            }
            Button {
                confirmed = selection
                self.selection = nil
                results = []
            } label: {
                Text(.placePickerUseThisLocation)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .padding(.horizontal, 20)
        .padding(.top, 28)
        .padding(.bottom, 12)
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.size.height
        } action: { height in
            cardHeight = height
            if self.selection != nil {
                detent = .height(height)
            }
        }
    }

    // The picker closes from the sheet's onDismiss so the two presentations
    // do not dismiss at the same time.
    private func finishIfConfirmed() {
        guard let confirmed else {
            return
        }
        onPick(confirmed.coordinate, confirmed.name, confirmed.source, confirmed.suggestion)
        dismiss()
    }

    private static func region(around coordinate: CLLocationCoordinate2D) -> MKCoordinateRegion {
        MKCoordinateRegion(center: coordinate, latitudinalMeters: 600, longitudinalMeters: 600)
    }
}

extension PlacePickerMapView {
    private static let storeTapRadius: CLLocationDistance = 20

    private func selectTapped(_ coordinate: CLLocationCoordinate2D) {
        select(coordinate, source: .map, name: nil, address: nil)
        Task {
            await lookUpStore(at: coordinate)
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

    private func select(
        _ coordinate: CLLocationCoordinate2D,
        source: PlaceSource,
        name: String?,
        address: String?,
        suggestion: BuiltinCategory? = nil
    ) {
        selection = Selection(coordinate: coordinate, source: source, name: name, address: address,
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

    private func reverseGeocode(_ coordinate: CLLocationCoordinate2D) async {
        guard let geocoded = await Geocoding.lookUp(coordinate),
              var current = selection, current.isAt(coordinate) else {
            return
        }
        current.name = current.name ?? geocoded.name
        current.address = geocoded.address
        selection = current
    }

    private var searchCenter: CLLocationCoordinate2D? {
        selection?.coordinate ?? locationProvider.location?.coordinate
    }

    // Stores first, as the suggestions are; addresses only when no store matches.
    private func search() async {
        var items = await mapItems(for: query, types: .pointOfInterest)
        if items.isEmpty {
            items = await mapItems(for: query, types: [.pointOfInterest, .address])
        }
        show(items)
    }

    private func mapItems(for query: String, types: MKLocalSearch.ResultType) async -> [MKMapItem] {
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        request.resultTypes = types
        if let center = searchCenter {
            request.region = MKCoordinateRegion(center: center, latitudinalMeters: 20_000, longitudinalMeters: 20_000)
        }
        let response = try? await MKLocalSearch(request: request).start()
        return response?.mapItems ?? []
    }

    // A suggestion names one place, or a kind of place ("coffee") that has many.
    private func pick(_ completion: MKLocalSearchCompletion) async {
        let response = try? await MKLocalSearch(request: MKLocalSearch.Request(completion: completion)).start()
        let items = response?.mapItems ?? []
        isSearching = false
        if items.count == 1, let item = items.first {
            results = []
            select(item.location.coordinate, source: .search, name: item.name, address: item.address?.shortAddress,
                   suggestion: CategoryGuess.category(for: item.pointOfInterestCategory))
            return
        }
        show(items)
    }

    private func show(_ items: [MKMapItem]) {
        results = items.sorted { (distance(to: $0) ?? .infinity) < (distance(to: $1) ?? .infinity) }
        selection = nil
        detent = Self.listDetent
    }

    private func distance(to item: MKMapItem) -> CLLocationDistance? {
        locationProvider.location?.distance(from: item.location)
    }

    // The kinds of store people add most, one tap from the stores of that kind around them.
    private var nearbyKinds: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                nearbyKind(.placePickerNearbyGrocery, systemImage: "cart")
                nearbyKind(.placePickerNearbyDrugstore, systemImage: "cross.case")
                nearbyKind(.placePickerNearbyConvenience, systemImage: "storefront")
                nearbyKind(.placePickerNearbyHardware, systemImage: "hammer")
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
    }

    private func nearbyKind(_ name: LocalizedStringResource, systemImage: String) -> some View {
        Button {
            Task {
                await searchNearby(String(localized: name))
            }
        } label: {
            Label(name, systemImage: systemImage)
                .font(.subheadline)
        }
        .buttonStyle(.bordered)
        .buttonBorderShape(.capsule)
        .background(.thickMaterial, in: Capsule())
    }

    // Close by, so the nearest stores of the kind come first rather than the best-known ones in the city.
    private func searchNearby(_ kind: String) async {
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = kind
        request.resultTypes = .pointOfInterest
        if let center = searchCenter {
            request.region = MKCoordinateRegion(center: center, latitudinalMeters: 5_000, longitudinalMeters: 5_000)
        }
        let response = try? await MKLocalSearch(request: request).start()
        isSearching = false
        show(response?.mapItems ?? [])
    }
}
