import SwiftUI
import UIKit

/// Sits behind the card and reports every finger on the screen to the controller.
///
/// React Native pauses the loop for a touch anywhere (the scroll view's `onTouchStart`) and runs the
/// card's pan only for a touch that starts on the card. One zero-delay press recogniser on the
/// window sees both without taking the touch from the header's buttons, and, because it is not a
/// `DragGesture`, it keeps its finger when the gallery's back swipe begins (see `EmberTouchView`).
struct PaperDeckTutorialTouchLayer: UIViewRepresentable {
    let controller: PaperDeckTutorialController

    func makeUIView(context: Context) -> PaperDeckTutorialTouchSurface {
        let view = PaperDeckTutorialTouchSurface()
        view.controller = controller
        return view
    }

    func updateUIView(_ view: PaperDeckTutorialTouchSurface, context: Context) {
        view.controller = controller
    }
}

final class PaperDeckTutorialTouchSurface: UIView, UIGestureRecognizerDelegate {
    weak var controller: PaperDeckTutorialController?

    /// Presses within the last 100 ms, for the release velocity.
    private var samples: [(time: Double, x: CGFloat)] = []
    private var start = CGPoint.zero
    private var tracking = false

    private lazy var press: UILongPressGestureRecognizer = {
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(track(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delaysTouchesBegan = false
        recognizer.delaysTouchesEnded = false
        recognizer.delegate = self
        return recognizer
    }()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        isUserInteractionEnabled = false
        isAccessibilityElement = false
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("PaperDeckTutorialTouchSurface is created in code")
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        press.view?.removeGestureRecognizer(press)
        if let window {
            window.addGestureRecognizer(press)
        } else {
            finish(velocity: 0, cancelled: true)
        }
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func track(_ recognizer: UILongPressGestureRecognizer) {
        let now = CACurrentMediaTime()
        let point = recognizer.location(in: nil)
        switch recognizer.state {
        case .began:
            tracking = true
            start = point
            samples = [(time: now, x: point.x)]
            let local = convert(point, from: nil)
            controller?.touchBegan(cardPoint: bounds.contains(local) ? local : nil)
        case .changed:
            guard tracking else { return }
            samples.append((time: now, x: point.x))
            samples.removeAll { now - $0.time > 0.1 }
            let translation = CGSize(width: point.x - start.x, height: point.y - start.y)
            controller?.touchMoved(translation: translation)
        case .ended:
            finish(velocity: velocity(at: now), cancelled: false)
        case .cancelled, .failed:
            finish(velocity: 0, cancelled: true)
        default:
            break
        }
    }

    /// Points per second along x over the last 100 ms; a finger that rested for 100 ms has none.
    private func velocity(at now: Double) -> Double {
        guard let first = samples.first, let last = samples.last, last.time > first.time else { return 0 }
        if now - last.time > 0.1 { return 0 }
        return Double(last.x - first.x) / (last.time - first.time)
    }

    private func finish(velocity: Double, cancelled: Bool) {
        guard tracking else { return }
        tracking = false
        samples.removeAll()
        controller?.touchEnded(velocityX: velocity, cancelled: cancelled)
    }
}
