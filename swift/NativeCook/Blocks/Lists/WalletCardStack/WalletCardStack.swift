import QuartzCore
import SwiftUI
import UIKit

/// A wallet home recreated from the approved React Native block.
///
/// The screen is intentionally measured in the same 402 × 874 point coordinate space as the
/// reference. A top offset keeps the reference's 62 point safe-area baseline while still allowing
/// the gallery to present the block in a different window.
struct WalletCardStack: View {
    var autoPlay: Bool
    var freezeAt: Double?

    static let loop: Double = WalletCardStackMotion.loop
    static let LOOP_MS = WalletCardStackMotion.LOOP_MS

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var timeline = WalletCardStackTimeline()
    @State private var awake = false
    @State private var scriptWindowActive = false
    @State private var wakeToken = 0
    @State private var autoplayGeneration = 0
    @State private var resumeAfterTouch = false
    @State private var lightHaptic = 0
    @State private var mediumHaptic = 0

    init(autoPlay: Bool = false, freezeAt: Double? = nil) {
        self.autoPlay = autoPlay
        self.freezeAt = freezeAt
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let oy = WalletCardStackTheme.topOffset(safeTop: insets.top)

            TimelineView(.animation(paused: freezeAt != nil || (!awake && !scriptWindowActive))) { tick in
                let playScript = autoPlay && freezeAt == nil && !reduceMotion
                let _ = timeline.advance(to: tick.date, autoPlay: playScript, reduced: reduceMotion)

                WalletCardStackScreen(
                    width: width,
                    height: height,
                    oy: oy,
                    values: timeline.values,
                    timeline: timeline,
                    autoPlay: playScript,
                    reduced: reduceMotion,
                    onAwake: wake,
                    onTouchFinished: finishTouch,
                    onLightHaptic: { lightHaptic += 1 },
                    onMediumHaptic: { mediumHaptic += 1 }
                )
                .frame(width: width, height: height, alignment: .topLeading)
                .allowsHitTesting(freezeAt == nil)
            }
            .ignoresSafeArea()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(WalletCardStackTheme.color.screen.ignoresSafeArea())
        .preferredColorScheme(.light)
        .sensoryFeedback(.impact(weight: .light), trigger: lightHaptic)
        .sensoryFeedback(.impact(weight: .medium), trigger: mediumHaptic)
        .onAppear {
            resetTimeline()
            awake = false
            resumeAfterTouch = false
            scriptWindowActive = autoPlay && freezeAt == nil && !reduceMotion
        }
        .onChange(of: autoPlay) { _, value in
            resetTimeline()
            awake = false
            resumeAfterTouch = false
            scriptWindowActive = value && freezeAt == nil && !reduceMotion
            autoplayGeneration += 1
            wakeToken += 1
        }
        .onChange(of: freezeAt) { _, _ in
            resetTimeline()
            awake = false
            resumeAfterTouch = false
            scriptWindowActive = autoPlay && freezeAt == nil && !reduceMotion
            autoplayGeneration += 1
            wakeToken += 1
        }
        .onChange(of: reduceMotion) { _, _ in
            resetTimeline()
            awake = false
            resumeAfterTouch = false
            scriptWindowActive = autoPlay && freezeAt == nil && !reduceMotion
            autoplayGeneration += 1
            wakeToken += 1
        }
        .task(id: wakeToken) {
            guard awake else { return }
            let milliseconds = autoPlay
                ? Int64(WalletCardStackMotion.manualSettleSeconds * 1_000)
                : Int64(WalletCardStackMotion.manualSettleSeconds * 1_000)
            try? await Task.sleep(for: .milliseconds(milliseconds))
            if !Task.isCancelled, !timeline.touching { awake = false }
        }
        .task(id: WalletCardStackLoopKey(
            autoPlay: autoPlay && freezeAt == nil,
            reduced: reduceMotion,
            generation: autoplayGeneration
        )) {
            await autoplayLoop()
        }
    }

    private func wake() {
        awake = true
        resumeAfterTouch = false
        if autoPlay {
            scriptWindowActive = false
            autoplayGeneration += 1
        }
        wakeToken += 1
    }

    private func finishTouch() {
        awake = true
        if autoPlay {
            resumeAfterTouch = true
            scriptWindowActive = false
            autoplayGeneration += 1
        }
        wakeToken += 1
    }

    private func autoplayLoop() async {
        guard autoPlay, freezeAt == nil, !reduceMotion else {
            scriptWindowActive = false
            return
        }

        if resumeAfterTouch {
            try? await Task.sleep(for: .milliseconds(Int64(WalletCardStackMotion.RESUME_AFTER_MS)))
            guard !Task.isCancelled else { return }

            let now = Date.now
            timeline.setHidden(false, at: now, reduced: reduceMotion)
            if !reduceMotion {
                timeline.startDrop(at: now)
            }
            scriptWindowActive = true
            try? await Task.sleep(for: .milliseconds(Int64(WalletCardStackMotion.resumeRestSeconds * 1_000)))
            guard !Task.isCancelled else { return }
        }

        resumeAfterTouch = false
        timeline.reset(autoPlay: true, preserveSwap: true)
        scriptWindowActive = true
    }

    private func resetTimeline() {
        timeline.reset(autoPlay: autoPlay)
        if let freezeAt {
            timeline.freeze(atMilliseconds: freezeAt, reduced: reduceMotion)
        }
    }
}

// MARK: - Timeline

extension WalletCardStack {
    struct WalletCardStackValues {
        var frontLift = 0.0
        var backLift = 0.0
        var tilt = 0.0
        var turn = 0.0
        var squish = 0.0
        var hidden = 0.0
        var swapTurn = 0.0
        var hiddenNow = false
    }

    /// A small deterministic tween used by both the autoplay script and touch-driven UI.
    struct WalletCardStackTween {
        var from = 0.0
        var to = 0.0
        var start = Date.distantPast
        var duration = 0.0
        var curve = WalletCardStackMotion.linear
        var active = false

        func value(at date: Date) -> Double {
            guard active, duration > 0 else { return to }
            let progress = min(max(date.timeIntervalSince(start) / duration, 0), 1)
            return from + (to - from) * curve(progress)
        }

        mutating func start(from: Double, to: Double, at date: Date, duration: Double, curve: @escaping (Double) -> Double) {
            self.from = from
            self.to = to
            self.start = date
            self.duration = duration
            self.curve = curve
            active = true
        }

        mutating func finishIfNeeded(at date: Date) {
            if active, date.timeIntervalSince(start) >= duration {
                active = false
            }
        }
    }

    /// A damped spring whose duration and damping ratio mirror Reanimated's spring config.
    struct WalletCardStackSpring {
        var from = 0.0
        var to = 0.0
        var velocity = 0.0
        var start = Date.distantPast
        var duration = 0.0
        var dampingRatio = 1.0
        var active = false

        func value(at date: Date) -> Double {
            guard active, duration > 0 else { return to }
            let elapsed = date.timeIntervalSince(start)
            guard elapsed > 0 else { return from }
            guard elapsed < duration else { return to }

            let omega = 12.0 / duration
            let zeta = dampingRatio
            let displacement = from - to
            guard abs(displacement) > 0.0001 else { return to }
            let normalizedVelocity = velocity * duration / displacement

            if zeta < 1 {
                let damped = omega * sqrt(max(0, 1 - zeta * zeta))
                let coefficient = (zeta * omega + normalizedVelocity) / max(damped, 0.0001)
                let envelope = exp(-zeta * omega * elapsed)
                let response = envelope * (cos(damped * elapsed) + coefficient * sin(damped * elapsed))
                return to + displacement * response
            }

            let envelope = exp(-omega * elapsed)
            let response = envelope * (1 + (omega + normalizedVelocity) * elapsed)
            return to + displacement * response
        }

        mutating func start(from: Double, to: Double, at date: Date, duration: Double, dampingRatio: Double, velocity: Double = 0) {
            self.from = from
            self.to = to
            self.start = date
            self.duration = duration
            self.dampingRatio = dampingRatio
            self.velocity = velocity
            active = true
        }

        mutating func finishIfNeeded(at date: Date) {
            if active, date.timeIntervalSince(start) >= duration {
                active = false
            }
        }
    }

    /// The one source of time for autoplay and all touch-settling transforms.
    final class WalletCardStackTimeline {
        var values = WalletCardStackValues()
        var lastDate: Date?
        var currentDate = Date.distantPast
        var clock = 0.0
        var touching = false
        var dragging = false
        var manual = false
        var hold = 0.0
        var resumeDelay = 0.0
        var grab = 0.0
        var tapDropAt: Date?

        var frontSpring = WalletCardStackSpring()
        var backSpring = WalletCardStackSpring()
        var tiltSpring = WalletCardStackSpring()
        var turnSpring = WalletCardStackSpring()
        var squishSpring = WalletCardStackSpring()
        var hiddenTween = WalletCardStackTween()
        var swapTween = WalletCardStackTween()
        var frontTween = WalletCardStackTween()
        var backTween = WalletCardStackTween()
        var tiltTween = WalletCardStackTween()
        var squishTween = WalletCardStackTween()
        var pendingFrontSpringAt: Date?
        var pendingSquishSpringAt: Date?

        func reset(autoPlay: Bool, preserveSwap: Bool = false) {
            let swapTurn = preserveSwap ? values.swapTurn : 0
            values = WalletCardStackValues(swapTurn: swapTurn)
            lastDate = nil
            currentDate = .distantPast
            clock = 0
            touching = false
            dragging = false
            manual = false
            hold = 0
            resumeDelay = 0
            grab = 0
            tapDropAt = nil
            frontSpring = WalletCardStackSpring()
            backSpring = WalletCardStackSpring()
            tiltSpring = WalletCardStackSpring()
            turnSpring = WalletCardStackSpring()
            squishSpring = WalletCardStackSpring()
            hiddenTween = WalletCardStackTween()
            swapTween = WalletCardStackTween(from: swapTurn, to: swapTurn)
            frontTween = WalletCardStackTween()
            backTween = WalletCardStackTween()
            tiltTween = WalletCardStackTween()
            squishTween = WalletCardStackTween()
            pendingFrontSpringAt = nil
            pendingSquishSpringAt = nil
            _ = autoPlay
        }

        func freeze(atMilliseconds milliseconds: Double, reduced: Bool) {
            values = scriptValues(at: milliseconds / 1_000, reduced: reduced)
            values.hiddenNow = values.hidden > 0.5
            clock = milliseconds / 1_000
            manual = true
            lastDate = nil
        }

