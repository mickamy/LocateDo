import SwiftUI

struct PlaceRow: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

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
            Image(systemName: place.categoryStyle.systemImage)
                .font(.title3)
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(place.categoryStyle.tint, in: RoundedRectangle(cornerRadius: 8))
                .accessibilityLabel(Text(place.categoryStyle.name))
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
            if let distance = nearby.distanceMeters {
                Text(DistanceFormatting.string(meters: distance))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}
