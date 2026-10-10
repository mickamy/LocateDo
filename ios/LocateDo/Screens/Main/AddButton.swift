import SwiftData
import SwiftUI

// Stays put across screens and changes with them: on Home a plus that offers a to-do or a place, on a place or the
// list of to-dos a labeled button that adds a to-do, and nothing over the map.
struct AddButton: View {
    static let height: CGFloat = 48
    static let spacing: CGFloat = 20
    // Room lists leave below their last row, so it can scroll above the button.
    static let reservedHeight = height + spacing

    @Environment(Navigator.self) private var navigator
    @Environment(LocationProvider.self) private var locationProvider
    @Query private var places: [Place]

    var body: some View {
        Group {
            switch navigator.path.last {
            case nil:
                homeMenu
            case .place(let place):
                addTodoButton(at: place)
            case .allTodos:
                addTodoButton(at: nil)
            case .map:
                EmptyView()
            }
        }
        .padding(.trailing, Self.spacing)
        .padding(.bottom, Self.spacing)
        .animation(.snappy, value: navigator.path.isEmpty)
    }

    private var homeMenu: some View {
        Menu {
            Button(.todoEditorTitle, systemImage: "checklist") {
                navigator.present(.addTodo(place: nearestPlace))
            }
            Button(.homeAddPlace, systemImage: "mappin.and.ellipse") {
                navigator.present(.addPlace(AddPlace()))
            }
        } label: {
            Label(.commonAdd, systemImage: "plus")
                .labelStyle(.iconOnly)
                .font(.title3.weight(.semibold))
        }
        .menuOrder(.fixed)
        // The system style, unlike a glass effect on the label, keeps its tint off the menu it opens into.
        .buttonStyle(.glassProminent)
        .buttonBorderShape(.circle)
        .controlSize(.large)
        .accessibilityIdentifier("home.add")
    }

    private func addTodoButton(at place: Place?) -> some View {
        Button {
            navigator.present(.addTodo(place: place))
        } label: {
            Label(.todoEditorTitle, systemImage: "plus")
                .font(.body.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 20)
                .frame(height: Self.height)
                .glassEffect(.regular.tint(.accentColor).interactive(), in: .capsule)
        }
        .buttonStyle(.plain)
    }

    private var nearestPlace: Place? {
        Nearby.places(places, from: locationProvider.location).first?.place
    }
}
