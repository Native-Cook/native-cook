import SwiftUI
import UIKit

/// The hero's pan: `Gesture.Pan().maxPointers(1).activeOffsetX([-6, 6]).failOffsetY([-12, 12])`.
///
/// It takes one finger, becomes active once the finger has moved 6 pt sideways, and gives up if
/// it moves 12 pt up or down first (so the page's scroll view keeps vertical drags). `onStart`
/// fires when it activates, `onUpdate` with the horizontal translation from the finger's first
/// contact (in window coordinates, as React Native Gesture Handler measures it), `onEnd` with
/// the horizontal velocity in points per second, and `onFinalize` last.
///
/// The finger is tracked by a zero-delay press recogniser that allows every other recogniser
/// beside it, not by a drag gesture: the gallery's back swipe and the page's scroll view would
/// otherwise cancel it the moment they begin (see `EmberTouchView`).
struct CipherSeamTouch: UIViewRepresentable {
    let enabled: Bool
    let onStart: () -> Void
    let onUpdate: (CGFloat) -> Void
    let onEnd: (CGFloat) -> Void
    let onFinalize: () -> Void

    func makeUIView(context: Context) -> CipherSeamTouchView {
        let view = CipherSeamTouchView()
        view.apply(self)
        return view
    }

    func updateUIView(_ view: CipherSeamTouchView, context: Context) {
        view.apply(self)
    }
}

final class CipherSeamTouchView: UIView, UIGestureRecognizerDelegate {
    private enum Phase {
        case idle
        case possible
        case active
        case failed
    }

    private var onStart: () -> Void = {}
    private var onUpdate: (CGFloat) -> Void = { _ in }
    private var onEnd: (CGFloat) -> Void = { _ in }
    private var onFinalize: () -> Void = {}

    private var phase = Phase.idle
    private var origin = CGPoint.zero
    private var samples: [(time: TimeInterval, x: CGFloat)] = []

    /// `activeOffsetX([-6, 6])`.
    private static let activeOffsetX: CGFloat = 6
    /// `failOffsetY([-12, 12])`.
    private static let failOffsetY: CGFloat = 12
    /// Finger samples older than this do not count towards the release velocity.
    private static let velocityWindow: TimeInterval = 0.1

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isAccessibilityElement = false
        let press = UILongPressGestureRecognizer(target: self, action: #selector(track))
        press.minimumPressDuration = 0
        press.allowableMovement = .greatestFiniteMagnitude
        press.numberOfTouchesRequired = 1
        press.cancelsTouchesInView = false
        press.delegate = self
        addGestureRecognizer(press)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("CipherSeamTouchView is created in code")
    }

    func apply(_ touch: CipherSeamTouch) {
        onStart = touch.onStart
        onUpdate = touch.onUpdate
        onEnd = touch.onEnd
        onFinalize = touch.onFinalize
        if isUserInteractionEnabled != touch.enabled { isUserInteractionEnabled = touch.enabled }
    }

    func gestureRecognizer(
        _ recognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func track(_ press: UILongPressGestureRecognizer) {
        // The window's coordinates, so a vertical scroll of the page counts as vertical travel.
        let point = press.location(in: nil)
        let now = CACurrentMediaTime()
        switch press.state {
        case .began:
            phase = .possible
            origin = point
            samples = [(time: now, x: point.x)]
        case .changed:
            samples.append((time: now, x: point.x))
            samples.removeAll { now - $0.time > 0.15 }
            move(to: point)
        case .ended, .cancelled, .failed:
            if phase == .active {
                onEnd(velocityX(at: now))
                onFinalize()
            }
            phase = .idle
            samples = []
        default:
            break
        }
    }

    private func move(to point: CGPoint) {
        let dx = point.x - origin.x
        let dy = point.y - origin.y
        switch phase {
        case .possible:
            if abs(dy) >= Self.failOffsetY {
                phase = .failed
            } else if abs(dx) >= Self.activeOffsetX {
                phase = .active
                onStart()
                onUpdate(dx)
            }
        case .active:
            onUpdate(dx)
        case .idle, .failed:
            break
        }
    }

    /// The finger's horizontal speed over the last 100 ms; none if it has rested that long.
    private func velocityX(at now: TimeInterval) -> CGFloat {
        guard let last = samples.last, now - last.time <= Self.velocityWindow else { return 0 }
        guard let first = samples.first(where: { last.time - $0.time <= Self.velocityWindow }), last.time > first.time else {
            return 0
        }
        return (last.x - first.x) / CGFloat(last.time - first.time)
    }
}
