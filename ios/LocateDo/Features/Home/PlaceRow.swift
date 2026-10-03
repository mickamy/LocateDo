import SwiftUI

struct PlaceRow: View {
    let nearby: NearbyPlace

    private var place: Place { nearby.place }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: place.category.systemImage)
                .font(.title3)
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(place.category.tint, in: RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 4) {
                Text(place.name)
                    .font(.headline)
                ForEach(place.openTodos.prefix(2)) { todo in
                    Text(todo.title)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            if let distance = nearby.distanceMeters {
                Text(DistanceFormatting.string(meters: distance))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}
