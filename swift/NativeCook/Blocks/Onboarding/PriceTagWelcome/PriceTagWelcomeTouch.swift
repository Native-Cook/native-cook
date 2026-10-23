import SwiftUI
import UIKit

struct PriceTagWelcomeTouch: UIViewRepresentable {
    let scale: Double
    let onDown: (Double, Double, Double) -> Void
    let onBeginDrag: (Double) -> Void
    let onMoveDrag: (Double, Double) -> Void
    let onEndDrag: (Double, Double) -> Void
    let onTap: (Double, Double, Double) -> Void
    let onCancel: (Double) -> Void

    func makeUIView(context: Context) -> PriceTagWelcomeTouchView {
        let view = PriceTagWelcomeTouchView()
        view.callbacks = callbacks
        view.scale = scale
        return view
    }

    func updateUIView(_ view: PriceTagWelcomeTouchView, context: Context) {
        view.callbacks = callbacks
        view.scale = scale
    }

    private var callbacks: PriceTagWelcomeTouchView.Callbacks {
        PriceTagWelcomeTouchView.Callbacks(
            onDown: onDown,
            onBeginDrag: onBeginDrag,
            onMoveDrag: onMoveDrag,
            onEndDrag: onEndDrag,
            onTap: onTap,
            onCancel: onCancel
        )
    }
}

final class PriceTagWelcomeTouchView: UIView, UIGestureRecognizerDelegate {
    struct Callbacks {
        let onDown: (Double, Double, Double) -> Void
        let onBeginDrag: (Double) -> Void
        let onMoveDrag: (Double, Double) -> Void
        let onEndDrag: (Double, Double) -> Void
        let onTap: (Double, Double, Double) -> Void
        let onCancel: (Double) -> Void
    }

    var callbacks: Callbacks?
    var scale = 1.0

    private var origin = CGPoint.zero
    private var previous = CGPoint.zero
    private var previousTime = 0.0
    private var velocityX = 0.0
    private var dragging = false
    private var rejected = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isAccessibilityElement = false
        accessibilityElementsHidden = true
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        true
    }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        guard let callbacks else { return }
        let point = recognizer.location(in: self)
        let now = Date().timeIntervalSinceReferenceDate
        let unit = max(scale, 0.0001)
        switch recognizer.state {
        case .began:
            origin = point
            previous = point
            previousTime = now
            velocityX = 0
            dragging = false
            rejected = false
            callbacks.onDown(
                point.x / unit,
                point.y / unit + PriceTagWelcomeTheme.rowTop,
                now
            )
        case .changed:
            let translationX = (point.x - origin.x) / unit
            let translationY = (point.y - origin.y) / unit
            if !dragging && !rejected {
                if abs(translationY) > 16 {
                    rejected = true
                    callbacks.onCancel(now)
                } else if abs(translationX) > 8 {
                    dragging = true
                    callbacks.onBeginDrag(now)
                }
            }
            let delta = max(0.001, now - previousTime)
            velocityX = Double(point.x - previous.x) / delta / unit
            previous = point
            previousTime = now
            if dragging { callbacks.onMoveDrag(translationX, now) }
        case .ended:
            if dragging {
                callbacks.onEndDrag(velocityX, now)
            } else if !rejected {
                let distance = hypot(point.x - origin.x, point.y - origin.y) / unit
                if distance <= PriceTagWelcomeMotion.tapSlop {
                    callbacks.onTap(
                        point.x / unit,
                        point.y / unit + PriceTagWelcomeTheme.rowTop,
                        now
                    )
                } else {
                    callbacks.onCancel(now)
                }
            }
        case .cancelled, .failed:
            if dragging { callbacks.onEndDrag(0, now) }
            else { callbacks.onCancel(now) }
        default:
            break
        }
    }
}
