import CoreLocation
import MapKit
import SwiftUI

// A point on the map: tapped, searched, or one of the stores of a kind nearby. As the first screen of adding a place it
// hands the pick on; on its own it closes itself with it.
struct LocationPicker: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(LocationProvider.self) private var locationProvider

    let closesOnPick: Bool
    let entry: ScreenEntry?
    let title: LocalizedStringResource
    let initialSearch: LocalizedStringResource?
    let showsCancel: Bool
    let onPick: (PlacePick) -> Void
    @State private var model: LocationPickerModel
    @State private var query = ""
    @State private var isSearching = false
    @State private var hasSearchedInitially = false

    init(
        initialCoordinate: CLLocationCoordinate2D?,
        closesOnPick: Bool,
        entry: ScreenEntry? = nil,
        title: LocalizedStringResource = .placePickerTitle,
        initialSearch: LocalizedStringResource? = nil,
        showsCancel: Bool = true,
        onPick: @escaping (PlacePick) -> Void
    ) {
        self.entry = entry
        self.title = title
        self.initialSearch = initialSearch
        self.showsCancel = showsCancel
        self.closesOnPick = closesOnPick
        self.onPick = onPick
        _model = State(initialValue: LocationPickerModel(initialCoordinate: initialCoordinate))
    }

    var body: some View {
        @Bindable var model = model
        MapReader { proxy in
            Map(position: $model.position) {
                UserAnnotation()
                if let pinned = model.pinned {
                    Marker(coordinate: pinned.coordinate) {
                        Text(.placePickerSelected)
                    }
                }
            }
            .onTapGesture { point in
                if let tapped = proxy.convert(point, from: .local) {
                    model.selectTapped(tapped)
                }
            }
        }
        .safeAreaInset(edge: .top) {
            nearbyKinds
        }
        .trackScreen(.placePicker, opening: entry?.parameters ?? [:])
        .searchable(text: $query, isPresented: $isSearching, prompt: Text(.placePickerSearchPlaceholder))
        .searchSuggestions {
            suggestions
        }
        .onChange(of: query) {
            model.completer.update(query, near: model.searchCenter)
        }
        .onSubmit(of: .search) {
            Task {
                await model.search(query)
            }
        }
        .sheet(isPresented: isSheetPresented, onDismiss: finishIfConfirmed) {
            Group {
                if let selection = model.selection {
                    selectionCard(selection)
                } else {
                    resultList
                }
            }
            .presentationDetents(detents, selection: $model.detent)
            .presentationBackgroundInteraction(.enabled)
            .presentationBackground(.thickMaterial)
            .presentationDragIndicator(.visible)
        }
        .navigationTitle(Text(title))
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.thickMaterial, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbar {
            if showsCancel {
                ToolbarItem(placement: .cancellationAction) {
                    Button(.commonCancel) {
                        dismiss()
                    }
                }
            }
            ToolbarItem(placement: .bottomBar) {
                Button(.placePickerUseCurrentLocation, systemImage: "location", action: model.selectCurrentLocation)
                    .disabled(locationProvider.location == nil)
            }
        }
        .onAppear {
            model.currentLocation = { [locationProvider] in locationProvider.location }
        }
        .task(id: locationProvider.location == nil) {
            await searchInitially()
        }
    }

    // The stores of the kind around you, once where you are is known.
    private func searchInitially() async {
        guard let initialSearch, !hasSearchedInitially, locationProvider.location != nil else {
            return
        }
        hasSearchedInitially = true
        await model.searchNearby(String(localized: initialSearch))
    }

    private var isSheetPresented: Binding<Bool> {
        Binding {
            model.isShowingSheet
        } set: { isPresented in
            if !isPresented {
                model.closeSheet()
            }
        }
    }

    private var detents: Set<PresentationDetent> {
        if model.selection == nil {
            return [LocationPickerModel.listDetent, .large]
        }
        return [model.cardDetent]
    }

    // The picker closes from the card's onDismiss, so the two presentations do not close at the same time.
    private func finishIfConfirmed() {
        guard let confirmed = model.confirmed else {
            return
        }
        onPick(confirmed)
        if closesOnPick {
            dismiss()
        }
    }

    private var suggestions: some View {
        ForEach(model.completer.completions, id: \.self) { completion in
            Button {
                Task {
                    await model.pick(completion)
                    isSearching = false
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

    private var resultList: some View {
        List(model.results, id: \.self) { item in
            Button {
                model.select(item)
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
                    if let meters = model.distance(to: item) {
                        Text(DistanceFormatting.string(meters: meters))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
    }

    private func selectionCard(_ selection: PlacePick) -> some View {
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
                Button(action: model.clearSelection) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title2)
                        .foregroundStyle(.secondary)
                }
                .accessibilityLabel(Text(.commonCancel))
            }
            Button(action: model.confirm) {
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
            model.cardHeightChanged(height)
        }
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
                await model.searchNearby(String(localized: name))
                isSearching = false
            }
        } label: {
            Label(name, systemImage: systemImage)
                .font(.subheadline)
        }
        .buttonStyle(.bordered)
        .buttonBorderShape(.capsule)
        .background(.thickMaterial, in: Capsule())
    }
}
