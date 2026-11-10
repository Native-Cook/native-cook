import SwiftUI
import UIKit

/// One block on the whole screen, with nothing of the gallery over it, so a recording shows only
/// the block. Swipe in from the left edge to go back.
struct DemoScreen: View {
    let block: Block
    let onBack: () -> Void

    var body: some View {
        block.demo()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(GalleryColors.background.ignoresSafeArea())
            // The back swipe is a UIKit pan on the window that never cancels touches, not a SwiftUI
            // DragGesture over the block: once a SwiftUI drag began (24 pt into any drag), UIKit
            // cancelled the touches of every block that reads them itself (touchesBegan/Moved), so
            // their drags stopped dead. This pan only begins for a drag from the left edge.
            .background(DemoBackSwipe(onBack: onBack))
            .accessibilityAction(.escape, onBack)
    }
}

private struct DemoBackSwipe: UIViewRepresentable {
    let onBack: () -> Void

    func makeUIView(context: Context) -> Host { Host(onBack: onBack) }
    func updateUIView(_ view: Host, context: Context) { view.onBack = onBack }
    static func dismantleUIView(_ view: Host, coordinator: ()) { view.detach() }

    final class Host: UIView, UIGestureRecognizerDelegate {
        var onBack: () -> Void
        private lazy var edge: UIPanGestureRecognizer = {
            let g = UIPanGestureRecognizer(target: self, action: #selector(pan(_:)))
            g.cancelsTouchesInView = false
            g.delaysTouchesBegan = false
            g.delaysTouchesEnded = false
            g.delegate = self
            return g
        }()

        init(onBack: @escaping () -> Void) {
            self.onBack = onBack
            super.init(frame: .zero)
            isUserInteractionEnabled = false
        }
        required init?(coder: NSCoder) { fatalError() }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            edge.view?.removeGestureRecognizer(edge)
            window?.addGestureRecognizer(edge)
        }
        func detach() { edge.view?.removeGestureRecognizer(edge) }

        @objc private func pan(_ g: UIPanGestureRecognizer) {
            guard g.state == .ended, let view = g.view else { return }
            let t = g.translation(in: view)
            if t.x > 80, abs(t.y) < t.x / 2 { onBack() }
        }
        override func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
            guard g === edge, let view = g.view else { return super.gestureRecognizerShouldBegin(g) }
            let start = edge.location(in: view).x - edge.translation(in: view).x
            return start < 24 && edge.translation(in: view).x > 0
        }
        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
    }
}