        func advance(to date: Date, autoPlay: Bool, reduced: Bool) {
            currentDate = date
            guard let lastDate else {
                self.lastDate = date
                return
            }
            let elapsed = max(date.timeIntervalSince(lastDate), 0)
            let dt = min(elapsed, 0.064)
            self.lastDate = date
            guard dt > 0 else { return }

            if touching || dragging {
                if !dragging { updateManual(to: date) }
                return
            }

            if hold > 0 {
                hold -= dt
                updateManual(to: date)
                if hold <= 0 {
                    hold = 0
                    if autoPlay {
                        values.hiddenNow = false
                        setHidden(false, at: date, reduced: reduced)
                        startDrop(at: date)
                        resumeDelay = WalletCardStackMotion.resumeRestSeconds
                    } else {
                        manual = false
                    }
                }
            } else if resumeDelay > 0 {
                resumeDelay -= dt
                updateManual(to: date)
                if resumeDelay <= 0 {
                    resumeDelay = 0
                    manual = false
                    clock = 0
                    values = WalletCardStackValues()
                }
            } else if autoPlay && !manual {
                clock += elapsed
                if clock >= WalletCardStackMotion.loop {
                    clock.formTruncatingRemainder(dividingBy: WalletCardStackMotion.loop)
                }
                values = scriptValues(at: clock, reduced: reduced)
            } else {
                updateManual(to: date)
            }

            if let tapDropAt, date >= tapDropAt {
                self.tapDropAt = nil
                startDrop(at: date)
            }
        }

        func beginTouch() {
            if lastDate == nil {
                let now = Date.now
                currentDate = now
                lastDate = now
            }
            touching = true
            manual = true
            hold = 0
            resumeDelay = 0
        }

        func jumpClock(to value: Double) {
            clock = value
            let now = Date.now
            currentDate = now
            lastDate = now
        }

        func endTouch(autoPlay: Bool, reduced: Bool) {
            touching = false
            guard autoPlay else { return }
            hold = WalletCardStackMotion.resumeAfterSeconds
            _ = reduced
        }

        func beginDrag() {
            manual = true
            dragging = true
            hold = 0
            resumeDelay = 0
            grab = values.frontLift
            frontSpring.active = false
            backSpring.active = false
            tiltSpring.active = false
            turnSpring.active = false
        }

        func dragChanged(translationX: Double, translationY: Double, reduced: Bool) {
            guard !reduced else { return }
            let lift = max(0, grab + WalletCardStackMotion.rubber(-translationY, limit: WalletCardStackMotion.pullLimit))
            values.frontLift = lift
            values.backLift = WalletCardStackMotion.backLift * min(1, lift / WalletCardStackMotion.pullLift)
            values.tilt = WalletCardStackMotion.tilt * min(1.2, lift / WalletCardStackMotion.pullLift)
            values.turn = min(WalletCardStackMotion.turnMax,
                              max(-WalletCardStackMotion.turnMax, translationX * WalletCardStackMotion.turnPerDX))
        }

        func endDrag(velocity: Double, reduced: Bool) {
            dragging = false
            guard !reduced else { return }
            startDrop(at: currentDate, velocity: velocity)
        }

        func tapCard(reduced: Bool) {
            guard !reduced else { return }
            dragging = false
            manual = true
            frontSpring.start(from: values.frontLift, to: WalletCardStackMotion.tapLift, at: currentDate,
                              duration: WalletCardStackMotion.springPullSeconds,
                              dampingRatio: WalletCardStackMotion.springPullDamping)
            backSpring.start(from: values.backLift, to: WalletCardStackMotion.backLift * WalletCardStackMotion.tapLift / WalletCardStackMotion.pullLift,
                             at: currentDate, duration: WalletCardStackMotion.springPullSeconds,
                             dampingRatio: WalletCardStackMotion.springPullDamping)
            tiltSpring.start(from: values.tilt, to: WalletCardStackMotion.tilt * WalletCardStackMotion.tapLift / WalletCardStackMotion.pullLift,
                             at: currentDate, duration: WalletCardStackMotion.springPullSeconds,
                             dampingRatio: WalletCardStackMotion.springPullDamping)
            tapDropAt = currentDate.addingTimeInterval(WalletCardStackMotion.tapDropSeconds)
        }

        func startDrop(at date: Date, velocity: Double = 0) {
            guard !dragging else { return }
            manual = true
            frontSpring.start(from: values.frontLift, to: 0, at: date,
                              duration: WalletCardStackMotion.springDropSeconds,
                              dampingRatio: WalletCardStackMotion.springDropDamping,
                              velocity: velocity)
            backSpring.start(from: values.backLift, to: 0, at: date,
                             duration: WalletCardStackMotion.springDropSeconds,
                             dampingRatio: WalletCardStackMotion.springDropDamping)
            tiltSpring.start(from: values.tilt, to: 0, at: date,
                             duration: WalletCardStackMotion.springDropSeconds,
                             dampingRatio: WalletCardStackMotion.springDropDamping)
            turnSpring.start(from: values.turn, to: 0, at: date,
                             duration: WalletCardStackMotion.springDropSeconds,
                             dampingRatio: WalletCardStackMotion.springDropDamping)
            squishTween.start(from: values.squish, to: 1, at: date.addingTimeInterval(WalletCardStackMotion.squishDelay),
                              duration: WalletCardStackMotion.squishInSeconds, curve: WalletCardStackMotion.easeOut)
            pendingSquishSpringAt = date.addingTimeInterval(WalletCardStackMotion.squishDelay + WalletCardStackMotion.squishInSeconds)
        }

        func setHidden(_ hidden: Bool, at date: Date? = nil, reduced: Bool) {
            let now = date ?? currentDate
            let duration = reduced ? WalletCardStackMotion.fadeSeconds : WalletCardStackMotion.hideSpanSeconds
            let start = values.hidden
            hiddenTween.start(from: start, to: hidden ? 1 : 0, at: now, duration: duration,
                              curve: reduced ? WalletCardStackMotion.linear : WalletCardStackMotion.easeInOut)
            values.hiddenNow = hidden
            manual = true
        }

        func toggleHidden(reduced: Bool) {
            setHidden(!values.hiddenNow, reduced: reduced)
        }

        func swap() {
            manual = true
            let next = values.swapTurn.rounded() + 1
            swapTween.start(from: values.swapTurn, to: next, at: currentDate,
                            duration: WalletCardStackMotion.swapTurnSeconds, curve: WalletCardStackMotion.easeInOut)
            frontTween.start(from: values.frontLift, to: -WalletCardStackMotion.swapDip, at: currentDate,
                             duration: WalletCardStackMotion.swapDipSeconds, curve: WalletCardStackMotion.easeOut)
            pendingFrontSpringAt = currentDate.addingTimeInterval(WalletCardStackMotion.swapDipSeconds)
            squishTween.start(from: values.squish, to: WalletCardStackMotion.swapSquish, at: currentDate,
                              duration: WalletCardStackMotion.swapSquishInSeconds, curve: WalletCardStackMotion.easeOut)
            pendingSquishSpringAt = currentDate.addingTimeInterval(WalletCardStackMotion.swapSquishInSeconds)
        }

        private func updateManual(to date: Date) {
            if frontSpring.active {
                values.frontLift = frontSpring.value(at: date)
                frontSpring.finishIfNeeded(at: date)
            }
            if backSpring.active {
                values.backLift = backSpring.value(at: date)
                backSpring.finishIfNeeded(at: date)
            }
            if tiltSpring.active {
                values.tilt = tiltSpring.value(at: date)
                tiltSpring.finishIfNeeded(at: date)
            }
            if turnSpring.active {
                values.turn = turnSpring.value(at: date)
                turnSpring.finishIfNeeded(at: date)
            }
            if squishSpring.active {
                values.squish = squishSpring.value(at: date)
                squishSpring.finishIfNeeded(at: date)
            }
            if hiddenTween.active {
                values.hidden = hiddenTween.value(at: date)
                hiddenTween.finishIfNeeded(at: date)
            }
            if swapTween.active {
                values.swapTurn = swapTween.value(at: date)
                swapTween.finishIfNeeded(at: date)
            }
            if frontTween.active {
                values.frontLift = frontTween.value(at: date)
                frontTween.finishIfNeeded(at: date)
            }
            if squishTween.active {
                values.squish = squishTween.value(at: date)
                squishTween.finishIfNeeded(at: date)
            }
            if let pendingFrontSpringAt, date >= pendingFrontSpringAt {
                self.pendingFrontSpringAt = nil
                frontSpring.start(from: values.frontLift, to: 0, at: date,
                                  duration: WalletCardStackMotion.springDropSeconds,
                                  dampingRatio: WalletCardStackMotion.springDropDamping)
            }
            if let pendingSquishSpringAt, date >= pendingSquishSpringAt {
                self.pendingSquishSpringAt = nil
                squishSpring.start(from: values.squish, to: 0, at: date,
                                   duration: WalletCardStackMotion.springSquishSeconds,
                                   dampingRatio: WalletCardStackMotion.springSquishDamping)
            }
        }

        private func scriptValues(at time: Double, reduced: Bool) -> WalletCardStackValues {
            var output = WalletCardStackValues()
            let milliseconds = WalletCardStackMotion.wrap(time * 1_000)
            if reduced {
                output.hidden = WalletCardStackMotion.reducedHidden(at: milliseconds)
                output.hiddenNow = output.hidden > 0.5
                return output
            }
            output.frontLift = WalletCardStackMotion.loopLift(at: milliseconds)
            output.backLift = WalletCardStackMotion.backShare
                * WalletCardStackMotion.loopLift(at: max(0, milliseconds - WalletCardStackMotion.backLagMS))
            output.tilt = WalletCardStackMotion.loopLean
                * min(1.1, max(0, output.frontLift) / WalletCardStackMotion.pullLift)
            output.turn = WalletCardStackMotion.loopTurn(at: milliseconds)
            output.squish = WalletCardStackMotion.loopSquish(at: milliseconds, lift: output.frontLift)
            return output
        }
    }
}

// MARK: - Screen and sections

extension WalletCardStack {
    struct WalletCardStackLoopKey: Equatable {
        let autoPlay: Bool
        let reduced: Bool
        let generation: Int
    }

    struct WalletCardStackTouchView: UIViewRepresentable {
        let cardRect: CGRect
        let onBegan: (CGPoint, Bool) -> Void
        let onChanged: (CGPoint, CGPoint, CGPoint, Bool) -> Void
        let onEnded: (CGPoint, CGPoint, CGPoint, Bool) -> Void

        func makeUIView(context: Context) -> WalletCardStackTouchSurface {
            let view = WalletCardStackTouchSurface()
            view.cardRect = cardRect
            view.onBegan = onBegan
            view.onChanged = onChanged
            view.onEnded = onEnded
            return view
        }

        func updateUIView(_ view: WalletCardStackTouchSurface, context: Context) {
            view.cardRect = cardRect
            view.onBegan = onBegan
            view.onChanged = onChanged
            view.onEnded = onEnded
        }
    }

    final class WalletCardStackTouchSurface: UIView, UIGestureRecognizerDelegate {
        var cardRect = CGRect.zero
        var onBegan: ((CGPoint, Bool) -> Void)?
        var onChanged: ((CGPoint, CGPoint, CGPoint, Bool) -> Void)?
        var onEnded: ((CGPoint, CGPoint, CGPoint, Bool) -> Void)?

        private var start = CGPoint.zero
        private var previous = CGPoint.zero
        private var previousTime = 0.0
        private var velocity = CGPoint.zero
        private var cardMode = false

