import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

// MARK: - Page area: reading touches and the horizontal pan

/// What the touch surface reports to the engine.
struct SpectrumNewsCarouselTouchHandlers {
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
}

/// Covers the scroll content. It does two jobs for the page area: it counts the fingers that touch
/// the scroll view (`readingHandlers` of the React Native playback hook), and inside `panRect` it
/// runs `Gesture.Pan().activeOffsetX([-10, 10]).failOffsetY([-18, 18]).maxPointers(1)`.
struct SpectrumNewsCarouselTouchSurface: UIViewRepresentable {
    /// The scene's frame in the scroll content, where the pan may begin.
    var panRect: CGRect
    /// `Gesture.Pan().enabled(!previewing)`.
    var panEnabled: Bool
    var handlers: SpectrumNewsCarouselTouchHandlers

    func makeUIView(context: Context) -> SpectrumNewsCarouselTouchView {
        SpectrumNewsCarouselTouchView()
    }

    func updateUIView(_ view: SpectrumNewsCarouselTouchView, context: Context) {
        view.recognizer.panRect = panRect
        view.recognizer.panEnabled = panEnabled
        view.recognizer.handlers = handlers
    }
}

/// A clear view that owns the touch recogniser. The recogniser allows every other recogniser beside
/// it (the scroll view's, the gallery's back swipe) and never cancels a touch, the way
/// `EmberTouchView` keeps its finger.
final class SpectrumNewsCarouselTouchView: UIView, UIGestureRecognizerDelegate {
    let recognizer = SpectrumNewsCarouselTouchRecognizer(target: nil, action: nil)

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isAccessibilityElement = false
        isMultipleTouchEnabled = true
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("SpectrumNewsCarouselTouchView is created in code")
    }

    func gestureRecognizer(
        _ recognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
    ) -> Bool {
        true
    }
}

/// Tracks every finger from the first touch down to the last one up, and decides on its own whether
/// the first finger is a horizontal pan.
///
/// The pan follows React Native Gesture Handler's rules: it activates once the finger has moved
/// more than 10 points sideways, fails once it has moved more than 18 points up or down first,
/// and a second finger fails it (or cancels it once active). Translation is measured from the
/// place the finger first touched, so the page does not jump when the pan activates. Velocity is
/// the finger's speed over the last 100 ms before it lifts.
final class SpectrumNewsCarouselTouchRecognizer: UIGestureRecognizer {
    var handlers = SpectrumNewsCarouselTouchHandlers(
        touchStart: { _ in },
        touchEnd: { _ in },
        panStart: {},
        panUpdate: { _ in },
        panFinish: { _, _, _ in }
    )
    var panRect = CGRect.zero
    var panEnabled = true

    /// `activeOffsetX([-10, 10])` and `failOffsetY([-18, 18])`.
    private let activeOffsetX: CGFloat = 10
    private let failOffsetY: CGFloat = 18

    private enum PanPhase {
        /// The first finger is down inside the pan area and has not decided yet.
        case undecided
        case active
        /// Failed, cancelled, finished, or outside the pan area: reading only.
        case dead
    }

    private var phase = PanPhase.dead
    private var down: [UITouch] = []
    private var origin = CGPoint.zero
    private var translation = CGPoint.zero
    private var samples: [(time: TimeInterval, x: CGFloat)] = []

    override init(target: Any?, action: Selector?) {
        super.init(target: target, action: action)
        cancelsTouchesInView = false
        delaysTouchesBegan = false
        delaysTouchesEnded = false
    }

    private func isDown(_ touch: UITouch) -> Bool {
        down.contains { $0 === touch }
    }

