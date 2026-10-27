import Observation
import SwiftUI
import UIKit

/// What one rendered frame needs, worked out from the loop time (the derived values of
/// `NocturneProgramCarousel.tsx`: `position`, `programVisible` and the "controlled" test that the
/// welcome, program and sheen styles share).
struct NocturneProgramCarouselFrame {
    /// The displayed loop time in seconds (`displayedTime`): the previewed phase, or the clock.
    let t: Double
    /// `position`: the page position, fractional while a swipe is under way.
    let position: Double
    /// The page nearest to `position`, 0 to 2.
    let page: Int
    /// `reduced`.
    let reduced: Bool
    /// `timelineDriven`: the timeline (autoPlay or a preview) drives the intro.
    let timelineDriven: Bool
    /// `!timelineDriven || (!previewing && touching) || reduced`: the welcome is gone and the
    /// program is fully shown, whatever the loop time is.
    let held: Bool
    /// `programVisible`: the program takes touches and is read by VoiceOver.
    let programVisible: Bool
}

/// Owns the loop clock, the manual page position, the quiet-period timers and the touch rules of
/// the Nocturne Program Carousel.
///
/// It is `usePlayback` of `motion.ts` plus the handlers of `NocturneProgramCarousel.tsx`, written as
/// one object. Nothing here runs per frame: the clock and the spring are worked out from start
/// dates, so the view reads `frame(at:reduced:)` from its `TimelineView` and every frame is a
/// function of the loop time. `previewTime` freezes that time and switches every touch-driven
/// change off.
@MainActor
@Observable
final class NocturneProgramCarouselEngine {
    private typealias M = NocturneProgramCarouselMotion

    // MARK: State the view reads

    /// `plan`: the evening plan replaces the tour.
    private(set) var plan = false
    /// True while something moves (the clock, a spring or a finger), so the timeline has to tick.
    private(set) var moving = false

    /// A previewed phase freezes the clock (`previewing`).
    var previewing: Bool { previewPhase != nil }

    /// `timelineDriven = autoPlay || previewing`.
    var timelineDriven: Bool { autoPlay || previewing }

    /// `page` state: the page announced by the position reaction (`announcePage`).
    @ObservationIgnored private(set) var page = 0

    // MARK: Inputs

    @ObservationIgnored private var autoPlay: Bool
    @ObservationIgnored private var previewPhase: Double?
    @ObservationIgnored private var onStartPlan: (() -> Void)?
    @ObservationIgnored private var onPageChange: ((Int) -> Void)?
    @ObservationIgnored private var reduced: Bool
    @ObservationIgnored private var foreground = true

    // MARK: Clock (`time`, `epoch`, `enabled`)

    private enum PlaybackClock {
        /// The clock is stopped at this loop time.
        case still(Double)
        /// The clock runs: loop time `from` at `origin`, one second per second, wrapping at the loop.
        case loop(origin: Date, from: Double)
    }

    private var clock: PlaybackClock

    /// The loop time at `date`, in seconds (`time`).
    private func time(at date: Date) -> Double {
        switch clock {
        case .still(let value):
            return value
        case .loop(let origin, let from):
            let elapsed = max(0, date.timeIntervalSince(origin))
            return (from + elapsed).truncatingRemainder(dividingBy: M.loop)
        }
    }

    private var clockRunning: Bool {
        if case .loop = clock {
            return true
        }
        return false
    }

    // MARK: Manual position (`manual`, `touching`)

    private enum Manual {
        case still(Double)
        /// `withSpring(target, { duration: 400, dampingRatio: .8, velocity })`, started at `start`.
        case spring(from: Double, target: Double, velocity: Double, start: Date)
    }

    private var manual: Manual = .still(0)
    /// `touching`: the finger or a spring owns the position, not the timeline.
    private var touching = false

    @ObservationIgnored private var springActive = false
    @ObservationIgnored private var dragging = false
    @ObservationIgnored private var springToken = 0

    private func manualValue(at date: Date) -> Double {
        switch manual {
        case .still(let value):
            return value
        case .spring(let from, let target, let velocity, let start):
            let elapsed = max(0, date.timeIntervalSince(start))
            return M.springValue(from: from, target: target, velocity: velocity, time: elapsed)
        }
    }

    // MARK: Playback state (`usePlayback`)

