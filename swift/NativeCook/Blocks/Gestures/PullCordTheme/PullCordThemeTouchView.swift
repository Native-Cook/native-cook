import SwiftUI
import UIKit

struct PullCordThemeTouchView: UIViewRepresentable {
    let controller: PullCordThemeController

    func makeUIView(context: Context) -> PullCordThemeTouchSurface {
        PullCordThemeTouchSurface(controller: controller)
    }

    func updateUIView(_ view: PullCordThemeTouchSurface, context: Context) {
        view.controller = controller
        view.accessibilityValue = controller.snapshot.light == 1
            ? PullCordThemeTokens.Copy.day
            : PullCordThemeTokens.Copy.night
    }
}

final class PullCordThemeTouchSurface: UIView, UIGestureRecognizerDelegate {
    weak var controller: PullCordThemeController?

    private var lastPoint = CGPoint.zero
    private var lastTime = CACurrentMediaTime()
    private var velocity = CGVector.zero

    init(controller: PullCordThemeController) {
        self.controller = controller
        super.init(frame: .zero)
        backgroundColor = .clear
        isOpaque = false
        isAccessibilityElement = true
        accessibilityTraits = .button
        accessibilityLabel = PullCordThemeTokens.Copy.accessibilityLabel
        accessibilityHint = PullCordThemeTokens.Copy.accessibilityHint

        let press = UILongPressGestureRecognizer(target: self, action: #selector(press(_:)))
        press.minimumPressDuration = 0
        press.allowableMovement = .greatestFiniteMagnitude
        press.cancelsTouchesInView = true
        press.delegate = self
        addGestureRecognizer(press)

        let tap = UITapGestureRecognizer(target: self, action: #selector(tap(_:)))
        tap.numberOfTapsRequired = 1
        tap.delegate = self
        addGestureRecognizer(tap)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func accessibilityActivate() -> Bool {
        controller?.toggle()
        return true
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func press(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let now = CACurrentMediaTime()
        switch recognizer.state {
        case .began:
            lastPoint = point
            lastTime = now
            velocity = .zero
            controller?.pointerDown(at: point)
        case .changed:
            let delta = max(now - lastTime, 1 / 240)
            velocity = CGVector(
                dx: (point.x - lastPoint.x) / delta,
                dy: (point.y - lastPoint.y) / delta
            )
            lastPoint = point
            lastTime = now
            controller?.pointerMoved(to: point, velocity: velocity)
        case .ended:
            controller?.pointerEnded(velocity: velocity, cancelled: false)
        case .cancelled, .failed:
            controller?.pointerEnded(velocity: velocity, cancelled: true)
        default:
            break
        }
    }

    @objc private func tap(_ recognizer: UITapGestureRecognizer) {
        guard recognizer.state == .ended else { return }
        controller?.tap(at: recognizer.location(in: self))
    }
}
