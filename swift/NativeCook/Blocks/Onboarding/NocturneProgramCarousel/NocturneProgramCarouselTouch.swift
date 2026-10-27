import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

// MARK: - Page area: reading touches and the horizontal pan

/// What the touch observer reports to the engine.
struct NocturneProgramCarouselTouchHandlers {
    /// `onTouchStart` of the scroll view: a finger landed; the count of fingers now down.
    var touchStart: (Int) -> Void
    /// `onTouchEnd` and `onTouchCancel`: a finger left; the count still down.
    var touchEnd: (Int) -> Void
    /// The pan's `onStart`.
    var panStart: () -> Void
    /// The pan's `onUpdate`, with the finger's horizontal translation.
    var panUpdate: (Double) -> Void
    /// The pan's `onFinalize`: translation, horizontal velocity in points per second, success.
    var panFinish: (Double, Double, Bool) -> Void

    static let none = NocturneProgramCarouselTouchHandlers(
        touchStart: { _ in },
        touchEnd: { _ in },
        panStart: {},
        panUpdate: { _ in },
        panFinish: { _, _, _ in }
    )
}

/// Points at the view of the card row, so the observer knows where the pan may begin.
final class NocturneProgramCarouselRegion {
    weak var view: UIView?
}

/// A clear, touch-less view laid behind the card row: it only tells the observer where the row is.
struct NocturneProgramCarouselRegionMarker: UIViewRepresentable {
    let region: NocturneProgramCarouselRegion

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.isUserInteractionEnabled = false
        view.isAccessibilityElement = false
        view.backgroundColor = .clear
        region.view = view
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        region.view = view
    }
}

/// Laid behind the scroll area. It does two jobs for the page area: it counts the fingers that touch
/// the scroll view (`readingHandlers` of the React Native playback hook), and inside the region of
/// the card row it runs `Gesture.Pan().activeOffsetX([-10, 10]).failOffsetY([-18, 18]).maxPointers(1)`.
///
/// It only listens: the recogniser sits on the window and never recognises anything, so the scroll
/// view's pan, the buttons and the gallery's back swipe behave exactly as if it were not there (a
/// SwiftUI `DragGesture` here would be cancelled by the back swipe, and a view on top would swallow
/// the scroll).
struct NocturneProgramCarouselTouchObserver: UIViewRepresentable {
    /// False while the program is not on screen (React Native's `pointerEvents: 'none'`).
    var isEnabled: Bool
    /// `Gesture.Pan().enabled(!previewing)`.
    var panEnabled: Bool
    var region: NocturneProgramCarouselRegion
    var handlers: NocturneProgramCarouselTouchHandlers

    func makeUIView(context: Context) -> NocturneProgramCarouselProbe {
        NocturneProgramCarouselProbe()
    }

    func updateUIView(_ probe: NocturneProgramCarouselProbe, context: Context) {
        probe.isEnabled = isEnabled
        probe.panEnabled = panEnabled
        probe.region = region
        probe.handlers = handlers
    }
}

final class NocturneProgramCarouselProbe: UIView, UIGestureRecognizerDelegate {
    var isEnabled = true
    var panEnabled = true
    var region: NocturneProgramCarouselRegion?
    var handlers = NocturneProgramCarouselTouchHandlers.none

    private var listener: NocturneProgramCarouselListener?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        isAccessibilityElement = false
        backgroundColor = .clear
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("NocturneProgramCarouselProbe is created in code")
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if let listener {
            listener.view?.removeGestureRecognizer(listener)
        }
        listener = nil
        guard let window else { return }
        let listener = NocturneProgramCarouselListener(probe: self)
        listener.cancelsTouchesInView = false
        listener.delaysTouchesBegan = false
        listener.delaysTouchesEnded = false
        listener.delegate = self
        window.addGestureRecognizer(listener)
        self.listener = listener
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    /// Whether the touch landed on the card row, where the pan may begin.
    func regionContains(_ touch: UITouch) -> Bool {
        guard let row = region?.view else { return false }
        return row.bounds.contains(touch.location(in: row))
    }
}

