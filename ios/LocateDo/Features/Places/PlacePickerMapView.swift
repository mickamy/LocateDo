import CoreLocation
import MapKit
import SwiftUI

struct PlacePickerMapView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(LocationProvider.self) private var locationProvider

    let onPick: (CLLocationCoordinate2D, String?, PlaceSource) -> Void

    @State private var position: MapCameraPosition
    @State private var query = ""
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

        func isAt(_ other: CLLocationCoordinate2D) -> Bool {
            coordinate.latitude == other.latitude && coordinate.longitude == other.longitude
        }
    }

    init(
        initialCoordinate: CLLocationCoordinate2D?,
        onPick: @escaping (CLLocationCoordinate2D, String?, PlaceSource) -> Void
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
                    if let selection {
                        Marker(coordinate: selection.coordinate) {
                            Text(.placePickerSelected)
                        }
                    }
                }
                .onTapGesture { point in
                    if let tapped = proxy.convert(point, from: .local) {
                        select(tapped, source: .map, name: nil, address: nil)
                    }
                }
            }
            .trackScreen(.placePicker)
            .searchable(text: $query, prompt: Text(.placePickerSearchPlaceholder))
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
                select(item.location.coordinate, source: .search, name: item.name, address: item.address?.shortAddress)
            } label: {
                VStack(alignment: .leading) {
                    Text(item.name ?? "")
                        .foregroundStyle(.primary)
                    if let address = item.address?.shortAddress {
                        Text(address)
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

    private func select(_ coordinate: CLLocationCoordinate2D, source: PlaceSource, name: String?, address: String?) {
        selection = Selection(coordinate: coordinate, source: source, name: name, address: address)
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

    // The picker closes from the sheet's onDismiss so the two presentations
    // do not dismiss at the same time.
    private func finishIfConfirmed() {
        guard let confirmed else {
            return
        }
        onPick(confirmed.coordinate, confirmed.name, confirmed.source)
        dismiss()
    }

    private func search() async {
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        if let center = selection?.coordinate ?? locationProvider.location?.coordinate {
            request.region = MKCoordinateRegion(center: center, latitudinalMeters: 20_000, longitudinalMeters: 20_000)
        }
        let response = try? await MKLocalSearch(request: request).start()
        results = response?.mapItems ?? []
        selection = nil
        detent = Self.listDetent
    }

    private static func region(around coordinate: CLLocationCoordinate2D) -> MKCoordinateRegion {
        MKCoordinateRegion(center: coordinate, latitudinalMeters: 600, longitudinalMeters: 600)
    }
}
