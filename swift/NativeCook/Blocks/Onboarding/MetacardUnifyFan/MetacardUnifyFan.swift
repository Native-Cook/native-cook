import SwiftUI

/// Pass `autoPlay: false` for the touch-driven onboarding component.
struct MetacardUnifyFan: UIViewRepresentable {
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeUIView(context: Context) -> MetacardUnifyFanView {
        let view = MetacardUnifyFanView()
        view.motion.autoPlay = autoPlay
        view.motion.reduced = reduceMotion
        return view
    }

    func updateUIView(_ view: MetacardUnifyFanView, context: Context) {
        view.motion.autoPlay = autoPlay
        view.motion.reduced = reduceMotion
    }
}
