import QuartzCore
import SwiftUI
import UIKit

/// Full-screen input surface for the glass key. Keep it above the rendered lock and below the vault sheet so the
/// sheet's real buttons remain tappable. Its accessibility element is virtual, so no invisible button intercepts
/// the same key region used for dragging.
struct GlassLockKeyTouch: UIViewRepresentable {
    let isOpen: Bool
    let accessibilityFrame: CGRect
    let onInput: (GlassLockKeyInput) -> Void
    let onCommand: (GlassLockKeyCommand) -> Void

    func makeUIView(context: Context) -> Surface {
        Surface(onInput: onInput, onCommand: onCommand)
    }

    func updateUIView(_ view: Surface, context: Context) {
        view.onInput = onInput
        view.onCommand = onCommand
        view.updateAccessibility(isOpen: isOpen, frame: accessibilityFrame)
    }

    @MainActor
    final class Surface: UIView, UIGestureRecognizerDelegate {
        var onInput: (GlassLockKeyInput) -> Void
        var onCommand: (GlassLockKeyCommand) -> Void

        private var previousPoint = CGPoint.zero
        private var previousTime = 0.0
        private var velocity = CGVector.zero
        private var beganAt = CGPoint.zero
        private var beganTime = 0.0
        private var maximumTravel = 0.0
        private var keyActivationFrame = CGRect.zero
        private var cancelledForExtraTouch = false
        private var keyElement: KeyElement!

        init(
            onInput: @escaping (GlassLockKeyInput) -> Void,
            onCommand: @escaping (GlassLockKeyCommand) -> Void
        ) {
            self.onInput = onInput
            self.onCommand = onCommand
            super.init(frame: .zero)

            backgroundColor = .clear
            isOpaque = false
            isAccessibilityElement = false

            keyElement = KeyElement(accessibilityContainer: self)
            keyElement.accessibilityTraits = .button
            keyElement.activate = { [weak self] in
                guard let self else { return false }
                self.onCommand(self.keyElement.isOpen ? .lock : .unlock)
                return true
            }
            accessibilityElements = [keyElement!]

            let touch = UILongPressGestureRecognizer(target: self, action: #selector(handleTouch(_:)))
            touch.minimumPressDuration = 0
            touch.allowableMovement = .greatestFiniteMagnitude
            touch.numberOfTouchesRequired = 1
            touch.cancelsTouchesInView = false
            touch.delaysTouchesBegan = false
            touch.delegate = self
            addGestureRecognizer(touch)
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("init(coder:) is unavailable")
        }

        func updateAccessibility(isOpen: Bool, frame: CGRect) {
            keyActivationFrame = frame
            keyElement.isOpen = isOpen
            keyElement.accessibilityFrameInContainerSpace = frame
            keyElement.accessibilityLabel = isOpen
                ? GlassLockKeyConstants.Copy.lock
                : GlassLockKeyConstants.Copy.unlock
            keyElement.accessibilityHint = GlassLockKeyConstants.Copy.keyHint
            keyElement.accessibilityValue = isOpen
                ? GlassLockKeyConstants.Copy.unlocked
                : GlassLockKeyConstants.Copy.locked
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc private func handleTouch(_ recognizer: UILongPressGestureRecognizer) {
            if recognizer.numberOfTouches > 1 {
                if !cancelledForExtraTouch {
                    cancelledForExtraTouch = true
                    onInput(.up(velocity: .zero))
                }
                return
            }
            if cancelledForExtraTouch {
                if recognizer.state == .ended
                    || recognizer.state == .cancelled
                    || recognizer.state == .failed {
                    cancelledForExtraTouch = false
                }
                return
            }

            let point = recognizer.location(in: self)
            let now = CACurrentMediaTime()

            switch recognizer.state {
            case .began:
                previousPoint = point
                previousTime = now
                velocity = .zero
                beganAt = point
                beganTime = now
                maximumTravel = 0
                onInput(.down(point))

            case .changed:
                maximumTravel = max(maximumTravel, hypot(point.x - beganAt.x, point.y - beganAt.y))
                updateVelocity(point: point, time: now)
                onInput(.move(point))

            case .ended:
                maximumTravel = max(maximumTravel, hypot(point.x - beganAt.x, point.y - beganAt.y))
                updateFinalVelocity(point: point, time: now)
                onInput(.up(velocity: velocity))
                if now - beganTime <= 0.35,
                   maximumTravel <= 8,
                   keyActivationFrame.insetBy(dx: -8, dy: -8).contains(beganAt) {
                    onCommand(keyElement.isOpen ? .lock : .unlock)
                }

            case .cancelled, .failed:
                // Cancellation is still a real lift. Sending the last measured velocity preserves RN's
                // distance-or-velocity commit rule while ensuring the two-second resume timer can begin.
                if now - previousTime > 0.1 { velocity = .zero }
                onInput(.up(velocity: velocity))

            default:
                break
            }
        }

        private func updateVelocity(point: CGPoint, time: CFTimeInterval) {
            let elapsed = max(time - previousTime, 1.0 / 240.0)
            let measured = CGVector(
                dx: (point.x - previousPoint.x) / elapsed,
                dy: (point.y - previousPoint.y) / elapsed
            )
            let weight = 1 - exp(-elapsed / 0.035)
            velocity.dx += (measured.dx - velocity.dx) * weight
            velocity.dy += (measured.dy - velocity.dy) * weight
            previousPoint = point
            previousTime = time
        }

        private func updateFinalVelocity(point: CGPoint, time: CFTimeInterval) {
            if time - previousTime > 0.1 {
                velocity = .zero
            } else if point != previousPoint {
                updateVelocity(point: point, time: time)
            }
        }
    }

    private final class KeyElement: UIAccessibilityElement {
        var isOpen = false
        var activate: (() -> Bool)?

        override func accessibilityActivate() -> Bool {
            activate?() ?? false
        }
    }
}