    private var primary: UITouch? {
        down.first
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesBegan(touches, with: event)
        let wasEmpty = down.isEmpty
        for touch in touches where !isDown(touch) {
            down.append(touch)
        }
        if wasEmpty, let first = primary {
            origin = first.location(in: nil)
            translation = .zero
            samples = [(first.timestamp, origin.x)]
            let inside = panRect.contains(first.location(in: view))
            phase = (panEnabled && inside) ? .undecided : .dead
            // Recognised at touch down, like a press of no delay: other recognisers run beside it.
            state = .began
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
        handlers.touchStart(down.count)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesMoved(touches, with: event)
        guard let first = primary, touches.contains(where: { $0 === first }) else { return }
        let point = first.location(in: nil)
        translation = CGPoint(x: point.x - origin.x, y: point.y - origin.y)
        samples.append((first.timestamp, point.x))
        samples.removeAll { first.timestamp - $0.time > 0.2 }
        switch phase {
        case .undecided:
            if abs(translation.y) > failOffsetY {
                phase = .dead
            } else if abs(translation.x) > activeOffsetX {
                phase = .active
                handlers.panStart()
                handlers.panUpdate(Double(translation.x))
            }
        case .active:
            handlers.panUpdate(Double(translation.x))
        case .dead:
            break
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesEnded(touches, with: event)
        if let first = primary, touches.contains(where: { $0 === first }) {
            let point = first.location(in: nil)
            translation = CGPoint(x: point.x - origin.x, y: point.y - origin.y)
            samples.append((first.timestamp, point.x))
            if phase == .active {
                finishPan(success: true, at: first.timestamp)
            }
            phase = .dead
        }
        down.removeAll { lifted in touches.contains { $0 === lifted } }
        handlers.touchEnd(down.count)
        if down.isEmpty {
            state = .ended
        }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesCancelled(touches, with: event)
        if phase == .active {
            finishPan(success: false, at: event.timestamp)
        }
        phase = .dead
        down.removeAll { lifted in touches.contains { $0 === lifted } }
        handlers.touchEnd(down.count)
        if down.isEmpty {
            state = .cancelled
        }
    }

    override func reset() {
        super.reset()
        if !down.isEmpty {
            // UIKit ended the gesture without a lift (it was failed from outside): let go of the fingers
            // so the loop is not left waiting for them.
            down.removeAll()
            handlers.touchEnd(0)
        }
        down.removeAll()
        samples.removeAll()
        phase = .dead
        translation = .zero
    }

    /// The pan's `onFinalize`.
    private func finishPan(success: Bool, at time: TimeInterval) {
        guard phase == .active else { return }
        phase = .dead
        handlers.panFinish(Double(translation.x), releaseVelocity(at: time), success)
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

// MARK: - Scroll watcher

/// Reports the drags of the enclosing `UIScrollView` (the `onScrollBeginDrag`, `onScrollEndDrag`,
/// `onMomentumScrollBegin` and `onMomentumScrollEnd` of React Native's ScrollView) by listening to its
/// own pan recogniser, so SwiftUI's `ScrollView` keeps every behaviour it has.
struct SpectrumNewsCarouselScrollWatcher: UIViewRepresentable {
    var onDragBegan: () -> Void
    var onDragEnded: () -> Void
    var onMomentumBegan: () -> Void
    var onMomentumEnded: () -> Void

    func makeUIView(context: Context) -> WatcherView {
        let view = WatcherView()
        view.isUserInteractionEnabled = false
        view.isAccessibilityElement = false
        view.handlers = self
        return view
    }

    func updateUIView(_ view: WatcherView, context: Context) {
        view.handlers = self
        view.attachIfNeeded()
    }

    final class WatcherView: UIView {
        var handlers: SpectrumNewsCarouselScrollWatcher?
        private weak var scroll: UIScrollView?
        private var link: CADisplayLink?
        private var decelerating = false
        private var graceFrames = 0

        override func didMoveToWindow() {
            super.didMoveToWindow()
            attachIfNeeded()
            if window == nil {
                stopLink()
            }
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            attachIfNeeded()
        }

        func attachIfNeeded() {
            guard scroll == nil else { return }
            var ancestor = superview
            while let candidate = ancestor, !(candidate is UIScrollView) {
                ancestor = candidate.superview
            }
            guard let found = ancestor as? UIScrollView else { return }
            scroll = found
            found.panGestureRecognizer.addTarget(self, action: #selector(pan(_:)))
        }

        @objc private func pan(_ recognizer: UIPanGestureRecognizer) {
            switch recognizer.state {
            case .began:
                handlers?.onDragBegan()
            case .ended, .cancelled, .failed:
                handlers?.onDragEnded()
                // Momentum (or a bounce back) begins right after the finger lifts; watch for it.
                graceFrames = 0
                startLink()
            default:
                break
            }
        }

        private func startLink() {
            guard link == nil else { return }
            let displayLink = CADisplayLink(target: self, selector: #selector(tick))
            displayLink.add(to: .main, forMode: .common)
            link = displayLink
        }

        private func stopLink() {
            link?.invalidate()
            link = nil
            if decelerating {
                decelerating = false
                handlers?.onMomentumEnded()
            }
        }

        @objc private func tick() {
            guard let scroll else {
                stopLink()
                return
            }
            if scroll.isDecelerating {
                if !decelerating {
                    decelerating = true
                    handlers?.onMomentumBegan()
                }
            } else if decelerating {
                stopLink()
            } else {
                graceFrames += 1
                if graceFrames > 6 {
                    stopLink()
                }
            }
        }

        deinit {
            link?.invalidate()
        }
    }
}

// MARK: - Press

/// A zero-delay press recogniser over a button, standing in for React Native's `Pressable`:
/// `onPressIn` when the finger lands, `onPressOut` when it leaves the button's region (the button
/// plus 16 pt, the `pressRetentionOffset`) or lifts, `onPress` after the lift inside the region
/// (React Native calls `onPressOut` first). It is a recogniser that allows the others beside it,
/// not raw touches, so the gallery's back swipe does not end the press while the finger is down.
struct SpectrumNewsCarouselPressSurface: UIViewRepresentable {
    var onPressIn: () -> Void
    var onPressOut: () -> Void
    var onPress: () -> Void

    func makeUIView(context: Context) -> SpectrumNewsCarouselPressView {
        SpectrumNewsCarouselPressView()
    }

    func updateUIView(_ view: SpectrumNewsCarouselPressView, context: Context) {
        view.onPressIn = onPressIn
        view.onPressOut = onPressOut
        view.onPress = onPress
    }
}

final class SpectrumNewsCarouselPressView: UIView, UIGestureRecognizerDelegate {
    var onPressIn: () -> Void = {}
    var onPressOut: () -> Void = {}
    var onPress: () -> Void = {}

    /// The finger is inside the press region.
    private var inside = false
    private var active = false

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
        fatalError("SpectrumNewsCarouselPressView is created in code")
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

/// A pressable surface: the content answers the finger like React Native's `PressScale` (0.96 over
/// 150 ms on the strong ease-out; under Reduce Motion it dims to 0.72 instead) or, for the tour's
/// own buttons, like `TourButton` (0.97 over 120 ms; no movement under Reduce Motion). It announces
/// itself as a button.
struct SpectrumNewsCarouselPressable<Content: View>: View {
    enum Feedback {
        /// `PressScale`.
        case pressScale
        /// `TourButton`.
        case tourButton
    }

    let feedback: Feedback
    let reduced: Bool
    let label: String
    /// The point the press scales about; the centre unless a button's wrapper is not its content.
    var anchor: UnitPoint = .center
    var onPressIn: () -> Void
    var onPressOut: () -> Void
    var onPress: () -> Void
    @ViewBuilder var content: () -> Content

    @State private var pressed = false

    var body: some View {
        let scaled = pressed && !reduced
        let target: CGFloat = feedback == .pressScale ? 0.96 : 0.97
        let duration: Double = feedback == .pressScale ? 0.15 : (reduced ? 0 : 0.12)
        let dimmed = feedback == .pressScale && reduced && pressed
        // `transition: 150ms cubic-bezier(0.23, 1, 0.32, 1)` / `120ms` of the React Native styles.
        let curve = Animation.timingCurve(0.23, 1, 0.32, 1, duration: duration)
        content()
            .scaleEffect(scaled ? target : 1, anchor: anchor)
            .opacity(dimmed ? 0.72 : 1)
            .animation(curve, value: pressed)
            .overlay {
                SpectrumNewsCarouselPressSurface(
                    onPressIn: {
                        pressed = true
                        onPressIn()
                    },
                    onPressOut: {
                        pressed = false
                        onPressOut()
                    },
                    onPress: onPress
                )
                .accessibilityHidden(true)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: label))
            .accessibilityAddTraits(.isButton)
            .accessibilityAction {
                onPress()
            }
    }
}
