import SwiftUI
import UIKit

/// A zero-delay UIKit recognizer. It coexists with the gallery's edge pan, unlike a SwiftUI drag.
struct GlassDiscOnboardingTouchView: UIViewRepresentable {
    let model: GlassDiscOnboardingModel

    func makeUIView(context: Context) -> GlassDiscOnboardingTouchSurface {
        GlassDiscOnboardingTouchSurface(model: model)
    }

    func updateUIView(_ view: GlassDiscOnboardingTouchSurface, context: Context) {
        view.model = model
        view.updateAccessibility()
    }
}

@MainActor
final class GlassDiscOnboardingTouchSurface: UIView, UIGestureRecognizerDelegate {
    var model: GlassDiscOnboardingModel
    private var previous = CGPoint.zero
    private var previousTime = 0.0
    private var velocityY = 0.0

    init(model: GlassDiscOnboardingModel) {
        self.model = model
        super.init(frame: .zero)
        backgroundColor = .clear
        isAccessibilityElement = true
        accessibilityTraits = .button
        updateAccessibility()

        let press = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        press.minimumPressDuration = 0
        press.allowableMovement = .greatestFiniteMagnitude
        press.cancelsTouchesInView = false
        press.delaysTouchesBegan = false
        press.delegate = self
        addGestureRecognizer(press)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { nil }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool { true }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        // The intro's dome occupies this same lower area. Buttons are only real targets after the transition.
        guard model.stage == .community else { return true }
        let point = touch.location(in: self)
        let darkY = bounds.height - GlassDiscOnboardingTheme.Layout.buttonsBottom - GlassDiscOnboardingTheme.Layout.buttonHeightDark
        let lightY = darkY - GlassDiscOnboardingTheme.Layout.buttonGap - GlassDiscOnboardingTheme.Layout.buttonHeight
        let buttons = CGRect(
            x: GlassDiscOnboardingTheme.Layout.gutter,
            y: lightY,
            width: bounds.width - GlassDiscOnboardingTheme.Layout.gutter * 2,
            height: bounds.height - lightY
        )
        // CTA touches belong to SwiftUI's Button; the glass recognizer remains available everywhere else.
        return !buttons.contains(point)
    }

    override func accessibilityActivate() -> Bool {
        if model.stage == .community {
            model.startOver()
        } else {
            model.enter()
        }
        return true
    }

    func updateAccessibility() {
        if model.stage == .community {
            accessibilityLabel = "\(GlassDiscOnboardingTheme.Copy.headline) \(GlassDiscOnboardingTheme.Copy.sub.joined(separator: " "))"
            accessibilityHint = "Double tap to start over"
            accessibilityTraits = .header
        } else {
            accessibilityLabel = "\(GlassDiscOnboardingTheme.Copy.intro.joined(separator: " ")) \(GlassDiscOnboardingTheme.Copy.hint)"
            accessibilityHint = "Pull the glass dome up to enter"
            accessibilityTraits = .button
        }
    }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let now = ProcessInfo.processInfo.systemUptime
        switch recognizer.state {
        case .began:
            previous = point
            previousTime = now
            velocityY = 0
            model.touchBegin(x: point.x, y: point.y)
        case .changed:
            let elapsed = now - previousTime
            if elapsed > 0 { velocityY = (point.y - previous.y) / elapsed }
            previous = point
            previousTime = now
            model.touchMove(x: point.x, y: point.y)
        case .ended, .cancelled, .failed:
            let elapsed = now - previousTime
            // UIKit's final position frequently equals the previous changed position. Keep the last genuine velocity.
            if elapsed > 0, abs(point.y - previous.y) > GlassDiscOnboardingTheme.Touch.velocityPositionEpsilon {
                velocityY = (point.y - previous.y) / elapsed
            }
            if elapsed > GlassDiscOnboardingTheme.Touch.velocityStaleAfter { velocityY = 0 }
            model.touchEnd(velocityY: velocityY, cancelled: recognizer.state != .ended)
        default:
            break
        }
    }
}
