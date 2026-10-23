import SwiftUI

struct ChromeZeroNumeralDemo: View {
    var body: some View {
        ChromeZeroNumeral(autoPlay: true)
    }
}

struct ChromeZeroNumeral: View {
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ChromeZeroNumeralSurface(autoPlay: autoPlay, reduced: reduceMotion, active: scenePhase == .active)
            .ignoresSafeArea()
            .statusBarHidden()
            .persistentSystemOverlays(.hidden)
    }
}

private struct ChromeZeroNumeralSurface: UIViewRepresentable {
    let autoPlay: Bool
    let reduced: Bool
    let active: Bool

    func makeUIView(context: Context) -> ChromeZeroNumeralView {
        ChromeZeroNumeralView()
    }
    func updateUIView(_ view: ChromeZeroNumeralView, context: Context) {
        view.motion.autoPlay = autoPlay
        view.motion.reduced = reduced
        view.active = active
    }
    static func dismantleUIView(_ view: ChromeZeroNumeralView, coordinator: ()) {
        view.stop()
    }
}

#Preview {
    ChromeZeroNumeralDemo()
}
