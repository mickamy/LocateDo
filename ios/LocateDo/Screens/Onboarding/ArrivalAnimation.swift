import SwiftUI

// The moment the app is for, drawn rather than mapped: you walk down the street into the store's circle, and the
// notification arrives. It plays on a loop; with Reduce Motion it stays on the arrival.
struct ArrivalAnimation: View {
    private enum Phase {
        case walking
        case arrived
        case notified
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var progress: CGFloat = 0
    @State private var phase = Phase.walking

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            ZStack(alignment: .top) {
                map(size)
                SampleArrivalNotification(isShown: phase == .notified)
                    .padding(12)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 28))
        .accessibilityHidden(true)
        .task {
            await play()
        }
    }

    private func map(_ size: CGSize) -> some View {
        let road = size.height * 0.68
        let store = CGPoint(x: size.width * 0.74, y: road)
        let start = CGPoint(x: size.width * 0.06, y: road)
        let end = CGPoint(x: store.x - 24, y: road)
        let walker = CGPoint(x: start.x + (end.x - start.x) * progress, y: road)
        return ZStack {
            Color(.secondarySystemBackground)
            RoundedRectangle(cornerRadius: 12)
                .fill(.green.opacity(0.18))
                .frame(width: size.width * 0.3, height: size.height * 0.28)
                .position(x: size.width * 0.18, y: size.height * 0.36)
            Path { path in
                path.move(to: CGPoint(x: 0, y: road))
                path.addLine(to: CGPoint(x: size.width, y: road))
                path.move(to: CGPoint(x: size.width * 0.44, y: 0))
                path.addLine(to: CGPoint(x: size.width * 0.44, y: size.height))
            }
            .stroke(Color(.systemBackground), lineWidth: 16)
            Circle()
                .fill(.tint.opacity(phase == .walking ? 0.12 : 0.28))
                .stroke(.tint, style: StrokeStyle(lineWidth: 2, dash: [6, 4]))
                .frame(width: 104, height: 104)
                .scaleEffect(phase == .walking ? 1 : 1.08)
                .position(store)
            Image(systemName: "cart.fill")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 40, height: 40)
                .background(.tint, in: Circle())
                .position(store)
            Circle()
                .fill(.blue)
                .stroke(.white, lineWidth: 3)
                .frame(width: 20, height: 20)
                .shadow(color: .black.opacity(0.2), radius: 3)
                .position(walker)
        }
    }

    private func play() async {
        if reduceMotion {
            progress = 1
            phase = .notified
            return
        }
        while !Task.isCancelled {
            progress = 0
            phase = .walking
            try? await Task.sleep(for: .milliseconds(400))
            withAnimation(.easeInOut(duration: 2.2)) {
                progress = 1
            }
            try? await Task.sleep(for: .milliseconds(2_200))
            withAnimation(.spring(duration: 0.4)) {
                phase = .arrived
            }
            try? await Task.sleep(for: .milliseconds(300))
            withAnimation(.spring(duration: 0.6, bounce: 0.3)) {
                phase = .notified
            }
            try? await Task.sleep(for: .milliseconds(3_000))
            withAnimation(.easeOut(duration: 0.4)) {
                phase = .walking
            }
            try? await Task.sleep(for: .milliseconds(500))
        }
    }
}
