import SwiftUI

struct TodoListView: View {
    var body: some View {
        NavigationStack {
            Text(.tabTodos)
                .navigationTitle(Text(.tabTodos))
        }
    }
}
