import SwiftUI

struct PlaceListView: View {
    @Environment(WatchStore.self) private var store

    var body: some View {
        NavigationStack {
            content
                .navigationTitle(Text(.appName))
                .navigationDestination(for: WatchSnapshot.Place.self) { place in
                    PlaceTodosView(place: place)
                }
        }
    }

    @ViewBuilder
    private var content: some View {
        let places = store.snapshot?.places ?? []
        if places.isEmpty {
            ContentUnavailableView {
                Label(.watchEmptyTitle, systemImage: "checklist")
            } description: {
                Text(.watchEmptyMessage)
            }
        } else {
            List(places) { place in
                NavigationLink(value: place) {
                    PlaceRow(place: place)
                }
            }
        }
    }
}

private struct PlaceRow: View {
    let place: WatchSnapshot.Place

    var body: some View {
        HStack(spacing: 8) {
            CategoryBadge(icon: place.categoryIcon, color: place.categoryColor)
            Text(place.name)
                .lineLimit(2)
            Spacer(minLength: 0)
            Text(place.todos.count, format: .number)
                .foregroundStyle(.secondary)
        }
    }
}

struct CategoryBadge: View {
    let icon: String?
    let color: String?

    var body: some View {
        Image(systemName: CategoryAppearance.systemImage(forIcon: icon))
            .font(.footnote.weight(.semibold))
            .foregroundStyle(.white)
            .frame(width: 24, height: 24)
            .background(CategoryAppearance.tint(forColor: color), in: RoundedRectangle(cornerRadius: 6))
            .accessibilityHidden(true)
    }
}