    /// `playing` state: whether the loop is meant to run.
    @ObservationIgnored private var playing: Bool
    /// `settledTime`: the time shown under Reduce Motion.
    @ObservationIgnored private var settledTime = M.anchors[0]
    /// `stopped`: the plan was opened; nothing restarts the loop until "Back to the tour".
    @ObservationIgnored private var stopped = false
    @ObservationIgnored private var readingTouches = 0
    @ObservationIgnored private var gestureHeld = false
    @ObservationIgnored private var quietTimer: Task<Void, Never>?

    /// `startPage` and `panning` of the pan gesture.
    @ObservationIgnored private var startPage = 0.0
    @ObservationIgnored private var panning = false
    @ObservationIgnored private var announced: Int?

    @ObservationIgnored private let selectionHaptic = UISelectionFeedbackGenerator()
    @ObservationIgnored private let notificationHaptic = UINotificationFeedbackGenerator()

    // MARK: Setup

    init(autoPlay: Bool, previewTime: Double?, reduced: Bool) {
        let phase = Self.normalized(previewTime)
        self.autoPlay = autoPlay
        self.previewPhase = phase
        self.reduced = reduced
        self.playing = autoPlay
        // `useSharedValue(previewPhase ?? (autoPlay && !reduced ? 0 : restTime))`.
        self.clock = .still(phase ?? (autoPlay && !reduced ? 0 : M.anchors[0]))
        if let phase {
            // `useState(() => previewPhase === undefined ? 0 : Math.round(timelinePage(previewPhase)))`.
            self.page = Int(M.jsRound(M.timelinePage(phase)))
        }
    }

    /// `previewTime % LOOP`, kept positive; a value that is not a number is the start of the loop.
    private static func normalized(_ previewTime: Double?) -> Double? {
        guard let previewTime else { return nil }
        guard previewTime.isFinite else { return 0 }
        let remainder = previewTime.truncatingRemainder(dividingBy: M.loop)
        return remainder < 0 ? remainder + M.loop : remainder
    }

    /// The props: they may change while the screen is up.
    func configure(
        autoPlay: Bool,
        previewTime: Double?,
        onStartPlan: (() -> Void)?,
        onPageChange: ((Int) -> Void)?
    ) {
        let changed = self.autoPlay != autoPlay || previewPhase != Self.normalized(previewTime)
        self.autoPlay = autoPlay
        previewPhase = Self.normalized(previewTime)
        self.onStartPlan = onStartPlan
        self.onPageChange = onPageChange
        if changed {
            // The effect with [autoPlay, previewing]: stop the quiet timer, then follow the props.
            cancelQuietTimer()
            if !stopped && !hasOwnership {
                playing = autoPlay
            }
            reconcile()
        }
    }

    /// The screen appeared: the effects that run on mount.
    func start() {
        cancelQuietTimer()
        if !stopped && !hasOwnership {
            playing = autoPlay
        }
        reconcile()
        if reduced && !previewing {
            clock = .still(settledTime)
            refreshMoving()
        }
    }

    /// The screen went away: `useEffect` cleanups (timers and ownership).
    func teardown() {
        cancelQuietTimer()
        clearOwnership()
        freezeClock()
        cancelSpring()
        dragging = false
        refreshMoving()
    }

    // MARK: Environment (Reduce Motion, foreground)

    /// `useMotionPreference` changed.
    func setReduced(_ value: Bool) {
        guard value != reduced else { return }
        let restoredMotion = reduced && !value
        reduced = value
        // The effect that runs the clock.
        reconcile()
        if value && !previewing {
            // Reduce Motion shows the settled pose: the clock stops at `settledTime`.
            cancelQuietTimer()
            clock = .still(settledTime)
            refreshMoving()
        }
        if restoredMotion && autoPlay && foreground && !previewing && !stopped && !hasOwnership {
            // Resume from the selected phase only after the usual reading quiet period.
            settle(time(at: Date()))
        }
    }

    /// The `AppState` listener: the app went to the background or came back.
    func setForeground(_ active: Bool) {
        guard active != foreground else { return }
        foreground = active
        cancelQuietTimer()
        if active {
            if !stopped && !hasOwnership {
                playing = autoPlay
            }
        } else {
            clearOwnership()
        }
        reconcile()
    }

    // MARK: Frame

