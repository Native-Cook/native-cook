import SwiftUI
import UIKit

/// The block's touch surface: the cassette area and the five keys, each with its own zero-delay recognizer
/// that runs beside the gallery's back swipe and keeps its touch to the end. Outside those areas it takes
/// nothing, so the rest of the screen still reaches SwiftUI.
struct CassetteDeckTouchView: UIViewRepresentable {
    let model: CassetteDeckModel

    func makeUIView(context: Context) -> CassetteDeckTouchSurface {
        CassetteDeckTouchSurface(model: model)
    }

    func updateUIView(_ surface: CassetteDeckTouchSurface, context: Context) {}
}

@MainActor
final class CassetteDeckTouchSurface: UIView {
    private let swipe: CassetteDeckSwipeArea
    private let keys: [CassetteDeckKeyArea]

    init(model: CassetteDeckModel) {
        swipe = CassetteDeckSwipeArea(model: model)
        keys = (0..<CassetteDeckSim.keyCount).map { CassetteDeckKeyArea(index: $0, model: model) }
        super.init(frame: .zero)
        backgroundColor = .clear
        isAccessibilityElement = false
        addSubview(swipe)
        keys.forEach(addSubview)
        var elements: [Any] = [swipe]
        elements.append(contentsOf: keys)
        accessibilityElements = elements
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { nil }

    override func layoutSubviews() {
        super.layoutSubviews()
        let metrics = CassetteDeckMetrics(size: bounds.size)
        let s = metrics.scale
        let layout = CassetteDeckTheme.Layout.self
        swipe.metrics = metrics
        swipe.frame = CGRect(
            x: 0,
            y: metrics.offsetY + (layout.cassette.y - layout.swipeMargin) * s,
            width: metrics.width,
            height: (layout.cassette.h + layout.swipeMargin * 2) * s
        )
        for (index, key) in keys.enumerated() {
            key.frame = CGRect(
                x: (layout.keys.x0 + Double(index) * layout.keys.pitch) * s,
                y: metrics.offsetY + layout.keys.y * s,
                width: layout.keys.w * s,
                height: layout.keys.h * s
            )
        }
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let hit = super.hitTest(point, with: event)
        return hit === self ? nil : hit
    }
}

/// The cassette area: a drag slides the cassettes, a drag that starts on a loaded reel turns the tape.
@MainActor
final class CassetteDeckSwipeArea: UIView, UIGestureRecognizerDelegate {
    private let model: CassetteDeckModel
    private var tracker = CassetteDeckVelocityTracker()
    private var origin = CGPoint.zero
    private var dragOrigin = CGPoint.zero
    private var dragging = false
    var metrics = CassetteDeckMetrics(size: .zero)

    /// The React Native pan starts after 2 pt of travel.
    private let minDistance = 2.0

    init(model: CassetteDeckModel) {
        self.model = model
        super.init(frame: .zero)
        backgroundColor = .clear
        isAccessibilityElement = true
        accessibilityTraits = .adjustable
        accessibilityLabel = "Cassette"
        accessibilityHint = "Swipe left or right to change the cassette"

        let press = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        press.minimumPressDuration = 0
        press.allowableMovement = .greatestFiniteMagnitude
        press.numberOfTouchesRequired = 1
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

    override func accessibilityIncrement() { model.nudge(1) }

    override func accessibilityDecrement() { model.nudge(-1) }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let now = ProcessInfo.processInfo.systemUptime
        switch recognizer.state {
        case .began:
            origin = point
            dragging = false
            tracker.reset()
            tracker.add(point, at: now)
            model.swipeBegan(at: point, metrics: metrics)
        case .changed:
            tracker.add(point, at: now)
            if !dragging && hypot(point.x - origin.x, point.y - origin.y) >= minDistance {
                dragging = true
                // RNGH reports translation from touch-down when the 2 pt activation threshold is crossed.
                dragOrigin = origin
                model.swipeStarted()
            }
            if dragging {
                model.swipeMoved(
                    translationX: point.x - dragOrigin.x,
                    at: point,
                    velocity: tracker.velocity(at: now),
                    metrics: metrics
                )
            }
        case .ended, .cancelled, .failed:
            tracker.add(point, at: now)
            model.swipeEnded(velocity: tracker.velocity(at: now), metrics: metrics)
            dragging = false
        default:
            break
        }
    }
}

/// One key: held while a finger is on it, released when the finger lifts or the touch is cancelled.
@MainActor
final class CassetteDeckKeyArea: UIView, UIGestureRecognizerDelegate {
    private let index: Int
    private let model: CassetteDeckModel

    init(index: Int, model: CassetteDeckModel) {
        self.index = index
        self.model = model
        super.init(frame: .zero)
        backgroundColor = .clear
        isAccessibilityElement = true
        accessibilityTraits = .button
        accessibilityLabel = CassetteDeckTheme.keyLabels[index]

        let press = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        press.minimumPressDuration = 0
        press.allowableMovement = .greatestFiniteMagnitude
        press.numberOfTouchesRequired = 1
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

    /// VoiceOver's double tap: a press that lasts 160 ms, like the React Native block's.
    override func accessibilityActivate() -> Bool {
        model.press(index, down: true)
        Task { [model, index] in
            try? await Task.sleep(for: .milliseconds(160))
            model.press(index, down: false)
        }
        return true
    }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        switch recognizer.state {
        case .began:
            model.press(index, down: true)
        case .ended, .cancelled, .failed:
            model.press(index, down: false)
        default:
            break
        }
    }
}
