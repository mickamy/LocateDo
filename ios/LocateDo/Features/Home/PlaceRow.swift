import SwiftUI

struct PlaceRow: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(LocationProvider.self) private var locationProvider

    let nearby: NearbyPlace

    private var place: Place { nearby.place }

    private var layout: AnyLayout {
        if dynamicTypeSize.isAccessibilitySize {
            return AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
        }
        return AnyLayout(HStackLayout(alignment: .top, spacing: 12))
    }

    var body: some View {
        layout {
            Image(systemName: place.category.systemImage)
                .font(.title3)
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(place.category.tint, in: RoundedRectangle(cornerRadius: 8))
                .accessibilityLabel(Text(place.category.title))
            VStack(alignment: .leading, spacing: 4) {
                Text(place.name)
                    .font(.headline)
                ForEach(place.openTodos.prefix(2)) { todo in
                    Text(todo.title)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            if !dynamicTypeSize.isAccessibilitySize {
                Spacer()
            }
            distance
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var distance: some View {
        if let meters = nearby.distanceMeters {
            Text(DistanceFormatting.string(meters: meters))
        } else if locationProvider.isAwaitingLocation {
            Text(DistanceFormatting.placeholder())
                .redacted(reason: .placeholder)
                .accessibilityHidden(true)
        }
    }
}
