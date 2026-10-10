import MapKit
import SwiftData
import SwiftUI

struct MapTabView: View {
    @Environment(AppRouter.self) private var router
    @Query(sort: \Place.sortOrder) private var places: [Place]
    @State private var position: MapCameraPosition = .userLocation(fallback: .automatic)
    @State private var selectedPlace: Place?
    @State private var path: [Place] = []
    @State private var isAddingTodo = false

    var body: some View {
        NavigationStack(path: $path) {
            Map(position: $position, selection: $selectedPlace) {
                UserAnnotation()
                ForEach(places) { place in
                    Marker(place.name, systemImage: place.categoryStyle.systemImage, coordinate: place.coordinate)
                        .tint(place.categoryStyle.tint)
                        .tag(place)
                }
            }
            .mapControls {
                MapUserLocationButton()
                MapCompass()
            }
            .safeAreaInset(edge: .bottom) {
                if places.isEmpty {
                    emptyCard
                }
            }
            .onAppear {
                Analytics.logScreen(.map, parameters: [.source: router.takeMapSource()])
            }
            .navigationTitle(Text(.tabMap))
            .maintenanceBanner()
            .navigationBarTitleDisplayMode(.inline)
            .onChange(of: selectedPlace) {
                if let selectedPlace {
                    path.append(selectedPlace)
                    self.selectedPlace = nil
                }
            }
            .navigationDestination(for: Place.self) { place in
                PlaceDetailView(place: place)
            }
        }
        .floatingAddButton(.todoEditorTitle, isShown: !path.isEmpty) {
            isAddingTodo = true
        }
        .sheet(isPresented: $isAddingTodo) {
            TodoEditorView(place: path.last) { added in
                path.append(added)
            }
        }
    }

    private var emptyCard: some View {
        VStack(spacing: 12) {
            Text(.homeEmptyTitle)
                .font(.headline)
            Text(.mapEmptyMessage)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button(.homeAddPlace) {
                router.requestAddPlace()
            }
            .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        .padding()
    }
}
