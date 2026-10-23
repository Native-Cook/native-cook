import SwiftUI

struct ChromePrizeWheelSurface: UIViewRepresentable {
    var autoPlay: Bool
    var reduced: Bool

    func makeUIView(context: Context) -> ChromePrizeWheelStage {
        ChromePrizeWheelStage(autoPlay: autoPlay, reduced: reduced)
    }

    func updateUIView(_ view: ChromePrizeWheelStage, context: Context) {
        view.config.autoPlay = autoPlay
        if view.config.reduced != reduced {
            view.scene = ChromePrizeWheelMotion.initialScene()
            view.config.reduced = reduced
        }
    }

    static func dismantleUIView(_ view: ChromePrizeWheelStage, coordinator: ()) {
        view.stop()
    }
}