    /// The values of one rendered frame at `date`. It also runs the position reaction
    /// (`useAnimatedReaction` on the rounded page) once the page changes.
    func frame(at date: Date, reduced reducedNow: Bool) -> NocturneProgramCarouselFrame {
        let t = previewPhase ?? time(at: date)
        let manualOwns = previewPhase == nil && touching
        let position = manualOwns ? manualValue(at: date) : M.timelinePage(t)
        let page = Int(M.clamp(M.jsRound(position), 0, Double(M.pageCount - 1)))
        announce(page)
        let held = !timelineDriven || manualOwns || reducedNow
        return NocturneProgramCarouselFrame(
            t: t,
            position: position,
            page: page,
            reduced: reducedNow,
            timelineDriven: timelineDriven,
            held: held,
            programVisible: held || M.programShows(t: t)
        )
    }

    /// `announcePage`, scheduled the way `scheduleOnRN` hands a result back from the UI clock.
    private func announce(_ next: Int) {
        guard announced != next else { return }
        announced = next
        Task { @MainActor [weak self] in
            guard let self else { return }
            self.page = next
            self.remember(M.anchors[next])
            self.onPageChange?(next)
        }
    }

    // MARK: Clock control

    private func schedule(after seconds: Double, _ work: @escaping @MainActor () -> Void) -> Task<Void, Never> {
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(seconds))
            guard !Task.isCancelled else { return }
            work()
        }
    }

    private func cancelQuietTimer() {
        quietTimer?.cancel()
        quietTimer = nil
    }

    /// `enabled.set(false)`: the clock stops where it is.
    private func freezeClock() {
        if case .loop = clock {
            clock = .still(time(at: Date()))
        }
    }

    private func refreshMoving() {
        let next = clockRunning || springActive || dragging
        if moving != next {
            moving = next
        }
    }

    /// The effect that starts and stops the clock (`[foreground, playing, previewPhase, previewing,
    /// reduced]`), and with it `if (playing) touching.set(false)`.
    private func reconcile() {
        if let phase = previewPhase {
            cancelQuietTimer()
            clock = .still(phase)
        } else if playing && foreground && !reduced {
            if case .still(let value) = clock {
                clock = .loop(origin: Date(), from: value)
            }
            touching = false
        } else {
            freezeClock()
        }
        refreshMoving()
    }

    private var hasOwnership: Bool {
        readingTouches > 0 || gestureHeld
    }

    private func clearOwnership() {
        readingTouches = 0
        gestureHeld = false
    }

    /// `pause`: stop the loop where it is and cancel the pending restart.
    private func pause() {
        cancelQuietTimer()
        freezeClock()
        playing = false
        refreshMoving()
    }

    /// `settle(anchor)`: stop, show `anchor` as the loop time, and start the loop again from it
    /// after 2 s without a touch.
    private func settle(_ anchor: Double) {
        pause()
        if let phase = previewPhase {
            clock = .still(phase)
            return
        }
        settledTime = anchor
        clock = .still(anchor)
        if autoPlay && foreground && !reduced && !stopped && !hasOwnership {
            quietTimer = schedule(after: 2) { [weak self] in
                guard let self else { return }
                self.quietTimer = nil
                if !self.stopped && !self.hasOwnership && self.foreground {
                    self.playing = true
                    self.reconcile()
                }
            }
        }
    }

    /// `remember`: the loop time to show under Reduce Motion.
    private func remember(_ anchor: Double) {
        settledTime = anchor
    }

    /// `stop`: the plan was opened; the loop stays down until `restart`.
    private func stop() {
        stopped = true
        clearOwnership()
        pause()
    }

    /// `restart`: the tour is back; the loop resumes after the quiet period.
    private func restart(_ anchor: Double) {
        stopped = false
        settle(anchor)
    }

    private func beginGesture() {
        gestureHeld = true
        pause()
    }

    private func endGesture() {
        gestureHeld = false
    }

    /// `releaseReading`: reading never seeks to a page anchor or emits a landing haptic.
    private func releaseReading() {
        if !stopped && !hasOwnership {
            settle(time(at: Date()))
        }
    }

    // MARK: Reading handlers (touches on the page area)

    /// `onTouchStart`: a finger lands in the scroll area; `count` fingers are down.
    func touchStart(count: Int) {
        if previewing {
            return
        }
        readingTouches = max(1, count)
        pause()
    }

    /// `onTouchEnd` and `onTouchCancel`: a finger leaves; `remaining` are still down.
    func touchEnd(remaining: Int) {
        if previewing {
            return
        }
        readingTouches = remaining
        releaseReading()
    }

    // MARK: Controls (`controls` of the component)

    /// `onPressIn: pause`.
    func controlPressIn() {
        pause()
    }

    /// `onPressOut: () => settle(ANCHORS[page])`.
    func controlPressOut() {
        settle(M.anchors[page])
    }

    // MARK: Position

    /// `position.get()` now.
    private func positionNow() -> Double {
        let now = Date()
        if previewPhase == nil && touching {
            return manualValue(at: now)
        }
        return M.timelinePage(previewPhase ?? time(at: now))
    }

    /// `manual.set(withSpring(target, { duration: 400, dampingRatio: .8, velocity }))`.
    private func startSpring(to target: Double, velocity: Double) {
        let now = Date()
        let from = manualValue(at: now)
        manual = .spring(from: from, target: target, velocity: velocity, start: now)
        springToken += 1
        let token = springToken
        springActive = true
        refreshMoving()
        // The spring is over when its envelope has died away; then the timeline may pause.
        Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(M.springSettle + 0.05))
            guard let self, token == self.springToken else { return }
            self.springActive = false
            self.manual = .still(target)
            self.refreshMoving()
        }
    }

    /// `cancelAnimation(manual)`: the value stays where the spring had got to.
    private func cancelSpring() {
        if case .spring = manual {
            manual = .still(manualValue(at: Date()))
        }
        springToken += 1
        springActive = false
    }

    /// `landed`: settle on the page's anchor with a selection haptic.
    private func landed(_ next: Int) {
        settle(M.anchors[next])
        selectionHaptic.selectionChanged()
    }

    /// `go`: an accessibility action moves to `next`.
    func go(_ next: Int) {
        if previewing {
            return
        }
        let target = max(0, min(M.pageCount - 1, next))
        pause()
        manual = .still(positionNow())
        touching = true
        if reduced {
            manual = .still(Double(target))
        } else {
            startSpring(to: Double(target), velocity: 0)
        }
        landed(target)
    }

    // MARK: Pan gesture

    /// `onStart` of the pan: a finger took the page.
    func panStart() {
        if previewing {
            return
        }
        let current = positionNow()
        panning = true
        freezeClock()
        cancelSpring()
        manual = .still(current)
        startPage = current
        touching = true
        dragging = true
        beginGesture()
        refreshMoving()
    }

    /// `onUpdate`: the page follows the finger, with rubber-banding past either end.
    func panUpdate(translationX: Double, stride: Double) {
        guard panning, !reduced, stride > 0 else { return }
        manual = .still(M.rubber(startPage - translationX / stride, max: Double(M.pageCount - 1)))
    }

    /// `onFinalize`: the finger left (`success`) or the gesture was cancelled.
    func panFinish(translationX: Double, velocityX: Double, success: Bool, stride: Double) {
        guard panning, stride > 0 else { return }
        panning = false
        dragging = false
        endGesture()
        let moved = success && (abs(translationX) > stride * 0.18 || abs(velocityX) > 450)
        let heading = abs(velocityX) > 450 ? velocityX : translationX
        let step = moved ? (heading < 0 ? 1 : -1) : 0
        let base = Int(M.jsRound(startPage))
        let target = Int(M.clamp(Double(base + step), 0, Double(M.pageCount - 1)))
        if reduced {
            manual = .still(Double(target))
        } else {
            startSpring(to: Double(target), velocity: -velocityX / stride)
        }
        refreshMoving()
        if moved && target != base {
            landed(target)
        } else {
            settle(M.anchors[target])
        }
    }

    // MARK: Plan

    /// `startPlan`: the Build my evening plan button.
    func startPlan() {
        if previewing {
            return
        }
        stop()
        plan = true
        onStartPlan?()
        notificationHaptic.notificationOccurred(.success)
    }

    /// "Back to the tour": the tour returns on the page it was left on and restarts after the
    /// quiet period.
    func backToTour() {
        plan = false
        touching = true
        manual = .still(Double(page))
        restart(M.anchors[page])
    }
}