/// Tracks every finger that lands on the scroll area, and decides on its own whether the first
/// finger is a horizontal pan.
///
/// The pan follows React Native Gesture Handler's rules: it activates once the finger has moved
/// more than 10 points sideways, fails once it has moved more than 18 points up or down first, and
/// a second finger fails it (or cancels it once active). The translation is measured from the place
/// the pan activated (the handler resets it there), so the page does not jump. Velocity is the
/// finger's speed over the last 100 ms before it lifts.
final class NocturneProgramCarouselListener: UIGestureRecognizer {
    private weak var probe: NocturneProgramCarouselProbe?

    /// `activeOffsetX([-10, 10])` and `failOffsetY([-18, 18])`.
    private let activeOffsetX: CGFloat = 10
    private let failOffsetY: CGFloat = 18

    private enum PanPhase {
        /// The first finger is down inside the pan region and has not decided yet.
        case undecided
        case active
        /// Failed, cancelled, finished, or outside the pan region: reading only.
        case dead
    }

    /// Every finger on the screen, so the recogniser can end when the last one lifts.
    private var everyTouch: [UITouch] = []
    /// The fingers that landed on the scroll area.
    private var down: [UITouch] = []
    private var phase = PanPhase.dead
    private var downPoint = CGPoint.zero
    private var anchorX: CGFloat = 0
    private var translationX: CGFloat = 0
    private var samples: [(time: TimeInterval, x: CGFloat)] = []

    init(probe: NocturneProgramCarouselProbe) {
        self.probe = probe
        super.init(target: nil, action: nil)
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesBegan(touches, with: event)
        guard let probe else { return }
        for touch in touches {
            everyTouch.append(touch)
        }
        guard probe.isEnabled else { return }
        let wasEmpty = down.isEmpty
        var landed = false
        for touch in touches where probe.bounds.contains(touch.location(in: probe)) {
            down.append(touch)
            landed = true
        }
        guard landed else { return }
        if wasEmpty, let first = down.first {
            let point = first.location(in: nil)
            downPoint = point
            anchorX = point.x
            translationX = 0
            samples = [(first.timestamp, point.x)]
            phase = (probe.panEnabled && probe.regionContains(first)) ? .undecided : .dead
        }
        if down.count > 1 {
            // `maxPointers(1)`: a second finger fails an undecided pan and cancels an active one.
            switch phase {
            case .undecided:
                phase = .dead
            case .active:
                finishPan(success: false, at: event.timestamp)
            case .dead:
                break
            }
        }
        probe.handlers.touchStart(down.count)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesMoved(touches, with: event)
        guard let probe, let first = down.first, touches.contains(where: { $0 === first }) else { return }
        let point = first.location(in: nil)
        samples.append((first.timestamp, point.x))
        samples.removeAll { first.timestamp - $0.time > 0.2 }
        switch phase {
        case .undecided:
            let dx = point.x - downPoint.x
            let dy = point.y - downPoint.y
            if abs(dy) > failOffsetY {
                phase = .dead
            } else if abs(dx) > activeOffsetX {
                phase = .active
                anchorX = point.x
                translationX = 0
                probe.handlers.panStart()
                probe.handlers.panUpdate(0)
            }
        case .active:
            translationX = point.x - anchorX
            probe.handlers.panUpdate(Double(translationX))
        case .dead:
            break
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesEnded(touches, with: event)
        if let first = down.first, touches.contains(where: { $0 === first }) {
            let point = first.location(in: nil)
            samples.append((first.timestamp, point.x))
            if phase == .active {
                translationX = point.x - anchorX
                finishPan(success: true, at: first.timestamp)
            }
            phase = .dead
        }
        release(touches)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesCancelled(touches, with: event)
        if phase == .active {
            finishPan(success: false, at: event.timestamp)
        }
        phase = .dead
        release(touches)
    }

    override func reset() {
        super.reset()
        everyTouch.removeAll()
        down.removeAll()
        samples.removeAll()
        phase = .dead
        translationX = 0
    }

    /// Forgets the fingers that left and reports it; the recogniser ends with the last finger.
    private func release(_ touches: Set<UITouch>) {
        let before = down.count
        down.removeAll { lifted in touches.contains { $0 === lifted } }
        everyTouch.removeAll { lifted in touches.contains { $0 === lifted } }
        if down.count != before {
            probe?.handlers.touchEnd(down.count)
        }
        if everyTouch.isEmpty {
            state = .failed
        }
    }

    /// The pan's `onFinalize`.
    private func finishPan(success: Bool, at time: TimeInterval) {
        guard phase == .active else { return }
        phase = .dead
        probe?.handlers.panFinish(Double(translationX), releaseVelocity(at: time), success)
    }

    /// The finger's horizontal speed over the last 100 ms before `time`; a finger that rested
    /// before it lifted has none.
    private func releaseVelocity(at time: TimeInterval) -> Double {
        let recent = samples.filter { time - $0.time <= 0.1 }
        guard let first = recent.first, let last = recent.last, last.time - first.time > 0.001 else {
            return 0
        }
        return Double((last.x - first.x) / CGFloat(last.time - first.time))
    }
}

// MARK: - Press

/// A zero-delay press recogniser over a button, standing in for React Native's `Pressable`:
/// `onPressIn` when the finger lands, `onPressOut` when it leaves the button's region (the button
/// plus 16 pt, the `pressRetentionOffset`) or lifts, `onPress` after the lift inside the region
/// (React Native calls `onPressOut` first). It is a recogniser that allows the others beside it,
/// not raw touches, so the gallery's back swipe does not end the press while the finger is down
/// (the approach of `EmberTouchView`).
struct NocturneProgramCarouselPressSurface: UIViewRepresentable {
    var onPressIn: () -> Void
    var onPressOut: () -> Void
    var onPress: () -> Void

