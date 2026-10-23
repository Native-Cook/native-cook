import SwiftUI
import UIKit

struct MemoCardFanTouchView: UIViewRepresentable {
    let dragEnabled: Bool
    let accessibilityValue: String
    let accessibilityHint: String
    let onPressChanged: (Bool) -> Void
    let onDragBegan: () -> Void
    let onDragChanged: (CGPoint) -> Void
    let onDragEnded: (CGPoint, CGPoint) -> Void
    let onTap: () -> Void
    let onContactBegan: (Int) -> Void
    let onContactEnded: (Int, Bool) -> Void
    let onAccessibilityEnded: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(owner: self)
    }

    func makeUIView(context: Context) -> MemoCardFanTouchButton {
        let button = MemoCardFanTouchButton(type: .custom)
        button.backgroundColor = .clear
        button.isAccessibilityElement = true
        button.accessibilityLabel = "Daily Memo, Notes & Journaling, 09 documents, 11th Notes"
        button.accessibilityTraits = .button
        button.addTarget(context.coordinator, action: #selector(Coordinator.didTap), for: .touchUpInside)
        button.onAccessibilityActivate = context.coordinator.didAccessibilityTap
        button.isMultipleTouchEnabled = true
        button.onRawBegan = { [weak coordinator = context.coordinator] count in
            coordinator?.owner.onContactBegan(count)
        }
        button.onRawEnded = { [weak coordinator = context.coordinator] count, cancelled in
            coordinator?.owner.onContactEnded(count, cancelled)
        }

        let gesture = UILongPressGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.didPress(_:))
        )
        gesture.minimumPressDuration = 0
        gesture.allowableMovement = .greatestFiniteMagnitude
        gesture.cancelsTouchesInView = false
        gesture.delegate = context.coordinator
        button.addGestureRecognizer(gesture)
        return button
    }

    func updateUIView(_ button: MemoCardFanTouchButton, context: Context) {
        context.coordinator.owner = self
        button.onAccessibilityActivate = context.coordinator.didAccessibilityTap
        button.onRawBegan = { [weak coordinator = context.coordinator] count in
            coordinator?.owner.onContactBegan(count)
        }
        button.onRawEnded = { [weak coordinator = context.coordinator] count, cancelled in
            coordinator?.owner.onContactEnded(count, cancelled)
        }
        button.accessibilityValue = accessibilityValue
        button.accessibilityHint = accessibilityHint
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var owner: MemoCardFanTouchView
        private var start = CGPoint.zero
        private var previous = CGPoint.zero
        private var previousTimestamp = 0.0
        private var trackedVelocity = CGPoint.zero
        private var verticalDrag = false
        private var horizontalHandoff = false

        init(owner: MemoCardFanTouchView) {
            self.owner = owner
        }

        @objc func didTap() {
            guard !verticalDrag, !horizontalHandoff else { return }
            owner.onTap()
        }

        func didAccessibilityTap() {
            owner.onPressChanged(true)
            owner.onTap()
            owner.onPressChanged(false)
            owner.onAccessibilityEnded()
        }

        @objc func didPress(_ recognizer: UILongPressGestureRecognizer) {
            let point = recognizer.location(in: recognizer.view)
            switch recognizer.state {
            case .began:
                start = point
                previous = point
                previousTimestamp = ProcessInfo.processInfo.systemUptime
                trackedVelocity = .zero
                verticalDrag = false
                horizontalHandoff = false
                owner.onPressChanged(true)

            case .changed:
                let translation = CGPoint(x: point.x - start.x, y: point.y - start.y)
                let timestamp = ProcessInfo.processInfo.systemUptime
                let elapsed = timestamp - previousTimestamp
                if elapsed > 0 {
                    trackedVelocity = CGPoint(
                        x: (point.x - previous.x) / elapsed,
                        y: (point.y - previous.y) / elapsed
                    )
                    previous = point
                    previousTimestamp = timestamp
                }
                if !verticalDrag, abs(translation.x) > 18 {
                    horizontalHandoff = true
                    owner.onPressChanged(false)
                }
                if owner.dragEnabled,
                   !horizontalHandoff,
                   !verticalDrag,
                   abs(translation.y) > 10 {
                    verticalDrag = true
                    owner.onDragBegan()
                }
                if verticalDrag {
                    owner.onDragChanged(translation)
                }

            case .ended:
                let translation = CGPoint(x: point.x - start.x, y: point.y - start.y)
                if verticalDrag {
                    owner.onDragEnded(translation, trackedVelocity)
                }
                owner.onPressChanged(false)

            case .cancelled, .failed:
                let translation = CGPoint(x: point.x - start.x, y: point.y - start.y)
                if verticalDrag {
                    owner.onDragEnded(translation, .zero)
                }
                owner.onPressChanged(false)

            default:
                break
            }
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }
    }
}

final class MemoCardFanTouchButton: UIButton {
    var onAccessibilityActivate: (() -> Void)?
    var onRawBegan: ((Int) -> Void)?
    var onRawEnded: ((Int, Bool) -> Void)?

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        onRawBegan?(touches.count)
        super.touchesBegan(touches, with: event)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesEnded(touches, with: event)
        onRawEnded?(touches.count, false)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesCancelled(touches, with: event)
        onRawEnded?(touches.count, true)
    }

    override func accessibilityActivate() -> Bool {
        onAccessibilityActivate?()
        return true
    }
}
