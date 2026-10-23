import SwiftUI
import UIKit

/// A zero-delay recognizer remains simultaneous with the gallery's edge-back gesture.
struct WeekendMoodCarouselTouchView: UIViewRepresentable {
    let panelHeight: CGFloat
    let chipsRect: CGRect
    let backRect: CGRect
    let saveRect: CGRect
    let onBegan: (CGPoint, Bool) -> Void
    let onChanged: (CGFloat, CGPoint) -> Void
    let onEnded: (CGFloat, CGFloat, CGPoint, Bool, Bool) -> Void

    func makeUIView(context: Context) -> WeekendMoodCarouselTouchSurface {
        let view = WeekendMoodCarouselTouchSurface()
        view.backgroundColor = .clear
        view.isOpaque = false
        updateUIView(view, context: context)
        return view
    }

    func updateUIView(_ view: WeekendMoodCarouselTouchSurface, context: Context) {
        view.panelHeight = panelHeight
        view.chipsRect = chipsRect
        view.backRect = backRect
        view.saveRect = saveRect
        view.onBegan = onBegan
        view.onChanged = onChanged
        view.onEnded = onEnded
    }
}

final class WeekendMoodCarouselTouchSurface: UIView, UIGestureRecognizerDelegate {
    var panelHeight: CGFloat = 0
    var chipsRect = CGRect.zero
    var backRect = CGRect.zero
    var saveRect = CGRect.zero
    var onBegan: ((CGPoint, Bool) -> Void)?
    var onChanged: ((CGFloat, CGPoint) -> Void)?
    var onEnded: ((CGFloat, CGFloat, CGPoint, Bool, Bool) -> Void)?

    private var startPoint = CGPoint.zero
    private var previousX: CGFloat = 0
    private var previousTime = 0.0
    private var velocityX = 0.0
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
        let panel = CGRect(x: 0, y: 0, width: bounds.width, height: panelHeight)
        if backRect.contains(point) || saveRect.contains(point) { return false }
        return panel.contains(point) || chipsRect.contains(point)
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
            onBegan?(point, chipsRect.contains(point))
        case .changed:
            maximumTravel = max(maximumTravel, hypot(point.x - startPoint.x, point.y - startPoint.y))
            let elapsed = max(0.001, time - previousTime)
            velocityX = Double(point.x - previousX) / elapsed
            previousX = point.x
            previousTime = time
            onChanged?(point.x - startPoint.x, point)
        case .ended:
            maximumTravel = max(maximumTravel, hypot(point.x - startPoint.x, point.y - startPoint.y))
            onEnded?(point.x - startPoint.x, CGFloat(velocityX), point, true, maximumTravel <= 10)
        case .cancelled, .failed:
            onEnded?(point.x - startPoint.x, CGFloat(velocityX), point, false, false)
        default:
            break
        }
    }
}
