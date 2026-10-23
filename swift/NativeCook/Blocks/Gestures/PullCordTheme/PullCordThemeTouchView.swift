import SwiftUI
import UIKit

/// A zero-delay UIKit recognizer survives the gallery's parent pan recognizer.
struct PullCordThemeTouchView: UIViewRepresentable {
    let controller: PullCordThemeController

    func makeUIView(context: Context) -> Surface {
        Surface(controller: controller)
    }

    func updateUIView(_ view: Surface, context: Context) {
        let isLight = controller.snapshot.renderValues.isLight
        if let geometry = controller.geometry {
            let scale = geometry.scale
            view.updateAccessibility(
                controller: controller,
                isLight: isLight,
                frame: CGRect(
                    x: geometry.anchorX - PullCordThemeTokens.Cord.hit * scale / 2,
                    y: geometry.restY - 8 * scale,
                    width: PullCordThemeTokens.Cord.hit * scale,
                    height: (PullCordThemeTokens.Cord.beadHeight + 16) * scale
                )
            )
        }
    }

    @MainActor
    final class Surface: UIView, UIGestureRecognizerDelegate {
        weak var controller: PullCordThemeController?

        private var previousPoint = CGPoint.zero
        private var previousTime = 0.0
        private var velocity = CGVector.zero
        private var cancelledForExtraTouch = false
        private var toggleElement: ToggleElement?

        init(controller: PullCordThemeController) {
            self.controller = controller
            super.init(frame: .zero)

            backgroundColor = .clear
            isOpaque = false
            isAccessibilityElement = false

            let toggleElement = ToggleElement(container: self, controller: controller)
            self.toggleElement = toggleElement
            accessibilityElements = [toggleElement]

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

        func updateAccessibility(
            controller: PullCordThemeController,
            isLight: Bool,
            frame: CGRect
        ) {
            self.controller = controller
            toggleElement?.controller = controller
            toggleElement?.accessibilityFrameInContainerSpace = frame
            toggleElement?.accessibilityValue = isLight
                ? PullCordThemeTokens.Copy.light
                : PullCordThemeTokens.Copy.dark
            toggleElement?.accessibilityTraits = isLight
                ? [.button, .toggleButton, .selected]
                : [.button, .toggleButton]
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
                    controller?.touchEnded(velocity: velocity, cancelled: true)
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
                controller?.touchBegan(at: point)

            case .changed:
                let elapsed = max(now - previousTime, 1.0 / 240.0)
                velocity = CGVector(
                    dx: (point.x - previousPoint.x) / elapsed,
                    dy: (point.y - previousPoint.y) / elapsed
                )
                previousPoint = point
                previousTime = now
                controller?.touchMoved(to: point, velocity: velocity)

            case .ended:
                updateFinalVelocity(point: point, time: now)
                controller?.touchEnded(velocity: velocity, cancelled: false)

            case .cancelled, .failed:
                updateFinalVelocity(point: point, time: now)
                controller?.touchEnded(velocity: velocity, cancelled: true)

            default:
                break
            }
        }

        private func updateFinalVelocity(point: CGPoint, time: CFTimeInterval) {
            let elapsed = time - previousTime
            guard elapsed > 1.0 / 240.0, point != previousPoint else { return }
            velocity = CGVector(
                dx: (point.x - previousPoint.x) / elapsed,
                dy: (point.y - previousPoint.y) / elapsed
            )
        }

        @MainActor
        private final class ToggleElement: UIAccessibilityElement {
            weak var controller: PullCordThemeController?

            init(container: Any, controller: PullCordThemeController) {
                self.controller = controller
                super.init(accessibilityContainer: container)
                isAccessibilityElement = true
                accessibilityTraits = [.button, .toggleButton]
                accessibilityLabel = PullCordThemeTokens.Copy.a11yLabel
                accessibilityHint = PullCordThemeTokens.Copy.a11yHint
            }

            override func accessibilityActivate() -> Bool {
                controller?.toggle()
                return true
            }
        }
    }
}