    func makeUIView(context: Context) -> NocturneProgramCarouselPressView {
        NocturneProgramCarouselPressView()
    }

    func updateUIView(_ view: NocturneProgramCarouselPressView, context: Context) {
        view.onPressIn = onPressIn
        view.onPressOut = onPressOut
        view.onPress = onPress
    }
}

final class NocturneProgramCarouselPressView: UIView, UIGestureRecognizerDelegate {
    var onPressIn: () -> Void = {}
    var onPressOut: () -> Void = {}
    var onPress: () -> Void = {}

    /// This press began on the button.
    private var active = false
    /// The finger is inside the press region.
    private var inside = false

    /// `pressRetentionOffset={16}`.
    private let retention: CGFloat = 16

    override init(frame: CGRect) {
        super.init(frame: frame)
        isAccessibilityElement = false
        backgroundColor = .clear
        let press = UILongPressGestureRecognizer(target: self, action: #selector(track))
        press.minimumPressDuration = 0
        press.allowableMovement = .greatestFiniteMagnitude
        press.cancelsTouchesInView = false
        press.delegate = self
        addGestureRecognizer(press)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("NocturneProgramCarouselPressView is created in code")
    }

    func gestureRecognizer(
        _ recognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func track(_ press: UILongPressGestureRecognizer) {
        let region = bounds.insetBy(dx: -retention, dy: -retention)
        switch press.state {
        case .began:
            active = true
            inside = true
            onPressIn()
        case .changed:
            guard active else { return }
            let within = region.contains(press.location(in: self))
            if inside && !within {
                inside = false
                onPressOut()
            } else if !inside && within {
                inside = true
                onPressIn()
            }
        case .ended:
            guard active else { return }
            active = false
            if inside {
                inside = false
                onPressOut()
                onPress()
            }
        case .cancelled, .failed:
            guard active else { return }
            active = false
            if inside {
                inside = false
                onPressOut()
            }
        default:
            break
        }
    }
}