        override init(frame: CGRect) {
            super.init(frame: frame)
            // UIKit receives the drag alongside DemoScreen's back swipe; SwiftUI DragGesture can be
            // cancelled when that parent recognizer begins after 24 points.
            let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            recognizer.minimumPressDuration = 0
            recognizer.allowableMovement = .greatestFiniteMagnitude
            recognizer.cancelsTouchesInView = false
            recognizer.delegate = self
            addGestureRecognizer(recognizer)
            backgroundColor = .clear
            isOpaque = false
            isAccessibilityElement = false
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
            true
        }

        @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
            let point = recognizer.location(in: self)
            let time = CACurrentMediaTime()
            switch recognizer.state {
            case .began:
                start = point
                previous = point
                previousTime = time
                velocity = .zero
                cardMode = cardRect.contains(point)
                onBegan?(point, cardMode)
            case .changed:
                let elapsed = max(0.001, time - previousTime)
                velocity = CGPoint(x: (point.x - previous.x) / elapsed, y: (point.y - previous.y) / elapsed)
                previous = point
                previousTime = time
                onChanged?(point, CGPoint(x: point.x - start.x, y: point.y - start.y), velocity, cardMode)
            case .ended, .cancelled, .failed:
                onEnded?(point, CGPoint(x: point.x - start.x, y: point.y - start.y), velocity, cardMode)
                cardMode = false
            default:
                break
            }
        }
    }

    struct WalletCardStackScreen: View {
        let width: CGFloat
        let height: CGFloat
        let oy: CGFloat
        let values: WalletCardStackValues
        let timeline: WalletCardStackTimeline
        let autoPlay: Bool
        let reduced: Bool
        let onAwake: () -> Void
        let onTouchFinished: () -> Void
        let onLightHaptic: () -> Void
        let onMediumHaptic: () -> Void

        var body: some View {
            ZStack(alignment: .topLeading) {
                WalletCardStackTheme.color.screen

                WalletCardStackTouchView(
                    cardRect: .null,
                    onBegan: { _, _ in
                        onAwake()
                        timeline.beginTouch()
                    },
                    onChanged: { _, _, _, _ in },
                    onEnded: { _, _, _, _ in
                        timeline.endTouch(autoPlay: autoPlay, reduced: reduced)
                        onTouchFinished()
                    }
                )
                .frame(width: width, height: height, alignment: .topLeading)

                WalletCardStackHeader(width: width, oy: oy, onAwake: onAwake, onLightHaptic: onLightHaptic)
                WalletCardStackQuickTopUp(width: width, oy: oy, onAwake: onAwake, onLightHaptic: onLightHaptic)
                WalletCardStackLatestTransactions(width: width, oy: oy, onAwake: onAwake, onLightHaptic: onLightHaptic)

                WalletCardStackWallet(
                    oy: oy,
                    values: values,
                    timeline: timeline,
                    autoPlay: autoPlay,
                    reduced: reduced,
                    onAwake: onAwake,
                    onTouchFinished: onTouchFinished,
                    onLightHaptic: onLightHaptic,
                    onMediumHaptic: onMediumHaptic
                )

                WalletCardStackTabBar(width: width, height: height, oy: oy, onAwake: onAwake, onLightHaptic: onLightHaptic)
            }
            .frame(width: width, height: height, alignment: .topLeading)
        }
    }

    struct WalletCardStackHeader: View {
        let width: CGFloat
        let oy: CGFloat
        let onAwake: () -> Void
        let onLightHaptic: () -> Void

        var body: some View {
            WalletCardStackCircleButton(label: "Menu", x: WalletCardStackTheme.header.inset,
                                        y: oy + WalletCardStackTheme.header.y - WalletCardStackTheme.header.button / 2,
                                        onAwake: onAwake, onLightHaptic: onLightHaptic) {
                WalletCardStackGlyph.menu
                    .stroke(WalletCardStackTheme.color.ink, style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
                    .frame(width: 22, height: 22)
            }

            WalletCardStackCircleButton(label: "Add card", x: width - WalletCardStackTheme.header.inset - WalletCardStackTheme.header.button,
                                        y: oy + WalletCardStackTheme.header.y - WalletCardStackTheme.header.button / 2,
                                        onAwake: onAwake, onLightHaptic: onLightHaptic) {
                WalletCardStackGlyph.plus
                    .stroke(WalletCardStackTheme.color.ink, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                    .frame(width: 22, height: 22)
            }

            Text("Wallet")
                .font(.system(size: 19, weight: .bold))
                .foregroundStyle(WalletCardStackTheme.color.ink)
                .frame(width: width, height: 26)
                .offset(y: oy + WalletCardStackTheme.header.titleY - 13)
                .accessibilityAddTraits(.isHeader)

            Text("My Cards & Transaction")
                .font(.system(size: 12.1, weight: .semibold))
                .foregroundStyle(WalletCardStackTheme.color.inkSoft)
                .frame(width: width, height: 18)
                .offset(y: oy + WalletCardStackTheme.header.subtitleY - 9)
        }
    }

    struct WalletCardStackCircleButton<Content: View>: View {
        let label: String
        let x: CGFloat
        let y: CGFloat
        let onAwake: () -> Void
        let onLightHaptic: () -> Void
        @ViewBuilder let content: Content

        var body: some View {
            Button {
                onAwake()
            } label: {
                content
                    .frame(width: WalletCardStackTheme.header.button, height: WalletCardStackTheme.header.button)
                    .background(WalletCardStackTheme.color.button)
                    .clipShape(Circle())
                    .shadow(color: WalletCardStackTheme.rgba.blackButtonShadow, radius: 9, y: 10)
                    .frame(width: WalletCardStackTheme.header.button + 12,
                           height: WalletCardStackTheme.header.button + 12)
            }
            .buttonStyle(WalletCardStackPressStyle(animated: false, onPress: onAwake))
            .accessibilityLabel(label)
            .frame(width: WalletCardStackTheme.header.button + 12,
                   height: WalletCardStackTheme.header.button + 12)
            .offset(x: x - 6, y: y - 6)
        }
    }

    struct WalletCardStackSectionHead: View {
        let title: String
        let y: CGFloat
        let width: CGFloat

        var body: some View {
            HStack {
                Text(title)
                    .font(.system(size: 14.5, weight: .bold))
                    .foregroundStyle(WalletCardStackTheme.color.ink)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
                Button("See more \(title)") { }
                    .font(.system(size: 12.4, weight: .medium))
                    .foregroundStyle(WalletCardStackTheme.color.inkSoft)
                    .buttonStyle(.plain)
            }
            .frame(width: width - 40, height: 24, alignment: .center)
            .offset(x: 20, y: y - 12)
        }
    }

    struct WalletCardStackQuickTopUp: View {
        let width: CGFloat
        let oy: CGFloat
        let onAwake: () -> Void
        let onLightHaptic: () -> Void

        var body: some View {
            WalletCardStackSectionHead(title: "Quick Top-Up", y: oy + WalletCardStackTheme.topUp.titleY, width: width)

            WalletCardStackPersonButton(label: "Add contact", name: "Add", centerX: WalletCardStackTheme.topUp.addX,
                                        y: oy + WalletCardStackTheme.topUp.rowY,
                                        onAwake: onAwake, onLightHaptic: onLightHaptic) {
                ZStack {
                    Circle()
                        .stroke(WalletCardStackTheme.color.accent,
                                style: StrokeStyle(lineWidth: 1.4, dash: [3.2, 3.2]))
                    WalletCardStackGlyph.plus
                        .stroke(WalletCardStackTheme.color.accent, style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
                        .frame(width: 22, height: 22)
                }
                .frame(width: WalletCardStackTheme.topUp.avatar, height: WalletCardStackTheme.topUp.avatar)
            }

            RoundedRectangle(cornerRadius: 1)
                .fill(WalletCardStackTheme.color.divider)
                .frame(width: 1.5, height: 52)
                .offset(x: WalletCardStackTheme.topUp.dividerX, y: oy + WalletCardStackTheme.topUp.rowY - 26)

            ForEach(WalletCardStackData.quick) { person in
                WalletCardStackPersonButton(label: "Top up \(person.name)", name: person.name,
                                            centerX: WalletCardStackData.quickCenterX(for: person),
                                            y: oy + WalletCardStackTheme.topUp.rowY,
                                            onAwake: onAwake, onLightHaptic: onLightHaptic) {
                    WalletCardStackAvatar(person: person, size: WalletCardStackTheme.topUp.avatar)
                }
            }
        }
    }

    struct WalletCardStackPersonButton<Content: View>: View {
        let label: String
        let name: String
        let centerX: CGFloat
        let y: CGFloat
        let onAwake: () -> Void
        let onLightHaptic: () -> Void
        @ViewBuilder let content: Content

        var body: some View {
            Button {
                onAwake()
            } label: {
                VStack(spacing: 0) {
                    content
                    Text(name)
                        .font(.system(size: 13.5, weight: .semibold))
                        .foregroundStyle(WalletCardStackTheme.color.ink)
                        .padding(.top, 5.5)
                        .lineLimit(1)
                }
                .frame(width: 80, alignment: .top)
            }
            .buttonStyle(WalletCardStackPressStyle(animated: false, onPress: onAwake))
            .accessibilityLabel(label)
            .frame(width: 80)
            .offset(x: centerX - 40, y: y - WalletCardStackTheme.topUp.avatar / 2)
        }
    }

    struct WalletCardStackAvatar: View {
        let person: WalletCardStackPerson
        let size: CGFloat

        var body: some View {
            ZStack {
                WalletCardStackTheme.tintColor(for: person.tint)
                WalletCardStackAsset.image(person.photo)
                    .resizable()
                    .scaledToFill()
            }
            .frame(width: size, height: size)
            .clipShape(Circle())
        }
    }

    struct WalletCardStackLatestTransactions: View {
        let width: CGFloat
        let oy: CGFloat
        let onAwake: () -> Void
        let onLightHaptic: () -> Void

        var body: some View {
            WalletCardStackSectionHead(title: "Latest Transactions", y: oy + WalletCardStackTheme.latest.titleY, width: width)
            ForEach(WalletCardStackData.latest) { transaction in
                WalletCardStackTransactionTile(transaction: transaction,
                                                onAwake: onAwake, onLightHaptic: onLightHaptic)
                    .offset(x: WalletCardStackTheme.latest.xs[transaction.slot],
                            y: oy + WalletCardStackTheme.latest.top)
            }
            ForEach(WalletCardStackData.earlier) { transaction in
                WalletCardStackTransactionTile(transaction: transaction,
                                                onAwake: onAwake, onLightHaptic: onLightHaptic)
                    .offset(x: WalletCardStackTheme.latest.xs[transaction.slot],
                            y: oy + WalletCardStackTheme.latest.top + WalletCardStackTheme.latest.height + 15)
            }
        }
    }

    struct WalletCardStackTransactionTile: View {
        let transaction: WalletCardStackPayment
        let onAwake: () -> Void
        let onLightHaptic: () -> Void

        var body: some View {
            Button {
                onAwake()
            } label: {
                VStack(alignment: .leading, spacing: 0) {
                    WalletCardStackAvatar(person: transaction.person, size: WalletCardStackTheme.latest.avatar)
                    Text(transaction.person.name)
                        .font(.system(size: 15.5, weight: .bold))
                        .foregroundStyle(WalletCardStackTheme.color.ink)
                        .padding(.top, 9)
                    Text(transaction.date)
                        .font(.system(size: 10.4, weight: .medium))
                        .foregroundStyle(WalletCardStackTheme.color.inkSoft)
                        .padding(.top, 1)
                    Text(transaction.amount)
                        .font(.system(size: 11.5, weight: .bold).monospacedDigit())
                        .foregroundStyle(transaction.incoming ? WalletCardStackTheme.color.green : WalletCardStackTheme.color.red)
                        .padding(.top, 7)
                }
                .padding(.leading, 10)
                .padding(.top, 14)
                .frame(width: WalletCardStackTheme.latest.width, height: WalletCardStackTheme.latest.height, alignment: .topLeading)
                .background(WalletCardStackTheme.color.tile)
                .clipShape(.rect(cornerRadius: WalletCardStackTheme.latest.radius))
            }
            .buttonStyle(WalletCardStackPressStyle(animated: false, onPress: onAwake))
            .accessibilityLabel("\(transaction.person.name), \(transaction.date.replacing(",", with: ", ")), \(transaction.amount)")
            .frame(width: WalletCardStackTheme.latest.width, height: WalletCardStackTheme.latest.height)
        }
    }

    struct WalletCardStackTabBar: View {
        let width: CGFloat
        let height: CGFloat
        let oy: CGFloat
        let onAwake: () -> Void
        let onLightHaptic: () -> Void

        var body: some View {
            let top = oy + WalletCardStackTheme.tabBar.top
            ZStack(alignment: .topLeading) {
                WalletCardStackTheme.color.white
                    .frame(width: width, height: max(0, height - top), alignment: .topLeading)

                ForEach(Array(WalletCardStackTab.all.enumerated()), id: \.element) { index, tab in
                    Button {
                        onAwake()
                    } label: {
                        VStack(spacing: 0) {
                            tab.glyph
                                .stroke(tab.active ? WalletCardStackTheme.color.ink : WalletCardStackTheme.color.muted,
                                        style: StrokeStyle(lineWidth: tab.stroke, lineCap: .round, lineJoin: .round))
                                .frame(width: 26, height: 26)
                            Text(tab.label)
                                .font(.system(size: 12, weight: tab.active ? .bold : .semibold))
                                .foregroundStyle(tab.active ? WalletCardStackTheme.color.ink : WalletCardStackTheme.color.muted)
                                .padding(.top, 2)
                        }
                        .frame(width: 80, alignment: .top)
                    }
                    .buttonStyle(WalletCardStackTabPressStyle(onPress: onAwake))
                    .accessibilityLabel(tab.label)
                    .accessibilityAddTraits(tab.active ? .isSelected : [])
                    .frame(width: 80)
                    .offset(x: WalletCardStackTheme.tabBar.xs[index] - 40,
                            y: WalletCardStackTheme.tabBar.iconY - WalletCardStackTheme.tabBar.top - 13)
                }

                Button {
                    onAwake()
                } label: {
                    WalletCardStackGlyph.scan
                        .stroke(WalletCardStackTheme.color.white, style: StrokeStyle(lineWidth: 2.2, lineCap: .butt, lineJoin: .miter))
                        .frame(width: 30, height: 30)
                        .frame(width: WalletCardStackTheme.tabBar.fab, height: WalletCardStackTheme.tabBar.fab)
                        .background(
                            LinearGradient(colors: [WalletCardStackTheme.color.fabTop, WalletCardStackTheme.color.fabBottom],
                                           startPoint: .top, endPoint: .bottom)
                        )
                        .clipShape(Circle())
                        .shadow(color: WalletCardStackTheme.rgba.fabShadow, radius: 10, y: 8)
                }
                .buttonStyle(WalletCardStackPressStyle(animated: false, onPress: onAwake))
                .accessibilityLabel("Scan")
                .frame(width: WalletCardStackTheme.tabBar.fab, height: WalletCardStackTheme.tabBar.fab)
                .offset(x: width / 2 - WalletCardStackTheme.tabBar.fab / 2,
                        y: WalletCardStackTheme.tabBar.fabY - WalletCardStackTheme.tabBar.top - WalletCardStackTheme.tabBar.fab / 2)
            }
            .frame(width: width, height: max(0, height - top), alignment: .topLeading)
            .offset(y: top)
        }
    }
}

// MARK: - Wallet and cards

extension WalletCardStack {
    struct WalletCardStackWallet: View {
        let oy: CGFloat
        let values: WalletCardStackValues
        let timeline: WalletCardStackTimeline
        let autoPlay: Bool
        let reduced: Bool
        let onAwake: () -> Void
        let onTouchFinished: () -> Void
        let onLightHaptic: () -> Void
        let onMediumHaptic: () -> Void
        @State private var landingHapticTask: Task<Void, Never>? = nil

        var body: some View {
            ZStack(alignment: .topLeading) {
                WalletCardStackWalletBackVisual()

                WalletCardStackCards(values: values)

                WalletCardStackWalletFrontVisual()

                WalletCardStackTouchView(
                    cardRect: CGRect(
                        x: 0,
                        y: 0,
                        width: WalletCardStackTheme.wallet.width,
                        height: -WalletCardStackMotion.touchTop + WalletCardStackTheme.wallet.pocketDip + 6
                    ),
                    onBegan: { _, _ in
                        landingHapticTask?.cancel()
                        onAwake()
                        timeline.beginTouch()
                        timeline.beginDrag()
                    },
                    onChanged: { _, translation, _, _ in
                        timeline.dragChanged(
                            translationX: Double(translation.x),
                            translationY: Double(translation.y),
                            reduced: reduced
                        )
                    },
                    onEnded: { _, translation, velocity, _ in
                        let distance = abs(translation.x) + abs(translation.y)
                        if distance < 8 {
                            timeline.tapCard(reduced: reduced)
                            landingHapticTask = Task { @MainActor in
                                try? await Task.sleep(for: .milliseconds(Int64(WalletCardStackMotion.tapDropSeconds * 1_000)))
                                guard !Task.isCancelled else { return }
                                onMediumHaptic()
                            }
                        } else {
                            landingHapticTask?.cancel()
                            timeline.endDrag(velocity: -Double(velocity.y), reduced: reduced)
                            onMediumHaptic()
                        }
                        timeline.endTouch(autoPlay: autoPlay, reduced: reduced)
                        onTouchFinished()
                    }
                )
                .frame(width: WalletCardStackTheme.wallet.width,
                       height: -WalletCardStackMotion.touchTop + WalletCardStackTheme.wallet.pocketDip + 6,
                       alignment: .topLeading)
                .offset(y: WalletCardStackMotion.touchTop)

                Text(WalletCardStackData.balance.label)
                    .font(.system(size: 12.2, weight: .medium))
                    .foregroundStyle(WalletCardStackTheme.color.white.opacity(0.9))
                    .frame(width: WalletCardStackTheme.pocketUI.pillW, height: 18, alignment: .leading)
                    .offset(x: WalletCardStackTheme.pocketUI.pillX, y: WalletCardStackTheme.pocketUI.labelY - 9)

                WalletCardStackBalance(values: values, reduced: reduced)
                    .offset(x: WalletCardStackTheme.pocketUI.pillX,
                            y: WalletCardStackTheme.pocketUI.balanceY - 20.5)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(values.hiddenNow ? "Balance hidden" : "\(WalletCardStackData.balance.amount) \(WalletCardStackData.balance.currency)")

                WalletCardStackAddBalance(onAwake: onAwake, onLightHaptic: onLightHaptic)

                Button {
                    onAwake()
                    onLightHaptic()
                    if !reduced { timeline.swap() }
                } label: {
                    WalletCardStackGlyph.swap
                        .stroke(WalletCardStackTheme.color.white, style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))
                        .frame(width: 16, height: 16)
                        .rotationEffect(.degrees(values.swapTurn * 180))
                        .frame(width: WalletCardStackTheme.pocketUI.icon, height: WalletCardStackTheme.pocketUI.icon)
                        .background(WalletCardStackTheme.color.white.opacity(0.1))
                        .overlay(Circle().stroke(WalletCardStackTheme.color.white.opacity(0.2), lineWidth: 1))
                        .clipShape(Circle())
                        .frame(width: WalletCardStackTheme.pocketUI.icon + 16,
                               height: WalletCardStackTheme.pocketUI.icon + 16)
                }
                .buttonStyle(WalletCardStackPressStyle(onPress: onAwake))
                .accessibilityLabel("Switch card")
                .frame(width: WalletCardStackTheme.pocketUI.icon + 16,
                       height: WalletCardStackTheme.pocketUI.icon + 16)
                .offset(x: WalletCardStackTheme.pocketUI.iconXs[0] - (WalletCardStackTheme.pocketUI.icon + 16) / 2,
                        y: WalletCardStackTheme.pocketUI.iconY - (WalletCardStackTheme.pocketUI.icon + 16) / 2)

                Button {
                    onAwake()
                    onLightHaptic()
                    timeline.toggleHidden(reduced: reduced)
                } label: {
                    WalletCardStackGlyph.eye(crossed: !values.hiddenNow)
                        .stroke(WalletCardStackTheme.color.white, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                        .frame(width: 17, height: 17)
                        .scaleEffect(17 / 18)
                        .frame(width: WalletCardStackTheme.pocketUI.icon, height: WalletCardStackTheme.pocketUI.icon)
                        .background(WalletCardStackTheme.color.white.opacity(0.1))
                        .overlay(Circle().stroke(WalletCardStackTheme.color.white.opacity(0.2), lineWidth: 1))
                        .clipShape(Circle())
                        .frame(width: WalletCardStackTheme.pocketUI.icon + 16,
                               height: WalletCardStackTheme.pocketUI.icon + 16)
                }
                .buttonStyle(WalletCardStackPressStyle(onPress: onAwake))
                .accessibilityLabel(values.hiddenNow ? "Show balance" : "Hide balance")
                .accessibilityValue(values.hiddenNow ? "Hidden" : "Visible")
                .frame(width: WalletCardStackTheme.pocketUI.icon + 16,
                       height: WalletCardStackTheme.pocketUI.icon + 16)
                .offset(x: WalletCardStackTheme.pocketUI.iconXs[1] - (WalletCardStackTheme.pocketUI.icon + 16) / 2,
                        y: WalletCardStackTheme.pocketUI.iconY - (WalletCardStackTheme.pocketUI.icon + 16) / 2)
            }
            .frame(width: WalletCardStackTheme.wallet.width, height: WalletCardStackTheme.wallet.height, alignment: .topLeading)
            .scaleEffect(x: CGFloat(1 + (WalletCardStackMotion.squishScaleX - 1) * values.squish),
                         y: CGFloat(1 + (WalletCardStackMotion.squishScaleY - 1) * values.squish),
                         anchor: .bottom)
            .offset(x: WalletCardStackTheme.wallet.left, y: oy + WalletCardStackTheme.wallet.top)
        }
    }

    struct WalletCardStackCards: View {
        let values: WalletCardStackValues

        var body: some View {
            ZStack(alignment: .topLeading) {
                WalletCardStackCard(kind: .back)
                    .frame(width: WalletCardStackTheme.cards.back.width, height: WalletCardStackTheme.cards.back.height)
                    .offset(x: WalletCardStackTheme.cards.back.x, y: WalletCardStackTheme.cards.back.y - WalletCardStackMotion.touchTop)
                    .offset(y: CGFloat(-values.backLift))

                WalletCardStackFrontCard(values: values)
                    .offset(x: WalletCardStackTheme.cards.front.x, y: WalletCardStackTheme.cards.front.y - WalletCardStackMotion.touchTop)
                    .offset(y: CGFloat(-values.frontLift))
                    .rotationEffect(.degrees(values.tilt))
            }
            .frame(width: WalletCardStackTheme.wallet.width,
                   height: -WalletCardStackMotion.touchTop + WalletCardStackTheme.wallet.pocketDip + 6,
                   alignment: .topLeading)
            .contentShape(Rectangle())
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(WalletCardStackData.card.network) card ending \(WalletCardStackData.card.last4)")
            .accessibilityHint("Pulls the card out of the wallet")
            .accessibilityAddTraits(.isButton)
            .offset(y: WalletCardStackMotion.touchTop)
        }
    }

    struct WalletCardStackFrontCard: View {
        let values: WalletCardStackValues

        var body: some View {
            let card = WalletCardStackTheme.cards.front
            let lifted = min(1, max(0, values.frontLift / WalletCardStackMotion.pullLift))
            let turnRatio = values.turn / WalletCardStackMotion.loopSway
            let sheenX = card.width * (0.5 + turnRatio * WalletCardStackMotion.sheenTravel) - 55
            let sheenAlpha = WalletCardStackMotion.sheenOpacity * lifted
                * (0.35 + 0.65 * min(1, abs(turnRatio)))

            ZStack(alignment: .topLeading) {
                WalletCardStackCard(kind: .front)

                LinearGradient(
                    colors: [.white.opacity(0), .white.opacity(0.9), .white.opacity(0)],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .frame(width: 110, height: 300)
                .rotationEffect(.degrees(20))
                .offset(x: sheenX, y: -60)
                .opacity(sheenAlpha)
            }
            .frame(width: card.width, height: card.height, alignment: .topLeading)
            .clipShape(.rect(cornerRadius: card.radius))
            .rotation3DEffect(
                .degrees(values.turn),
                axis: (x: 0, y: 1, z: 0),
                perspective: 1 / WalletCardStackMotion.turnPerspective
            )
            .shadow(
                color: Color(red: 42 / 255, green: 20 / 255, blue: 98 / 255)
                    .opacity(WalletCardStackMotion.cardShadowOpacity * lifted),
                radius: 4 + 10 * lifted,
                x: 0,
                y: 2 + 8 * lifted
            )
        }
    }

    enum WalletCardStackCardKind {
        case back
        case front
    }

    struct WalletCardStackCard: View {
        let kind: WalletCardStackCardKind

        var body: some View {
            switch kind {
            case .back:
                WalletCardStackAsset.image("WalletCardStack-back-field.png")
                    .resizable(resizingMode: .stretch)
                    .clipShape(.rect(cornerRadius: WalletCardStackTheme.cards.back.radius))
            case .front:
                ZStack(alignment: .topLeading) {
                    WalletCardStackAsset.image("WalletCardStack-card-field.png")
                        .resizable(resizingMode: .stretch)

                    Text(WalletCardStackData.card.name)
                        .font(.system(size: 17.5, weight: .bold))
                        .tracking(0.4)
                        .foregroundStyle(Color.white.opacity(0.88))
                        .frame(height: 24, alignment: .top)
                        .offset(x: WalletCardStackTheme.cards.padX,
                                y: WalletCardStackTheme.cards.nameY - WalletCardStackTheme.cards.front.y - 11)

                    HStack(spacing: 0) {
                        Text("**** **** ****  ")
                            .font(.system(size: 8, weight: .semibold))
                            .tracking(0.8)
                        Text(WalletCardStackData.card.last4)
                            .font(.system(size: 10, weight: .semibold))
                            .tracking(0.4)
                    }
                    .foregroundStyle(Color.white.opacity(0.85))
                    .frame(height: 16, alignment: .top)
                    .offset(x: WalletCardStackTheme.cards.padX,
                            y: WalletCardStackTheme.cards.numberY - WalletCardStackTheme.cards.front.y - 8)

                    Text(WalletCardStackData.card.network)
                        .font(.system(size: 19.5, weight: .black).italic())
                        .foregroundStyle(WalletCardStackTheme.color.white)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .frame(height: 24, alignment: .top)
                        .offset(x: -WalletCardStackTheme.cards.padX,
                                y: WalletCardStackTheme.cards.brandY - WalletCardStackTheme.cards.front.y - 12)

                    Text("Valid: \(WalletCardStackData.card.valid)")
                        .font(.system(size: 10.5, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.92))
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .frame(height: 16, alignment: .top)
                        .offset(x: -WalletCardStackTheme.cards.padX,
                                y: WalletCardStackTheme.cards.validY - WalletCardStackTheme.cards.front.y - 8)
                }
                .clipShape(.rect(cornerRadius: WalletCardStackTheme.cards.front.radius))
            }
        }
    }

    struct WalletCardStackBalance: View {
        let values: WalletCardStackValues
        let reduced: Bool

        var body: some View {
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                ForEach(WalletCardStackData.balanceCharacters) { character in
                    WalletCardStackBalanceCharacter(character: character.value, index: character.id,
                                                    hidden: values.hidden, reduced: reduced)
                }
                Text(WalletCardStackData.balance.currency)
                    .font(.system(size: 12.3, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.8))
                    .padding(.leading, 2)
            }
        }
    }

    struct WalletCardStackBalanceCharacter: View {
        let character: String
        let index: Int
        let hidden: Double
        let reduced: Bool

        private var progress: Double {
            guard !reduced else { return hidden }
            return min(1, max(0, (hidden * WalletCardStackMotion.hideSpanMS - Double(index) * WalletCardStackMotion.hideCharStaggerMS) / WalletCardStackMotion.hideCharDurationSeconds))
        }

        var body: some View {
            ZStack {
                Text(character)
                    .font(.system(size: 24.4, weight: .bold))
                    .tracking(-0.5)
                    .foregroundStyle(WalletCardStackTheme.color.white)
                    .opacity(1 - progress)
                        .offset(y: reduced ? 0 : CGFloat(-WalletCardStackMotion.hideRise * progress))
                if character.rangeOfCharacter(from: .decimalDigits) != nil {
                    Text("•")
                        .font(.system(size: 24.4, weight: .bold))
                        .tracking(-0.5)
                        .foregroundStyle(WalletCardStackTheme.color.white)
                        .opacity(progress)
                        .offset(y: reduced ? 0 : CGFloat(WalletCardStackMotion.hideRise * (1 - progress)))
                        .scaleEffect(reduced ? 1 : CGFloat(0.6 + 0.4 * progress))
                }
            }
        }
    }

    struct WalletCardStackAddBalance: View {
        let onAwake: () -> Void
        let onLightHaptic: () -> Void

        var body: some View {
            Button {
                onAwake()
                onLightHaptic()
            } label: {
                HStack(spacing: 9) {
                    WalletCardStackGlyph.plus
                        .stroke(WalletCardStackTheme.color.white, style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
                        .frame(width: 17, height: 17)
                        .scaleEffect(17 / 22)
                    Text("Add Balance")
                        .font(.system(size: 13.8, weight: .medium))
                        .foregroundStyle(WalletCardStackTheme.color.white)
                }
                .frame(width: WalletCardStackTheme.pocketUI.pillW, height: WalletCardStackTheme.pocketUI.pillH)
                .background(WalletCardStackTheme.color.white.opacity(0.16))
                .overlay(Capsule().stroke(WalletCardStackTheme.color.white.opacity(0.22), lineWidth: 1))
                .clipShape(Capsule())
                .frame(width: WalletCardStackTheme.pocketUI.pillW + 16,
                       height: WalletCardStackTheme.pocketUI.pillH + 16)
            }
            .buttonStyle(WalletCardStackPressStyle(onPress: onAwake))
            .accessibilityLabel("Add balance")
            .frame(width: WalletCardStackTheme.pocketUI.pillW + 16,
                   height: WalletCardStackTheme.pocketUI.pillH + 16)
            .offset(x: WalletCardStackTheme.pocketUI.pillX - 8,
                    y: WalletCardStackTheme.pocketUI.pillY - 8)
        }
    }

    struct WalletCardStackWalletBackVisual: View {
        var body: some View {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 9)
                    .fill(WalletCardStackTheme.rgba.walletBackBar)
                    .frame(width: WalletCardStackTheme.wallet.width * 0.8,
                           height: 18)
                    .offset(x: WalletCardStackTheme.wallet.width * 0.1,
                            y: WalletCardStackTheme.wallet.height - 14)
                    .shadow(color: WalletCardStackTheme.rgba.walletBackShadow, radius: 5, y: 6)

                RoundedRectangle(cornerRadius: WalletCardStackTheme.wallet.flapRadius)
                    .fill(WalletCardStackTheme.flapGradient)
                    .frame(width: WalletCardStackTheme.wallet.width,
                           height: WalletCardStackTheme.wallet.height - WalletCardStackTheme.wallet.flapShort)

                RoundedRectangle(cornerRadius: WalletCardStackTheme.wallet.flapRadius - 1)
                    .stroke(WalletCardStackTheme.wallet.rimTopColor, lineWidth: 1.5)
                    .frame(width: WalletCardStackTheme.wallet.width - 2,
                           height: WalletCardStackTheme.wallet.height - 2 - WalletCardStackTheme.wallet.flapShort)
                    .offset(x: 1, y: 1)
                    .opacity(0.8)
                    .overlay(alignment: .top) {
                        LinearGradient(colors: [WalletCardStackTheme.wallet.rimTopColor, .clear],
                                       startPoint: .top, endPoint: .bottom)
                            .frame(height: 50)
                            .mask {
                                RoundedRectangle(cornerRadius: WalletCardStackTheme.wallet.flapRadius - 1)
                                    .stroke(WalletCardStackTheme.wallet.rimTopColor, lineWidth: 1.5)
                            }
                    }
            }
            .offset(x: WalletCardStackTheme.wallet.pad, y: WalletCardStackTheme.wallet.pad)
            .frame(width: WalletCardStackTheme.wallet.width + WalletCardStackTheme.wallet.pad * 2,
                   height: WalletCardStackTheme.wallet.height + WalletCardStackTheme.wallet.pad * 2,
                   alignment: .topLeading)
            .offset(x: -WalletCardStackTheme.wallet.pad, y: -WalletCardStackTheme.wallet.pad)
        }
    }

    struct WalletCardStackWalletFrontVisual: View {
        var body: some View {
            ZStack(alignment: .topLeading) {
                WalletCardStackPocketShape()
                    .fill(WalletCardStackTheme.pocketGradient)
                    .frame(width: WalletCardStackTheme.wallet.width, height: WalletCardStackTheme.wallet.height)

                WalletCardStackPocketShape()
                    .fill(LinearGradient(colors: [Color.rgba(36, 16, 79, 0), Color.rgba(36, 16, 79, 0.12), Color.rgba(36, 16, 79, 0.8)],
                                          startPoint: UnitPoint(x: 0.5, y: WalletCardStackTheme.wallet.pocketSide / WalletCardStackTheme.wallet.height),
                                          endPoint: .bottom))
                    .frame(width: WalletCardStackTheme.wallet.width, height: WalletCardStackTheme.wallet.height)

                WalletCardStackPocketShape()
                    .fill(RadialGradient(colors: [WalletCardStackTheme.rgba.pocketGlow, .clear],
                                         center: UnitPoint(x: WalletCardStackTheme.wallet.pocketGlow.cx,
                                                           y: WalletCardStackTheme.wallet.pocketGlow.cy),
                                         startRadius: 0,
                                         endRadius: WalletCardStackTheme.wallet.width * WalletCardStackTheme.wallet.pocketGlow.r))
                    .frame(width: WalletCardStackTheme.wallet.width, height: WalletCardStackTheme.wallet.height)

                WalletCardStackPocketShape()
                    .fill(ImagePaint(image: WalletCardStackAsset.image("WalletCardStack-leather.jpg"), sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 3))
                    .opacity(0.16)
                    .blendMode(.softLight)
                    .frame(width: WalletCardStackTheme.wallet.width, height: WalletCardStackTheme.wallet.height)

                WalletCardStackStitchShape()
                    .stroke(WalletCardStackTheme.rgba.stitch, style: StrokeStyle(lineWidth: 1.1, dash: [4.5, 3.2]))
                    .frame(width: WalletCardStackTheme.wallet.width, height: WalletCardStackTheme.wallet.height)

                WalletCardStackRimShape()
                    .stroke(Color(hex: 0xE2CBFB), lineWidth: 1)
                    .opacity(0.7)
                    .frame(width: WalletCardStackTheme.wallet.width, height: WalletCardStackTheme.wallet.height)

                WalletCardStackRimShape()
                    .stroke(Color.rgba(48, 24, 100, 0.7), lineWidth: 1.4)
                    .frame(width: WalletCardStackTheme.wallet.width, height: WalletCardStackTheme.wallet.height)
                    .offset(y: 1.4)
            }
            .offset(x: WalletCardStackTheme.wallet.pad, y: WalletCardStackTheme.wallet.pad)
            .frame(width: WalletCardStackTheme.wallet.width + WalletCardStackTheme.wallet.pad * 2,
                   height: WalletCardStackTheme.wallet.height + WalletCardStackTheme.wallet.pad * 2,
                   alignment: .topLeading)
            .offset(x: -WalletCardStackTheme.wallet.pad, y: -WalletCardStackTheme.wallet.pad)
        }
    }

    struct WalletCardStackPocketShape: Shape {
        func path(in rect: CGRect) -> Path {
            let w = rect.width
            let h = rect.height
            let side = CGFloat(WalletCardStackTheme.wallet.pocketSide)
            let dip = CGFloat(WalletCardStackTheme.wallet.pocketDip)
            let cr = CGFloat(WalletCardStackTheme.wallet.cornerRadius)
            let radius = CGFloat(WalletCardStackTheme.wallet.pocketRadius)
            let inset = CGFloat(WalletCardStackTheme.wallet.pocketInset)
            let bottom = h - CGFloat(WalletCardStackTheme.wallet.pocketBottom)
            var path = Path()
            path.move(to: CGPoint(x: inset, y: side + cr))
            path.addQuadCurve(to: CGPoint(x: inset + cr, y: side), control: CGPoint(x: inset, y: side))
            path.addCurve(to: CGPoint(x: w / 2, y: dip), control1: CGPoint(x: w * 0.3, y: side), control2: CGPoint(x: w * 0.36, y: dip))
            path.addCurve(to: CGPoint(x: w - inset - cr, y: side), control1: CGPoint(x: w * 0.64, y: dip), control2: CGPoint(x: w * 0.7, y: side))
            path.addQuadCurve(to: CGPoint(x: w - inset, y: side + cr), control: CGPoint(x: w - inset, y: side))
            path.addLine(to: CGPoint(x: w - inset, y: bottom - radius))
            path.addCurve(to: CGPoint(x: w - inset - radius, y: bottom), control1: CGPoint(x: w - inset, y: bottom - radius * 0.45), control2: CGPoint(x: w - inset - radius * 0.45, y: bottom))
            path.addLine(to: CGPoint(x: inset + radius, y: bottom))
            path.addCurve(to: CGPoint(x: inset, y: bottom - radius), control1: CGPoint(x: inset + radius * 0.45, y: bottom), control2: CGPoint(x: inset, y: bottom - radius * 0.45))
            path.closeSubpath()
            return path
        }
    }

    struct WalletCardStackStitchShape: Shape {
        func path(in rect: CGRect) -> Path {
            let w = rect.width
            let h = rect.height
            let i = CGFloat(WalletCardStackTheme.wallet.stitchInset)
            let side = CGFloat(WalletCardStackTheme.wallet.pocketSide) + i
            let dip = CGFloat(WalletCardStackTheme.wallet.pocketDip) + i
            let left = CGFloat(WalletCardStackTheme.wallet.pocketInset) + i + 6
            let right = w - CGFloat(WalletCardStackTheme.wallet.pocketInset) - i - 6
            var path = Path()
            path.move(to: CGPoint(x: left, y: side))
            path.addCurve(to: CGPoint(x: w / 2, y: dip), control1: CGPoint(x: w * 0.3, y: side), control2: CGPoint(x: w * 0.36, y: dip))
            path.addCurve(to: CGPoint(x: right, y: side), control1: CGPoint(x: w * 0.64, y: dip), control2: CGPoint(x: w * 0.7, y: side))

            let bottomLeft = CGFloat(WalletCardStackTheme.wallet.pocketInset) + i
            let bottomRight = w - CGFloat(WalletCardStackTheme.wallet.pocketInset) - i
            let bottom = h - CGFloat(WalletCardStackTheme.wallet.pocketBottom) - i
            let radius = CGFloat(WalletCardStackTheme.wallet.pocketRadius) - i
            path.move(to: CGPoint(x: bottomLeft, y: bottom - radius - CGFloat(WalletCardStackTheme.wallet.stitchRise)))
            path.addLine(to: CGPoint(x: bottomLeft, y: bottom - radius))
            path.addCurve(to: CGPoint(x: bottomLeft + radius, y: bottom), control1: CGPoint(x: bottomLeft, y: bottom - radius * 0.45), control2: CGPoint(x: bottomLeft + radius * 0.45, y: bottom))
            path.addLine(to: CGPoint(x: bottomRight - radius, y: bottom))
            path.addCurve(to: CGPoint(x: bottomRight, y: bottom - radius), control1: CGPoint(x: bottomRight - radius * 0.45, y: bottom), control2: CGPoint(x: bottomRight, y: bottom - radius * 0.45))
            path.addLine(to: CGPoint(x: bottomRight, y: bottom - radius - CGFloat(WalletCardStackTheme.wallet.stitchRise)))
            return path
        }
    }

    struct WalletCardStackRimShape: Shape {
        func path(in rect: CGRect) -> Path {
            let w = rect.width
            let side = CGFloat(WalletCardStackTheme.wallet.pocketSide)
            let dip = CGFloat(WalletCardStackTheme.wallet.pocketDip)
            let radius = CGFloat(WalletCardStackTheme.wallet.cornerRadius)
            let inset = CGFloat(WalletCardStackTheme.wallet.pocketInset)
            var path = Path()
            path.move(to: CGPoint(x: inset, y: side + radius))
            path.addQuadCurve(to: CGPoint(x: inset + radius, y: side), control: CGPoint(x: inset, y: side))
            path.addCurve(to: CGPoint(x: w / 2, y: dip), control1: CGPoint(x: w * 0.3, y: side), control2: CGPoint(x: w * 0.36, y: dip))
            path.addCurve(to: CGPoint(x: w - inset - radius, y: side), control1: CGPoint(x: w * 0.64, y: dip), control2: CGPoint(x: w * 0.7, y: side))
            path.addQuadCurve(to: CGPoint(x: w - inset, y: side + radius), control: CGPoint(x: w - inset, y: side))
            return path
        }
    }
}

// MARK: - Glyphs and button style

extension WalletCardStack {
    enum WalletCardStackGlyph {
        static let menu = path(base: 22) { path in
            path.move(to: CGPoint(x: 2, y: 5)); path.addLine(to: CGPoint(x: 12, y: 5))
            path.move(to: CGPoint(x: 2, y: 11)); path.addLine(to: CGPoint(x: 21, y: 11))
            path.move(to: CGPoint(x: 2, y: 17)); path.addLine(to: CGPoint(x: 15, y: 17))
        }
        static let plus = path(base: 22) { path in
            path.move(to: CGPoint(x: 11, y: 2)); path.addLine(to: CGPoint(x: 11, y: 20))
            path.move(to: CGPoint(x: 2, y: 11)); path.addLine(to: CGPoint(x: 20, y: 11))
        }
        static let swap = path(base: 16) { path in
            path.move(to: CGPoint(x: 3, y: 6)); path.addLine(to: CGPoint(x: 13, y: 6)); path.addLine(to: CGPoint(x: 10, y: 3))
            path.move(to: CGPoint(x: 13, y: 10)); path.addLine(to: CGPoint(x: 3, y: 10)); path.addLine(to: CGPoint(x: 6, y: 13))
        }
        static let home = path(base: 26) { path in
            path.move(to: CGPoint(x: 4, y: 11.5)); path.addLine(to: CGPoint(x: 13, y: 4)); path.addLine(to: CGPoint(x: 22, y: 11.5)); path.addLine(to: CGPoint(x: 22, y: 20.5))
            path.addCurve(to: CGPoint(x: 20, y: 22.5), control1: CGPoint(x: 22, y: 21.6), control2: CGPoint(x: 21.1, y: 22.5)); path.addLine(to: CGPoint(x: 6, y: 22.5))
            path.addCurve(to: CGPoint(x: 4, y: 20.5), control1: CGPoint(x: 4.9, y: 22.5), control2: CGPoint(x: 4, y: 21.6)); path.closeSubpath()
        }
        static let chart = path(base: 26) { path in
            path.move(to: CGPoint(x: 3, y: 3)); path.addLine(to: CGPoint(x: 3, y: 22.5)); path.addLine(to: CGPoint(x: 23.5, y: 22.5))
            path.move(to: CGPoint(x: 7, y: 17)); path.addLine(to: CGPoint(x: 11.5, y: 11.5)); path.addLine(to: CGPoint(x: 15, y: 15)); path.addLine(to: CGPoint(x: 22.5, y: 8))
        }
        static let pulse = path(base: 26) { path in
            path.move(to: CGPoint(x: 2.5, y: 14)); path.addLine(to: CGPoint(x: 7, y: 14)); path.addLine(to: CGPoint(x: 9.5, y: 9)); path.addLine(to: CGPoint(x: 12.5, y: 19)); path.addLine(to: CGPoint(x: 15.5, y: 7)); path.addLine(to: CGPoint(x: 18, y: 14)); path.addLine(to: CGPoint(x: 23.5, y: 14))
        }
        static let card = path(base: 26) { path in
            path.addRoundedRect(in: CGRect(x: 2.5, y: 5, width: 21, height: 16), cornerSize: CGSize(width: 3, height: 3))
            path.move(to: CGPoint(x: 2.5, y: 10)); path.addLine(to: CGPoint(x: 23.5, y: 10)); path.move(to: CGPoint(x: 6, y: 16.5)); path.addLine(to: CGPoint(x: 9, y: 16.5))
        }
        static let scan = path(base: 30) { path in
            path.move(to: CGPoint(x: 4, y: 10)); path.addLine(to: CGPoint(x: 4, y: 4)); path.addLine(to: CGPoint(x: 10, y: 4))
            path.move(to: CGPoint(x: 20, y: 4)); path.addLine(to: CGPoint(x: 26, y: 4)); path.addLine(to: CGPoint(x: 26, y: 10))
            path.move(to: CGPoint(x: 26, y: 20)); path.addLine(to: CGPoint(x: 26, y: 26)); path.addLine(to: CGPoint(x: 20, y: 26))
            path.move(to: CGPoint(x: 10, y: 26)); path.addLine(to: CGPoint(x: 4, y: 26)); path.addLine(to: CGPoint(x: 4, y: 20)); path.move(to: CGPoint(x: 4, y: 15)); path.addLine(to: CGPoint(x: 26, y: 15))
        }

        static func eye(crossed: Bool) -> Path {
            path(base: 18) { path in
                path.move(to: CGPoint(x: 1.5, y: 9)); path.addCurve(to: CGPoint(x: 9, y: 3.6), control1: CGPoint(x: 3.6, y: 5.2), control2: CGPoint(x: 6.1, y: 3.6)); path.addCurve(to: CGPoint(x: 16.5, y: 9), control1: CGPoint(x: 11.9, y: 3.6), control2: CGPoint(x: 14.4, y: 5.2)); path.addCurve(to: CGPoint(x: 9, y: 14.4), control1: CGPoint(x: 14.4, y: 12.8), control2: CGPoint(x: 11.9, y: 14.4)); path.addCurve(to: CGPoint(x: 1.5, y: 9), control1: CGPoint(x: 6.1, y: 14.4), control2: CGPoint(x: 3.6, y: 12.8))
                path.move(to: CGPoint(x: 6.6, y: 9)); path.addArc(center: CGPoint(x: 9, y: 9), radius: 2.4, startAngle: .zero, endAngle: .degrees(360), clockwise: false)
                if crossed { path.move(to: CGPoint(x: 2.5, y: 2.5)); path.addLine(to: CGPoint(x: 15.5, y: 15.5)) }
            }
        }

        private static func path(base: CGFloat, _ draw: (inout Path) -> Void) -> Path {
            var path = Path()
            draw(&path)
            _ = base
            return path
        }
    }

    struct WalletCardStackPressStyle: ButtonStyle {
        var animated = true
        let onPress: () -> Void

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? WalletCardStackMotion.pressScale : 1)
                .animation(animated ? WalletCardStackMotion.pressAnimation : nil, value: configuration.isPressed)
                .onChange(of: configuration.isPressed) { _, pressed in
                    if pressed { onPress() }
                }
        }
    }

    struct WalletCardStackTabPressStyle: ButtonStyle {
        let onPress: () -> Void

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .opacity(configuration.isPressed ? 0.6 : 1)
                .onChange(of: configuration.isPressed) { _, pressed in
                    if pressed { onPress() }
                }
        }
    }
}

