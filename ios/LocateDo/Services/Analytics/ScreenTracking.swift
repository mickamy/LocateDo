import SwiftUI

nonisolated enum EditorMode: String {
    case new
    case edit

    init(editing existing: AnyObject?) {
        if existing == nil {
            self = .new
        } else {
            self = .edit
        }
    }
}

// Where a screen was opened from. Sent only when the screen first appears, so screen views with it count opens.
nonisolated extension ScreenEntry {
    var parameters: AnalyticsParameters {
        [.entry: rawValue]
    }
}

// The screen last shown on each presentation level: 0 is Home and what is pushed over it, each sheet one more.
nonisolated struct ScreenStack {
    struct Entry {
        let screen: AnalyticsScreen
        let parameters: AnalyticsParameters
    }

    private var entries: [Int: Entry] = [:]

    var screens: [AnalyticsScreen] {
        entries.keys.sorted().compactMap { entries[$0]?.screen }
    }

    mutating func appeared(_ entry: Entry, at level: Int) {
        entries = entries.filter { $0.key < level }
        entries[level] = entry
    }

    // The screen uncovered once the levels from `level` up close, or nil when nothing up there was on screen.
    mutating func closed(from level: Int) -> Entry? {
        guard entries.keys.contains(where: { $0 >= level }) else {
            return nil
        }
        entries = entries.filter { $0.key < level }
        guard let top = entries.keys.max() else {
            return nil
        }
        return entries[top]
    }
}

// A closing sheet does not make the screen below appear again, so it is sent again here; otherwise GA4 would keep
// the sheet as the current screen.
enum ScreenTracker {
    private static var stack = ScreenStack()

    static func appeared(
        _ screen: AnalyticsScreen,
        parameters: AnalyticsParameters,
        opening: AnalyticsParameters,
        level: Int
    ) {
        Analytics.logScreen(screen, parameters: parameters.merging(opening) { current, _ in current })
        stack.appeared(ScreenStack.Entry(screen: screen, parameters: parameters), at: level)
    }

    static func closed(from level: Int) {
        if let uncovered = stack.closed(from: level) {
            Analytics.logScreen(uncovered.screen, parameters: uncovered.parameters)
        }
    }
}

private nonisolated struct PresentationLevelKey: EnvironmentKey {
    static let defaultValue = 0
}

nonisolated extension EnvironmentValues {
    var presentationLevel: Int {
        get { self[PresentationLevelKey.self] }
        set { self[PresentationLevelKey.self] = newValue }
    }
}

private struct ScreenTracking: ViewModifier {
    @Environment(\.presentationLevel) private var level
    @State private var hasAppeared = false
    let screen: AnalyticsScreen
    let parameters: AnalyticsParameters
    let opening: AnalyticsParameters

    func body(content: Content) -> some View {
        content.onAppear {
            var sentOnce: AnalyticsParameters = [:]
            if !hasAppeared {
                sentOnce = opening
            }
            hasAppeared = true
            ScreenTracker.appeared(screen, parameters: parameters, opening: sentOnce, level: level)
        }
    }
}

private struct CoveringScreen: ViewModifier {
    @Environment(\.presentationLevel) private var below

    func body(content: Content) -> some View {
        content
            .environment(\.presentationLevel, below + 1)
            .onDisappear {
                ScreenTracker.closed(from: below + 1)
            }
    }
}

extension View {
    // `opening` goes only with the first appearance, not when the screen is come back to.
    func trackScreen(
        _ screen: AnalyticsScreen,
        parameters: AnalyticsParameters = [:],
        opening: AnalyticsParameters = [:]
    ) -> some View {
        modifier(ScreenTracking(screen: screen, parameters: parameters, opening: opening))
    }

    // For the content of a sheet: its screens sit a level up, and the screen below is current again once it closes.
    func coversScreen() -> some View {
        modifier(CoveringScreen())
    }
}
