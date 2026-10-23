import SwiftUI

/// Puts the list on the screen and hands it the engine, with the screen's measurements.
struct DragMultiSelectList: UIViewControllerRepresentable {
    let engine: DragMultiSelectEngine
    let size: CGSize
    let topInset: Double
    let bottomInset: Double
    let autoPlay: Bool
    let reduceMotion: Bool

    func makeUIViewController(context: Context) -> DragMultiSelectListController {
        configure()
        return DragMultiSelectListController(engine: engine)
    }

    func updateUIViewController(_ controller: DragMultiSelectListController, context: Context) {
        configure()
        controller.refreshContentSize()
    }

    static func dismantleUIViewController(_ controller: DragMultiSelectListController, coordinator: ()) {
        controller.stop()
    }

    private func configure() {
        engine.configure(
            width: size.width,
            height: size.height,
            topInset: topInset,
            bottomInset: bottomInset,
            autoPlay: autoPlay,
            reduceMotion: reduceMotion
        )
    }
}
