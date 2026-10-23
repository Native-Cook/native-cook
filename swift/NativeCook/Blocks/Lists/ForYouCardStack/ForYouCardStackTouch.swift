import UIKit
import SwiftUI

struct ForYouCardStackTouchView: UIViewRepresentable {
    let rowRect: CGRect
    let seeingRect: CGRect
    let onBegan: (CGPoint) -> Void
    let onChanged: (CGFloat, CGPoint) -> Void
    let onEnded: (CGFloat, CGFloat, CGPoint, Bool) -> Void

    func makeUIView(context: Context) -> ForYouCardStackTouchSurface {
        let view = ForYouCardStackTouchSurface()
        view.backgroundColor = .clear
        view.isOpaque = false
        updateUIView(view, context: context)
        return view
    }

    func updateUIView(_ view: ForYouCardStackTouchSurface, context: Context) {
        view.rowRect = rowRect
        view.seeingRect = seeingRect
        view.onBegan = onBegan
        view.onChanged = onChanged
        view.onEnded = onEnded
    }
}

final class ForYouCardStackTouchSurface: UIView, UIGestureRecognizerDelegate {
    var rowRect = CGRect.zero
    var seeingRect = CGRect.zero
    var onBegan: ((CGPoint) -> Void)?
    var onChanged: ((CGFloat, CGPoint) -> Void)?
    var onEnded: ((CGFloat, CGFloat, CGPoint, Bool) -> Void)?

    private var startPoint = CGPoint.zero
    private var previousX: CGFloat = 0
    private var previousTime = 0.0
    private var velocityX: CGFloat = 0
    private var maximumTravel: CGFloat = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.numberOfTouchesRequired = 1
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
        isAccessibilityElement = false
        accessibilityElementsHidden = true
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        guard rowRect.contains(point) else { return false }
        // Let the visible book button retain its normal SwiftUI hit target.
        return !seeingRect.contains(point)
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let time = ProcessInfo.processInfo.systemUptime
        switch recognizer.state {
        case .began:
            startPoint = point
            previousX = point.x
            previousTime = time
            velocityX = 0
            maximumTravel = 0
            onBegan?(point)
        case .changed:
            maximumTravel = max(maximumTravel, hypot(point.x - startPoint.x, point.y - startPoint.y))
            let elapsed = max(0.001, time - previousTime)
            velocityX = (point.x - previousX) / CGFloat(elapsed)
            previousX = point.x
            previousTime = time
            onChanged?(point.x - startPoint.x, point)
        case .ended:
            maximumTravel = max(maximumTravel, hypot(point.x - startPoint.x, point.y - startPoint.y))
            onEnded?(point.x - startPoint.x, velocityX, point, true)
        case .cancelled, .failed:
            onEnded?(point.x - startPoint.x, velocityX, point, false)
        default:
            break
        }
    }
}
