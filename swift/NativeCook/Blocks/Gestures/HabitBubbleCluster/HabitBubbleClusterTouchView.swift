import SwiftUI
import UIKit

struct HabitBubbleClusterTouchView: UIViewRepresentable {
    let model: HabitBubbleClusterModel

    func makeUIView(context: Context) -> HabitBubbleClusterTouchSurface {
        HabitBubbleClusterTouchSurface(model: model)
    }

    func updateUIView(_ view: HabitBubbleClusterTouchSurface, context: Context) {}
}

@MainActor
final class HabitBubbleClusterTouchSurface: UIView, UIGestureRecognizerDelegate {
    private let model: HabitBubbleClusterModel

    init(model: HabitBubbleClusterModel) {
        self.model = model
        super.init(frame: .zero)
        backgroundColor = .clear
        isAccessibilityElement = false
        let press = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        press.minimumPressDuration = 0
        press.allowableMovement = .greatestFiniteMagnitude
        press.cancelsTouchesInView = false
        press.delegate = self
        addGestureRecognizer(press)
    }

    required init?(coder: NSCoder) { nil }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let now = ProcessInfo.processInfo.systemUptime
        switch recognizer.state {
        case .began: model.pointerBegan(at: point, timestamp: now)
        case .changed: model.pointerMoved(to: point, timestamp: now)
        case .ended, .cancelled, .failed: model.pointerEnded(at: point, timestamp: now, cancelled: recognizer.state != .ended)
        default: break
        }
    }
}
