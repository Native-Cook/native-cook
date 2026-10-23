import SwiftUI
import UIKit

struct PullLeverRefreshTouchView: UIViewRepresentable {
    let onBegin: (CGPoint) -> Void
    let onMove: (CGPoint) -> Void
    let onEnd: (CGPoint) -> Void

    func makeCoordinator() -> PullLeverRefreshTouchCoordinator {
        PullLeverRefreshTouchCoordinator(onBegin: onBegin, onMove: onMove, onEnd: onEnd)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.backgroundColor = .clear
        view.isOpaque = false
        view.layer.isOpaque = false
        view.isAccessibilityElement = false
        let pan = UIPanGestureRecognizer(target: context.coordinator, action: #selector(PullLeverRefreshTouchCoordinator.pan(_:)))
        pan.cancelsTouchesInView = false
        pan.maximumNumberOfTouches = 1
        view.addGestureRecognizer(pan)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.onBegin = onBegin
        context.coordinator.onMove = onMove
        context.coordinator.onEnd = onEnd
    }
}

final class PullLeverRefreshTouchCoordinator: NSObject {
    var onBegin: (CGPoint) -> Void
    var onMove: (CGPoint) -> Void
    var onEnd: (CGPoint) -> Void

    init(onBegin: @escaping (CGPoint) -> Void, onMove: @escaping (CGPoint) -> Void, onEnd: @escaping (CGPoint) -> Void) {
        self.onBegin = onBegin
        self.onMove = onMove
        self.onEnd = onEnd
    }

    @objc func pan(_ gesture: UIPanGestureRecognizer) {
        guard let view = gesture.view else { return }
        let point = gesture.location(in: view)
        switch gesture.state {
        case .began: onBegin(point)
        case .changed: onMove(point)
        case .ended, .cancelled, .failed: onEnd(gesture.velocity(in: view))
        default: break
        }
    }
}
