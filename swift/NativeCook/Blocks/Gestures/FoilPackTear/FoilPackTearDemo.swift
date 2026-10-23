import SwiftUI

struct FoilPackTearDemo: View {
    var body: some View {
        FoilPackTear(autoPlay: true)
    }
}

/// A self-contained, touch-driven foil pack. The gallery enables the optional demo.
struct FoilPackTear: View {
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        FoilPackTearSurface(autoPlay: autoPlay, reduced: reduceMotion, active: scenePhase == .active)
            .ignoresSafeArea()
            .statusBarHidden()
            .background(Color(red: 1 / 255, green: 2 / 255, blue: 7 / 255))
    }
}

private struct FoilPackTearSurface: UIViewRepresentable {
    var autoPlay: Bool
    var reduced: Bool
    var active: Bool

    func makeUIView(context: Context) -> FoilPackTearView {
        FoilPackTearView(autoPlay: autoPlay, reduced: reduced)
    }

    func updateUIView(_ view: FoilPackTearView, context: Context) {
        view.autoPlay = autoPlay
        view.engine.sim.reduced = reduced
        view.setActive(active)
    }

    static func dismantleUIView(_ view: FoilPackTearView, coordinator: ()) {
        view.setActive(false)
        view.metalView.delegate = nil
    }
}

#Preview {
    FoilPackTearDemo()
}