// MARK: - Data and constants

extension WalletCardStack {
    enum WalletCardStackAsset {
        static func image(_ name: String) -> Image {
#if DEBUG
            guard let image = UIImage(named: name) else {
                assertionFailure("Missing Wallet Card Stack asset: \(name)")
                return Image(systemName: "photo")
            }
            return Image(uiImage: image)
#else
            return Image(name)
#endif
        }
    }

    struct WalletCardStackPerson: Identifiable {
        let id: String
        let name: String
        let photo: String
        let tint: UInt32
    }

    struct WalletCardStackPayment: Identifiable {
        let person: WalletCardStackPerson
        let date: String
        let amount: String
        let incoming: Bool
        let slot: Int

        var id: String { "\(person.id)-\(date)" }
    }

    enum WalletCardStackData {
        static let card = WalletCardStackCardData(name: "Rohan Kesari", last4: "5678", network: "ZIVA", valid: "05/29")
        static let balance = WalletCardStackBalanceData(label: "Total Balance", amount: "25,867.40", currency: "SAR")
        static let balanceCharacters = Array(balance.amount.enumerated()).map {
            WalletCardStackBalanceCharacterData(id: $0.offset, value: String($0.element))
        }

        static let warren = WalletCardStackPerson(id: "warren", name: "Warren", photo: "WalletCardStack-warren.jpg", tint: 0xA8E6C6)
        static let edwards = WalletCardStackPerson(id: "edwards", name: "Edwards", photo: "WalletCardStack-edwards.jpg", tint: 0xA7CCF8)
        static let ingrid = WalletCardStackPerson(id: "ingrid", name: "Ingrid", photo: "WalletCardStack-ingrid.jpg", tint: 0xF7DC74)
        static let sofia = WalletCardStackPerson(id: "sofia", name: "Sofia", photo: "WalletCardStack-sofia.jpg", tint: 0xD8C4F4)
        static let quick = [warren, edwards, ingrid, sofia]

