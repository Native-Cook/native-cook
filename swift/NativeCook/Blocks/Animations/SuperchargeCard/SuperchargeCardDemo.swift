import SwiftUI

struct SuperchargeCardDemo: View {
    var body: some View {
        SuperchargeCard(autoPlay: true)
    }
}

/// A self-contained native renderer. The demo opts into the twelve-second script.
struct SuperchargeCard: View {
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        SuperchargeCardSurface(autoPlay: autoPlay, reduced: reduceMotion, active: scenePhase == .active)
            .ignoresSafeArea()
            .statusBarHidden()
            .persistentSystemOverlays(.hidden)
    }
}

private struct SuperchargeCardSurface: UIViewRepresentable {
    let autoPlay: Bool
    let reduced: Bool
    let active: Bool

    func makeUIView(context: Context) -> SuperchargeCardMetalView {
        SuperchargeCardMetalView(autoPlay: autoPlay, reduced: reduced)
    }

    func updateUIView(_ view: SuperchargeCardMetalView, context: Context) {
        view.renderer.autoPlay = autoPlay
        view.renderer.reduced = reduced
        view.isPaused = !active
        if !active {
            view.renderer.input("cancel")
            view.renderer.resetFrameTime()
        }
    }

    static func dismantleUIView(_ view: SuperchargeCardMetalView, coordinator: ()) {
        view.isPaused = true
        view.delegate = nil
    }
}

#Preview {
    SuperchargeCardDemo()
}
