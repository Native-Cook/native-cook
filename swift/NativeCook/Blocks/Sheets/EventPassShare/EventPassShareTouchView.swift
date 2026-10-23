import SwiftUI
import UIKit

struct EventPassShareTouchView: UIViewRepresentable {
    var scale: CGFloat
    var onBegan: (TimeInterval) -> Bool
    var onChanged: (CGFloat, CGFloat, TimeInterval) -> Void
    var onEnded: (CGFloat, CGFloat, CGFloat, CGFloat, TimeInterval) -> Void
    var onCancelled: (TimeInterval) -> Void

    func makeUIView(context: Context) -> EventPassShareTouchSurface {
        let view = EventPassShareTouchSurface()
        update(view)
        return view
    }

    func updateUIView(_ view: EventPassShareTouchSurface, context: Context) {
        update(view)
    }

    private func update(_ view: EventPassShareTouchSurface) {
        view.scale = scale
        view.onBegan = onBegan
        view.onChanged = onChanged
        view.onEnded = onEnded
        view.onCancelled = onCancelled
    }
}

final class EventPassShareTouchSurface: UIView, UIGestureRecognizerDelegate {
    var scale: CGFloat = 1
    var onBegan: ((TimeInterval) -> Bool)?
    var onChanged: ((CGFloat, CGFloat, TimeInterval) -> Void)?
    var onEnded: ((CGFloat, CGFloat, CGFloat, CGFloat, TimeInterval) -> Void)?
    var onCancelled: ((TimeInterval) -> Void)?

    private var accepted = false
    private var start = CGPoint.zero
    private var last = CGPoint.zero
    private var lastTime = 0.0
    private var velocity = CGVector.zero

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isMultipleTouchEnabled = false
        isAccessibilityElement = false

        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { nil }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let now = Date().timeIntervalSinceReferenceDate
        let location = recognizer.location(in: self)
        switch recognizer.state {
        case .began:
            accepted = onBegan?(now) ?? false
            guard accepted else { return }
            start = location
            last = location
            lastTime = now
            velocity = .zero
        case .changed:
            guard accepted else { return }
            let delta = max(0.001, now - lastTime)
            velocity = CGVector(
                dx: (location.x - last.x) / delta,
                dy: (location.y - last.y) / delta
            )
            last = location
            lastTime = now
            let horizontal = (location.x - start.x) / max(0.001, scale)
            let vertical = (location.y - start.y) / max(0.001, scale)
            onChanged?(horizontal, vertical, now)
        case .ended:
            guard accepted else { return }
            accepted = false
            let safeScale = max(0.001, scale)
            let horizontal = (location.x - start.x) / safeScale
            let vertical = (location.y - start.y) / safeScale
            onEnded?(
                horizontal,
                vertical,
                horizontal + velocity.dx / safeScale * 0.2,
                vertical + velocity.dy / safeScale * 0.2,
                now
            )
        case .cancelled, .failed:
            guard accepted else { return }
            accepted = false
            onCancelled?(now)
        default:
            break
        }
    }
}

struct EventPassShareSheetDragView: UIViewRepresentable {
    var activeOffset: CGFloat
    var onChanged: (CGFloat) -> Void
    var onEnded: (CGFloat, CGFloat) -> Void
    var onCancelled: () -> Void

    func makeUIView(context: Context) -> EventPassShareSheetDragSurface {
        let view = EventPassShareSheetDragSurface()
        update(view)
        return view
    }

    func updateUIView(_ view: EventPassShareSheetDragSurface, context: Context) {
        update(view)
    }

    private func update(_ view: EventPassShareSheetDragSurface) {
        view.activeOffset = activeOffset
        view.onChanged = onChanged
        view.onEnded = onEnded
        view.onCancelled = onCancelled
    }
}

final class EventPassShareSheetDragSurface: UIView, UIGestureRecognizerDelegate {
    var activeOffset: CGFloat = 8
    var onChanged: ((CGFloat) -> Void)?
    var onEnded: ((CGFloat, CGFloat) -> Void)?
    var onCancelled: (() -> Void)?

    private var active = false
    private var start = CGPoint.zero
    private var last = CGPoint.zero
    private var lastTime = 0.0
    private var velocityY: CGFloat = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isAccessibilityElement = false

        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { nil }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let now = Date().timeIntervalSinceReferenceDate
        let location = recognizer.location(in: self)
        switch recognizer.state {
        case .began:
            active = false
            start = location
            last = location
            lastTime = now
            velocityY = 0
        case .changed:
            let translation = location.y - start.y
            let delta = max(0.001, now - lastTime)
            velocityY = (location.y - last.y) / delta
            last = location
            lastTime = now
            if !active, abs(translation) >= activeOffset { active = true }
            if active { onChanged?(translation) }
        case .ended:
            guard active else { return }
            active = false
            onEnded?(location.y - start.y, velocityY)
        case .cancelled, .failed:
            guard active else { return }
            active = false
            onCancelled?()
        default:
            break
        }
    }
}