        static let latest = [
            WalletCardStackPayment(person: sofia, date: "April 10,2025", amount: "-36.95 SAR", incoming: false, slot: 0),
            WalletCardStackPayment(person: edwards, date: "April 05,2025", amount: "-5.00 SAR", incoming: false, slot: 1),
            WalletCardStackPayment(person: warren, date: "March 25,2025", amount: "+175.75 SAR", incoming: true, slot: 2),
        ]
        static let earlier = [
            WalletCardStackPayment(person: ingrid, date: "March 18,2025", amount: "-120.00 SAR", incoming: false, slot: 0),
            WalletCardStackPayment(person: warren, date: "March 12,2025", amount: "+48.50 SAR", incoming: true, slot: 1),
            WalletCardStackPayment(person: sofia, date: "March 02,2025", amount: "-12.75 SAR", incoming: false, slot: 2),
        ]

        static func quickCenterX(for person: WalletCardStackPerson) -> CGFloat {
            switch person.id {
            case "warren": 139
            case "edwards": 211
            case "ingrid": 283
            default: 355
            }
        }
    }

    struct WalletCardStackCardData {
        let name: String
        let last4: String
        let network: String
        let valid: String
    }

    struct WalletCardStackBalanceData {
        let label: String
        let amount: String
        let currency: String
    }

