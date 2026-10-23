import SwiftUI
import UIKit

struct GiftCardPickerTouchSurface: UIViewRepresentable {
    let enabled: Bool
    let onBegan: () -> Void
    let onChanged: (CGFloat) -> Void
    let onEnded: (CGFloat, CGFloat, CGFloat, Bool) -> Void

    func makeUIView(context: Context) -> GiftCardPickerTouchView {
        let view = GiftCardPickerTouchView()
        updateUIView(view, context: context)
        return view
    }

    func updateUIView(_ view: GiftCardPickerTouchView, context: Context) {
        view.enabled = enabled
        view.onBegan = onBegan
        view.onChanged = onChanged
        view.onEnded = onEnded
    }
}

final class GiftCardPickerTouchView: UIView, UIGestureRecognizerDelegate {
    var enabled = true
    var onBegan: (() -> Void)?
    var onChanged: ((CGFloat) -> Void)?
    var onEnded: ((CGFloat, CGFloat, CGFloat, Bool) -> Void)?

    private var startX: CGFloat = 0
    private var startY: CGFloat = 0
    private var previousX: CGFloat = 0
    private var previousTime: TimeInterval = 0
    private var maximumTravel: CGFloat = 0
    private var velocityX: CGFloat = 0
    private var dragging = false
    private var rejected = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.numberOfTouchesRequired = 1
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
        isOpaque = false
        backgroundColor = .clear
        isAccessibilityElement = false
        accessibilityElementsHidden = true
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        enabled && bounds.contains(point)
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        guard enabled else { return }
        let point = recognizer.location(in: self)
        let now = ProcessInfo.processInfo.systemUptime
        switch recognizer.state {
        case .began:
            startX = point.x
            startY = point.y
            previousX = point.x
            previousTime = now
            maximumTravel = 0
            velocityX = 0
            dragging = false
            rejected = false
            onBegan?()
        case .changed:
            let elapsed = max(0.001, now - previousTime)
            velocityX = (point.x - previousX) / elapsed
            maximumTravel = max(maximumTravel, hypot(point.x - startX, point.y - startY))
            previousX = point.x
            previousTime = now
            if !dragging && abs(point.y - startY) > 14 { rejected = true }
            if !rejected && abs(point.x - startX) > 10 { dragging = true }
            if dragging { onChanged?(point.x - startX) }
        case .ended, .cancelled, .failed:
            let elapsed = max(0.001, now - previousTime)
            maximumTravel = max(maximumTravel, hypot(point.x - startX, point.y - startY))
            guard !rejected else { return }
            let lastVelocity = point.x == previousX && elapsed < 0.08 ? velocityX : (point.x - previousX) / elapsed
            onEnded?(point.x - startX, lastVelocity, point.x, !dragging && maximumTravel <= 10)
        default:
            break
        }
    }
}
