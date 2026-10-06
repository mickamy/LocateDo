import SwiftUI

private struct ScreenTracking: ViewModifier {
    let screen: AnalyticsScreen
    let parameters: AnalyticsParameters

    func body(content: Content) -> some View {
        content.onAppear {
            Analytics.logScreen(screen, parameters: parameters)
        }
    }
}

extension View {
    func trackScreen(_ screen: AnalyticsScreen, parameters: AnalyticsParameters = [:]) -> some View {
        modifier(ScreenTracking(screen: screen, parameters: parameters))
    }
}