    struct WalletCardStackBalanceCharacterData: Identifiable {
        let id: Int
        let value: String
    }

    enum WalletCardStackTheme {
        enum color {
            static let screen = Color(hex: 0xF0F0F0)
            static let ink = Color(hex: 0x1D1D1F)
            static let inkSoft = Color(hex: 0x6B6B6F)
            static let muted = Color(hex: 0x8E8E93)
            static let white = Color(hex: 0xFFFFFF)
            static let button = Color(hex: 0xFBFBFB)
            static let tile = Color(hex: 0xE4E4E4)
            static let divider = Color(hex: 0xC4C4C4)
            static let accent = Color(hex: 0x5A46E0)
            static let red = Color(hex: 0xCF2E2E)
            static let green = Color(hex: 0x2E8B3A)
            static let fabTop = Color(hex: 0x7B80FA)
            static let fabBottom = Color(hex: 0x5C4BE3)
        }

        enum rgba {
            static let blackButtonShadow = Color.rgba(0, 0, 0, 0.07)
            static let fabShadow = Color.rgba(92, 75, 227, 0.28)
            static let walletBackBar = Color.rgba(60, 30, 110, 0.32)
            static let walletBackShadow = Color.rgba(60, 30, 110, 0.35)
            static let pocketGlow = Color.rgba(255, 255, 255, 0.13)
            static let stitch = Color.rgba(222, 200, 252, 0.55)
        }

        struct screen {
            static let width: CGFloat = 402
            static let safeTop: CGFloat = 62
        }

        struct header {
            static let y: CGFloat = 102
            static let button: CGFloat = 48
            static let inset: CGFloat = 20
            static let titleY: CGFloat = 93
            static let subtitleY: CGFloat = 113
        }

        struct wallet {
            static let left: CGFloat = 37.5
            static let top: CGFloat = 201
            static let width: CGFloat = 332
            static let height: CGFloat = 195
            static let pad: CGFloat = 24
            static let flapRadius: CGFloat = 26
            static let rimTop: UInt32 = 0xB994E6
            static let flapTop: UInt32 = 0x7C5AB4
            static let flapMid: UInt32 = 0x5A3A90
            static let flapBottom: UInt32 = 0x2A1462
            static let pocketSide: CGFloat = 30
            static let pocketDip: CGFloat = 45
            static let pocketRadius: CGFloat = 34
            static let cornerRadius: CGFloat = 12
            static let flapShort: CGFloat = 24
            static let flapStops: [CGFloat] = [0, 0.1, 0.5, 1]
            static let flapColors: [UInt32] = [0x6E4FA9, 0x61429C, 0x4E3289, 0x3B257A]
            static let pocketStops: [CGFloat] = [0, 0.03, 0.1, 0.17, 0.25, 0.33, 0.4, 0.48, 0.55, 0.63, 0.7, 0.78, 0.86, 1]
            static let pocketColors: [UInt32] = [0x563788, 0x563788, 0xB48DDC, 0xAD85D6, 0x9F79CB, 0x8F6ABE, 0x7E59AC, 0x714EA0, 0x684596, 0x61408F, 0x583A88, 0x4C3180, 0x3F2878, 0x3C2782]
            static let pocketGlow: (cx: CGFloat, cy: CGFloat, r: CGFloat) = (0.35, 0.66, 0.42)
            // Kept for the fidelity matrix: these are the sampled legacy fields in the RN theme.
            static let pocketLeft: UInt32 = 0xA37DCF
            static let pocketMid: UInt32 = 0x8361B4
            static let pocketRight: UInt32 = 0x33207A
            static let pocketFoot: UInt32 = 0x24104F
            static let pocketInset: CGFloat = 2
            static let pocketBottom: CGFloat = 1
            static let stitchInset: CGFloat = 6.5
            static let stitchRise: CGFloat = 6
            static let shadowY: CGFloat = 202
            static let rimTopColor = Color(hex: rimTop)
        }

        struct cards {
            static let back: (x: CGFloat, y: CGFloat, width: CGFloat, height: CGFloat, radius: CGFloat) = (38, -43, 255, 150, 14)
            static let front: (x: CGFloat, y: CGFloat, width: CGFloat, height: CGFloat, radius: CGFloat) = (21, -32, 290, 180, 20)
            static let padX: CGFloat = 18
            static let nameY: CGFloat = -2
            static let numberY: CGFloat = 18
            static let brandY: CGFloat = 2
            static let validY: CGFloat = 22
        }

        struct pocketUI {
            static let labelY: CGFloat = 80
            static let balanceY: CGFloat = 105
            static let pillY: CGFloat = 130.5
            static let pillX: CGFloat = 24
            static let pillW: CGFloat = 142
            static let pillH: CGFloat = 31
            static let iconY: CGFloat = 145.5
            static let icon: CGFloat = 32
            static let iconXs: [CGFloat] = [248, 292]
        }

        struct topUp {
            static let titleY: CGFloat = 431
            static let rowY: CGFloat = 487
            static let avatar: CGFloat = 53
            static let addX: CGFloat = 47
            static let dividerX: CGFloat = 92
            static let xs: [CGFloat] = [139, 211, 283, 355]
            static let labelY: CGFloat = 527
        }

        struct latest {
            static let titleY: CGFloat = 580
            static let top: CGFloat = 615
            static let width: CGFloat = 103
            static let height: CGFloat = 142
            static let radius: CGFloat = 18
            static let xs: [CGFloat] = [25, 149, 273]
            static let avatar: CGFloat = 48
        }

        struct tabBar {
            static let top: CGFloat = 779
            static let iconY: CGFloat = 803
            static let labelY: CGFloat = 827
            static let xs: [CGFloat] = [38, 111, 279, 358]
            static let fab: CGFloat = 72
            static let fabY: CGFloat = 795
        }

        static let flapGradient = LinearGradient(
            gradient: Gradient(stops: zip(wallet.flapStops, wallet.flapColors).map { Gradient.Stop(color: Color(hex: $0.1), location: $0.0) }),
            startPoint: .leading,
            endPoint: .trailing
        )
        static let pocketGradient = LinearGradient(
            gradient: Gradient(stops: zip(wallet.pocketStops, wallet.pocketColors).map { Gradient.Stop(color: Color(hex: $0.1), location: $0.0) }),
            startPoint: .leading,
            endPoint: .trailing
        )
        static func tintColor(for tint: UInt32) -> Color { Color(hex: tint) }
        static let avatarZoom: CGFloat = 1
        static func topOffset(safeTop: CGFloat) -> CGFloat { safeTop > 0 ? safeTop - screen.safeTop : -40 }
    }

    enum WalletCardStackTab: Hashable {
        case home, statistic, activity, card

        static let all: [WalletCardStackTab] = [.home, .statistic, .activity, .card]

        var label: String {
            switch self {
            case .home: "Home"
            case .statistic: "Statistic"
            case .activity, .card: "Activity"
            }
        }

        var active: Bool { self == .home }
        var stroke: CGFloat { self == .home ? 2.2 : 1.8 }

        var glyph: Path {
            switch self {
            case .home: WalletCardStackGlyph.home
            case .statistic: WalletCardStackGlyph.chart
            case .activity: WalletCardStackGlyph.pulse
            case .card: WalletCardStackGlyph.card
            }
        }
    }
}

extension WalletCardStack {
    enum WalletCardStackMotion {
        static let pullLift = 44.0
        static let backLift = 7.0
        static let tilt = -1.8
        static let pullLimit = 62.0
        static let tapLift = 38.0

        static let springPullMS = 400.0
        static let springPullDamping = 1.0
        static let springDropMS = 400.0
        static let springDropDamping = 0.8
        static let springSquishMS = 400.0
        static let springSquishDamping = 0.8
        static let pressScale: CGFloat = 0.97
        static let pressInMS = 120.0
        static let tapHitSlop = 8.0
        static let pressRetention = 16.0
        static let swapTurnMS = 250.0
        static let squishScaleY = 0.982
        static let squishScaleX = 1.008
        static let hideCharStaggerMS = 30.0
        static let hideCharDurationMS = 200.0
        static let hideRise = 7.0
        static let fadeMS = 180.0
        static let LOOP_MS = 6_000.0
        static let RESUME_AFTER_MS = 3_500.0
        static let anticMS = 400.0
        static let drawMS = 600.0
        static let swayFromMS = 900.0
        static let swayToMS = 3_700.0
        static let fallMS = 3_800.0
        static let impactMS = 4_160.0
        static let loopDip = 4.0
        static let loopLean = -2.2
        static let loopSway = 7.0
        static let backShare = 0.12
        static let backLagMS = 60.0
        static let drawResponse = 0.55
        static let drawDamping = 0.8
        static let impactVelocity = -160.0
        static let impactResponse = 0.42
        static let impactDamping = 0.55
        static let squishVelocity = 30.0
        static let loopSquishResponse = 0.3
        static let loopSquishDamping = 0.5
        static let reducedHideMS = 1_500.0
        static let reducedShowMS = 4_000.0
        static let turnPerDX = 1.0 / 6.0
        static let turnMax = 10.0
        static let turnPerspective = 800.0
        static let sheenOpacity = 0.32
        static let sheenTravel = 0.45
        static let cardShadowOpacity = 0.32

        static let loop = LOOP_MS / 1_000
        static let resumeAfterSeconds = RESUME_AFTER_MS / 1_000
        static let resumeRestSeconds = 0.9
        static let manualSettleSeconds = 1.2
        static let springPullSeconds = springPullMS / 1_000
        static let springDropSeconds = springDropMS / 1_000
        static let springSquishSeconds = springSquishMS / 1_000
        static let swapTurnSeconds = swapTurnMS / 1_000
        static let pressAnimation = Animation.timingCurve(0.23, 1, 0.32, 1, duration: pressInMS / 1_000)
        static let touchTop: CGFloat = -50
        static let tapDropSeconds = 0.52
        static let swapDip = 9.0
        static let swapDipSeconds = 0.13
        static let swapSquish = 0.6
        static let swapSquishInSeconds = 0.11
        static let squishDelay = 0.11
        static let squishInSeconds = 0.09
        static let hideSpanMS = hideCharStaggerMS * 8 + hideCharDurationMS
        static let hideSpanSeconds = hideSpanMS / 1_000
        static let hideCharDurationSeconds = hideCharDurationMS
        static let fadeSeconds = fadeMS / 1_000
        static let linear: (Double) -> Double = { $0 }
        static let easeOut: (Double) -> Double = { WalletCardStackMotion.cubicBezier($0, 0.23, 1, 0.32, 1) }
        static let easeInOut: (Double) -> Double = { WalletCardStackMotion.cubicBezier($0, 0.77, 0, 0.175, 1) }

        static func springProgress(elapsed: Double, duration: Double, damping: Double) -> Double {
            guard elapsed > 0 else { return 0 }
            guard elapsed < duration else { return 1 }
            let omega = 12.0 / duration
            if damping < 1 {
                let damped = omega * sqrt(max(0, 1 - damping * damping))
                return 1 - exp(-damping * omega * elapsed) *
                    (cos(damped * elapsed) + damping * omega / max(damped, 0.0001) * sin(damped * elapsed))
            }
            return 1 - exp(-omega * elapsed) * (1 + omega * elapsed)
        }

        static func rubber(_ distance: Double, limit: Double) -> Double {
            let sign = distance < 0 ? -1.0 : 1.0
            let amount = abs(distance)
            return sign * limit * (1 - 1 / (amount / limit + 1))
        }

        static func wrap(_ milliseconds: Double) -> Double {
            ((milliseconds.truncatingRemainder(dividingBy: LOOP_MS)) + LOOP_MS)
                .truncatingRemainder(dividingBy: LOOP_MS)
        }

        static func loopLift(at input: Double) -> Double {
            let time = wrap(input)
            if time < anticMS { return 0 }
            if time < drawMS {
                return -loopDip * cubicBezier(
                    (time - anticMS) / (drawMS - anticMS),
                    0.77, 0, 0.175, 1
                )
            }
            func drawn(_ value: Double) -> Double {
                pullLift + springAt(
                    elapsed: (value - drawMS) / 1_000,
                    displacement: -loopDip - pullLift,
                    velocity: 0,
                    response: drawResponse,
                    damping: drawDamping
                )
            }
            if time < fallMS { return drawn(time) }
            if time < impactMS {
                let progress = (time - fallMS) / (impactMS - fallMS)
                return drawn(fallMS) * (1 - progress * progress)
            }
            return springAt(
                elapsed: (time - impactMS) / 1_000,
                displacement: 0,
                velocity: impactVelocity,
                response: impactResponse,
                damping: impactDamping
            )
        }

        static func loopTurn(at input: Double) -> Double {
            let time = wrap(input)
            guard time > swayFromMS, time < swayToMS else { return 0 }
            return loopSway * sin(2 * .pi * (time - swayFromMS) / (swayToMS - swayFromMS))
        }

        static func loopSquish(at input: Double, lift: Double) -> Double {
            let time = wrap(input)
            if time < drawMS {
                return 0.3 * max(0, -lift / loopDip)
            }
            guard time > impactMS else { return 0 }
            return max(-0.4, springAt(
                elapsed: (time - impactMS) / 1_000,
                displacement: 0,
                velocity: squishVelocity,
                response: loopSquishResponse,
                damping: loopSquishDamping
            ))
        }

        static func reducedHidden(at input: Double) -> Double {
            let time = wrap(input)
            func fade(_ start: Double) -> Double {
                min(1, max(0, (time - start) / fadeMS))
            }
            return fade(reducedHideMS) - fade(reducedShowMS)
        }

        private static func springAt(
            elapsed: Double,
            displacement: Double,
            velocity: Double,
            response: Double,
            damping: Double
        ) -> Double {
            guard elapsed > 0 else { return displacement }
            let omega = (2 * Double.pi) / response
            let envelope = exp(-damping * omega * elapsed)
            if damping >= 1 {
                return envelope * (displacement + (velocity + omega * displacement) * elapsed)
            }
            let damped = omega * sqrt(1 - damping * damping)
            return envelope * (
                displacement * cos(damped * elapsed)
                    + ((velocity + damping * omega * displacement) / damped) * sin(damped * elapsed)
            )
        }

        private static func cubicBezier(_ t: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
            let clamped = min(max(t, 0), 1)
            var x = clamped
            for _ in 0..<8 {
                let oneMinus = 1 - x
                let estimate = 3 * oneMinus * oneMinus * x * x1 + 3 * oneMinus * x * x * x2 + x * x * x
                let derivative = 3 * oneMinus * oneMinus * x1 + 6 * oneMinus * x * (x2 - x1) + 3 * x * x * (1 - x2)
                guard abs(derivative) > 0.0001 else { break }
                x -= (estimate - clamped) / derivative
                x = min(max(x, 0), 1)
            }
            let oneMinus = 1 - x
            return 3 * oneMinus * oneMinus * x * y1 + 3 * oneMinus * x * x * y2 + x * x * x
        }
    }
}
