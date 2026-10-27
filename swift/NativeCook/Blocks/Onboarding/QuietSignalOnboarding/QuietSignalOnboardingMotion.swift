import SwiftUI
import UIKit

// MARK: - Timeline constants and curves

/// The tour's clock and curves: the constants at the top of QuietSignalOnboarding.tsx and `motion.ts`.
enum QuietSignalOnboardingMotion {
    /// `LOOP` (QuietSignalOnboarding.tsx:16): one pass of the five pages, in seconds.
    static let loop = 34.0
    /// `ANCHORS`: where each page rests. A touch settles the clock on the anchor of the page it lands on.
    static let anchors: [Double] = [3, 11, 18, 25, 31.9]
    /// `STARTS`: when each page's scene starts to appear.
    static let starts: [Double] = [0, 9, 15.25, 22, 28.25]
    /// `ENDS`: when each page's scene starts to leave.
    static let ends: [Double] = [8.15, 14.4, 21.4, 27.5, 32.5]
    /// `FADE_ENDS`: when each page's scene is gone.
    static let fadeEnds: [Double] = [8.5, 14.8, 21.8, 27.9, 33.25]

    /// `Easing.bezierFn(.77, 0, .175, 1)` of `motion.ts`.
    private static let moveCurve = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.77, y: 0),
        endControlPoint: UnitPoint(x: 0.175, y: 1)
    )
    /// `Easing.bezierFn(.23, 1, .32, 1)` of `motion.ts`.
    private static let enterCurve = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.23, y: 1),
        endControlPoint: UnitPoint(x: 0.32, y: 1)
    )

    /// The input stops of the scene fade-out `interpolate` (QuietSignalOnboarding.tsx:47).
    private static let fadeInputs: [Double] = [0, 0.0486, 0.2857, 0.523, 0.762, 1]
    /// Its output stops.
    private static let fadeOutputs: [Double] = [1, 0.86, 0.4, 0.17, 0.06, 0]

    /// `clamp` of `motion.ts`.
    static func clamp(_ value: Double, _ low: Double = 0, _ high: Double = 1) -> Double {
        min(high, max(low, value))
    }

    /// `segment` of `motion.ts`: how far `time` is from `start` to `end`, 0 to 1.
    static func segment(_ time: Double, _ start: Double, _ end: Double) -> Double {
        clamp((time - start) / (end - start))
    }

    /// `ease` of `motion.ts`.
    static func ease(_ value: Double) -> Double {
        moveCurve.value(at: clamp(value))
    }

    /// `enter` of `motion.ts`.
    static func enter(_ value: Double) -> Double {
        enterCurve.value(at: clamp(value))
    }

    /// `Easing.inOut(Easing.quad)`: Reanimated's default for timing animations.
    static func quadInOut(_ value: Double) -> Double {
        let p = clamp(value)
        return p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
    }

    /// `rubber` of `motion.ts`: the drag resists past either end.
    static func rubber(_ value: Double, max limit: Double) -> Double {
        if value < 0 { return value / (1 + abs(value) * 4) }
        if value > limit { return limit + (value - limit) / (1 + (value - limit) * 4) }
        return value
    }

    /// `pageAt` (QuietSignalOnboarding.tsx:32): the page the loop time belongs to.
    static func pageAt(_ time: Double) -> Int {
        if time >= 33.25 { return 0 }
        if time >= 28 { return 4 }
        if time >= 21.75 { return 3 }
        if time >= 14.75 { return 2 }
        if time >= 8.5 { return 1 }
        return 0
    }

    /// The piecewise-linear fade-out of a scene (`interpolate(..., 'clamp')`).
    static func fadeOut(_ value: Double) -> Double {
        let x = clamp(value)
        for index in 1..<fadeInputs.count where x <= fadeInputs[index] {
            let span = fadeInputs[index] - fadeInputs[index - 1]
            let k = (x - fadeInputs[index - 1]) / span
            return fadeOutputs[index - 1] + (fadeOutputs[index] - fadeOutputs[index - 1]) * k
        }
        return fadeOutputs[fadeOutputs.count - 1]
    }

    /// `sceneStyle` opacity of `Scene` (QuietSignalOnboarding.tsx:45): while a finger or Reduce Motion has the
    /// pages, a scene is as visible as it is near the page position; on the loop it follows the script.
    static func sceneOpacity(index: Int, frame: QuietSignalOnboardingFrame, reduced: Bool) -> Double {
        if frame.rendersManual || reduced {
            return clamp(1 - abs(frame.position - Double(index)) * 2)
        }
        let appear = enter(segment(frame.time, starts[index], starts[index] + 1.3))
        let leave = fadeOut(segment(frame.time, ends[index], fadeEnds[index]))
        return appear * leave
    }

    /// `shown` of `copyStyle` (QuietSignalOnboarding.tsx:56): how far the words have come out of the dark.
    static func copyProgress(index: Int, frame: QuietSignalOnboardingFrame, reduced: Bool) -> Double {
        if frame.rendersManual || reduced { return 1 }
        if index == 4 { return ease(segment(frame.time, 29.15, 30.1)) }
        return enter(segment(frame.time, starts[index] + 0.45, starts[index] + 1.8))
    }
}

// MARK: - Frame

/// Everything the screen reads for one frame; each field is a function of the loop time or of the state the
/// conductor holds.
struct QuietSignalOnboardingFrame {
    /// The diagrams' clock (`time` of `usePlayback`, or the pinned preview phase).
    var time: Double
    /// `position`: the page position, whole on the loop and fractional while a finger or a spring moves it.
    var position: Double
    /// `renderManual`: the pages follow `position` instead of the script.
    var rendersManual: Bool
    /// `scenePresence`: the opacity of the whole page stack (dips while a button changes the page).
    var presence: Double
    /// The page `position` rounds to (`announcePage`).
    var page: Int
}

// MARK: - Conductor

/// `usePlayback` plus the manual page state of `QuietSignalOnboarding`: one loop clock that a touch pauses, a
/// page position a finger or a button takes over, and the quiet period after which the loop resumes. It holds
/// plain state and is read by date from the screen's `TimelineView`, so a frame is a pure function of that
/// state and the frame's date.
final class QuietSignalOnboardingConductor {
    private typealias M = QuietSignalOnboardingMotion

    // MARK: Configuration

    /// The component's `autoPlay`.
    let autoPlay: Bool
    /// `previewPhase`: the loop time pinned by `previewTime`, or nil. A pinned tour ignores every touch.
    let previewPhase: Double?
    /// Called after a change the screen cannot see by itself, because its timeline may be paused.
    var onChange: () -> Void = {}

    var previewing: Bool { previewPhase != nil }
    private(set) var reduced = false
    private(set) var foreground = true

    // MARK: Clock (usePlayback)

    /// `playing` state: the loop is allowed to run.
    private var playing: Bool
    /// The loop clock is advancing (`playing && foreground && !reduced && !previewing`).
    private var running = false
    /// The loop time when `runStart` began, or the frozen time while not running.
    private var base: Double
    private var runStart = Date()
    private var started = false
    /// `settledTime`: the time Reduce Motion shows.
    private var settledTime = QuietSignalOnboardingMotion.anchors[0]
    /// `stopped`: the session screen owns the app, the loop stays off.
    private var stopped = false
    /// `readingTouches`, `readingDrag`, `readingMomentum`, `gestureHeld`: who has a hand on the page.
    private var touching = false
    private var dragging = false
    private var coasting = false
    private var held = false
    /// `timer`: the two quiet seconds before the loop resumes.
    private var quietTimer: DispatchWorkItem?

    // MARK: Manual page position

    /// `manualMode`: the pages follow `manual` instead of the loop time.
    private(set) var manualMode: Bool
    private var manualFrom = 0.0
    private var manualTo = 0.0
    private var manualVelocity = 0.0
    /// Non-nil while the page spring runs.
    private var manualStart: Date?
    /// `startPage`: the position where the current drag began.
    private var startPage = 0.0
    private var panning = false
    /// `withSpring(target, { duration: 400, dampingRatio: .8 })`. Reanimated 4.5.1 reads `duration` as perceptual
    /// and solves the stiffness so the spring settles after 1.5 x duration (mass 1, energy threshold 6e-9): that
    /// is 653.4 here, with damping = 2 x ratio x sqrt(stiffness).
    private static let pageStiffness = 653.4
    private static let pageDampingRatio = 0.8
    private static let pageSpring = Spring(
        mass: 1,
        stiffness: pageStiffness,
        damping: 2 * pageDampingRatio * pageStiffness.squareRoot(),
        allowOverDamping: false
    )

    // MARK: Scene presence

    /// `scenePresence`: a timing animation, with the page change that waits for the fade-out to end.
    private var presenceFrom = 1.0
    private var presenceTo = 1.0
    private var presenceStart = Date.distantPast
    private var presenceDuration = 0.0
    private var presenceNext: Int?

    // MARK: Haptics

    private let selection = UISelectionFeedbackGenerator()
    private let notification = UINotificationFeedbackGenerator()

    init(autoPlay: Bool, previewTime: Double?) {
        let phase: Double?
        if let previewTime, previewTime.isFinite {
            let remainder = previewTime.truncatingRemainder(dividingBy: M.loop)
            phase = remainder < 0 ? remainder + M.loop : remainder
        } else {
            phase = nil
        }
        self.autoPlay = autoPlay
        previewPhase = phase
        playing = autoPlay
        base = phase ?? (autoPlay ? 0 : M.anchors[0])
        manualMode = !(autoPlay || phase != nil)
        running = autoPlay && phase == nil
    }

    deinit {
        quietTimer?.cancel()
    }

    /// `useState(() => previewPhase === undefined ? 0 : Math.round(pageAt(previewPhase)))`.
    var initialPage: Int {
        previewPhase.map { M.pageAt($0) } ?? 0
    }

    // MARK: Reading the state

    /// The loop clock at a date.
    private func currentTime(at date: Date) -> Double {
        if let previewPhase { return previewPhase }
        guard running else { return base }
        let elapsed = max(0, date.timeIntervalSince(runStart))
        return (base + elapsed).truncatingRemainder(dividingBy: M.loop)
    }

    private func manualValue(at date: Date) -> Double {
        guard let manualStart else { return manualTo }
        let elapsed = max(0, date.timeIntervalSince(manualStart))
        if elapsed > 3 { return manualTo }
        let travelled = Self.pageSpring.value(target: manualTo - manualFrom, initialVelocity: manualVelocity, time: elapsed)
        return manualFrom + travelled
    }

    private func presenceValue(at date: Date) -> Double {
        guard presenceDuration > 0 else { return presenceTo }
        let progress = M.clamp(date.timeIntervalSince(presenceStart) / presenceDuration)
        return presenceFrom + (presenceTo - presenceFrom) * M.enter(progress)
    }

    /// `position`: the page position the screen shows now.
    private func position(at date: Date) -> Double {
        if !previewing && manualMode { return manualValue(at: date) }
        return Double(M.pageAt(currentTime(at: date)))
    }

    /// The frame to draw at a date. Called by the screen's timeline on every update.
    func frame(at date: Date) -> QuietSignalOnboardingFrame {
        advance(to: date)
        let time = currentTime(at: date)
        let rendersManual = !previewing && manualMode
        let position = rendersManual ? manualValue(at: date) : Double(M.pageAt(time))
        let rounded = Int(M.clamp(floor(position + 0.5), 0, 4))
        return QuietSignalOnboardingFrame(
            time: time,
            position: position,
            rendersManual: rendersManual,
            presence: previewing ? 1 : presenceValue(at: date),
            page: rounded
        )
    }

    /// The end of the fade-out of a page change: the page jumps under the dark and fades back in (180 ms).
    private func advance(to date: Date) {
        guard let target = presenceNext, date.timeIntervalSince(presenceStart) >= presenceDuration else { return }
        presenceNext = nil
        setManual(Double(target))
        presenceStart = presenceStart.addingTimeInterval(presenceDuration)
        presenceFrom = 0
        presenceTo = 1
        presenceDuration = 0.18
    }

    // MARK: Environment

    /// Reduce Motion and the app's state, read by the screen on every update. Quiet: it only changes what the
    /// clock holds, so it is safe to call while the screen's body runs.
    @discardableResult
    func syncEnvironment(reduced newReduced: Bool, foreground newForeground: Bool) -> Bool {
        if newForeground != foreground {
            foreground = newForeground
            clearQuietTimer()
            if newForeground {
                if !stopped && !hasOwnership { playing = autoPlay }
            } else {
                clearOwnership()
            }
            updateRunning()
        }
        if newReduced != reduced {
            let restoredMotion = reduced && !newReduced
            reduced = newReduced
            updateRunning()
            if reduced && !previewing {
                clearQuietTimer()
                base = settledTime
            }
            if restoredMotion && autoPlay && foreground && !previewing && !stopped && !hasOwnership {
                // Resume from the selected phase only after the usual reading quiet period.
                settle(currentTime(at: Date()))
            }
        }
        return reduced
    }

    /// The loop starts when the screen first appears.
    func appeared() {
        guard !started else { return }
        started = true
        if running { runStart = Date() }
    }

    // MARK: Playback (usePlayback)

    private var hasOwnership: Bool {
        touching || dragging || coasting || held
    }

    private func clearOwnership() {
        touching = false
        dragging = false
        coasting = false
        held = false
    }

    private func clearQuietTimer() {
        quietTimer?.cancel()
        quietTimer = nil
    }

    /// Starts or stops the clock when its conditions change. A clock that starts continues from the time it holds.
    private func updateRunning(_ now: Date = Date()) {
        let shouldRun = playing && foreground && !reduced && !previewing
        guard shouldRun != running else { return }
        if shouldRun {
            running = true
            runStart = now
            // `useEffect(() => { if (playing) manualMode.set(false) })`: the script takes the pages back.
            manualMode = false
        } else {
            base = currentTime(at: now)
            running = false
        }
    }

    /// `pause`.
    private func pause(_ now: Date = Date()) {
        clearQuietTimer()
        playing = false
        updateRunning(now)
    }

    /// `settle(anchor)`: pause on an anchor and, after two quiet seconds, run again.
    private func settle(_ anchor: Double, _ now: Date = Date()) {
        pause(now)
        if previewing { return }
        settledTime = anchor
        base = anchor
        if autoPlay && foreground && !reduced && !stopped && !hasOwnership {
            let resume = DispatchWorkItem { [weak self] in
                guard let self else { return }
                self.quietTimer = nil
                if !self.stopped && !self.hasOwnership && self.foreground {
                    self.playing = true
                    self.updateRunning()
                }
            }
            quietTimer = resume
            DispatchQueue.main.asyncAfter(deadline: .now() + 2, execute: resume)
        }
    }

    /// `landed`: a page was chosen; the clock settles on its anchor and a selection tick answers.
    private func landed(_ target: Int, _ now: Date) {
        settle(M.anchors[target], now)
        selection.selectionChanged()
    }

    /// `remember`: the page the screen announced is where Reduce Motion rests.
    func remember(_ anchor: Double) {
        settledTime = anchor
    }

    /// `stop`: the session screen takes over, the loop stays off.
    func stop() {
        stopped = true
        clearOwnership()
        pause()
    }

    /// `restart`: the introduction is back; settle on `anchor` and run again after the quiet period.
    private func restart(_ anchor: Double) {
        stopped = false
        settle(anchor)
    }

    // MARK: Reading touches (readingHandlers)

    /// A finger came down anywhere on the scroll view (`onTouchStart`).
    func readingTouchBegan() {
        guard !previewing else { return }
        touching = true
        pause()
    }

    /// The finger left (`onTouchEnd`, `onTouchCancel`).
    func readingTouchEnded() {
        guard !previewing else { return }
        touching = false
        releaseReading()
    }

    func readingDragBegan() {
        guard !previewing else { return }
        dragging = true
        pause()
    }

    func readingDragEnded() {
        guard !previewing else { return }
        dragging = false
        releaseReading()
    }

    func readingMomentumBegan() {
        guard !previewing else { return }
        coasting = true
        pause()
    }

    func readingMomentumEnded() {
        guard !previewing else { return }
        coasting = false
        releaseReading()
    }

    /// `releaseReading`: reading never seeks to a page anchor or emits a landing haptic.
    private func releaseReading() {
        if !stopped && !hasOwnership { settle(currentTime(at: Date())) }
    }

    // MARK: Buttons (controls)

    /// `onPressIn: pause`.
    func pressBegan() {
        pause()
    }

    /// `onPressOut: () => settle(ANCHORS[page])`.
    func pressEnded(page: Int) {
        settle(M.anchors[min(4, max(0, page))])
    }

    // MARK: Pages

    private func setManual(_ value: Double) {
        manualFrom = value
        manualTo = value
        manualVelocity = 0
        manualStart = nil
    }

    private func setPresence(_ value: Double) {
        presenceFrom = value
        presenceTo = value
        presenceDuration = 0
        presenceNext = nil
    }

    /// `go`: a button or the accessibility action changes the page. The stack fades out in 100 ms, the page
    /// changes under the dark and the stack fades back in over 180 ms; under Reduce Motion it just changes.
    func go(to next: Int) {
        guard !previewing else { return }
        let now = Date()
        let target = min(4, max(0, next))
        pause(now)
        setManual(position(at: now))
        manualMode = true
        let current = presenceValue(at: now)
        if reduced {
            setManual(Double(target))
            setPresence(1)
        } else {
            presenceNext = target
            presenceFrom = current
            presenceTo = 0
            presenceStart = now
            presenceDuration = 0.1
        }
        landed(target, now)
        onChange()
    }

    // MARK: Pan (Gesture.Pan)

    /// `onStart`: a horizontal drag took the pages.
    func panBegan() {
        guard !previewing else { return }
        let now = Date()
        panning = true
        setPresence(1)
        pause(now)
        held = true
        let here = position(at: now)
        setManual(here)
        startPage = here
        manualMode = true
        onChange()
    }

    /// `onUpdate`: the pages follow the finger, with resistance past the ends.
    func panChanged(translation: Double, width: Double) {
        guard panning, !reduced, width > 0 else { return }
        // The timeline runs while a finger moves the pages, so no redraw needs asking for.
        setManual(M.rubber(startPage - translation / width, max: 4))
    }

    /// `onFinalize`: past 18% of the width or 450 pt/s the drag changes the page; the position springs there.
    func panEnded(translation: Double, velocity: Double, success: Bool, width: Double) {
        guard panning, width > 0 else { return }
        let now = Date()
        panning = false
        held = false
        let moved = success && (abs(translation) > width * 0.18 || abs(velocity) > 450)
        let origin = floor(startPage + 0.5)
        let direction: Double = (abs(velocity) > 450 ? velocity : translation) < 0 ? 1 : -1
        let target = Int(M.clamp(origin + (moved ? direction : 0), 0, 4))
        if reduced {
            setManual(Double(target))
        } else {
            manualFrom = manualValue(at: now)
            manualTo = Double(target)
            manualVelocity = -velocity / width
            manualStart = now
        }
        if moved && Double(target) != origin {
            landed(target, now)
        } else {
            settle(M.anchors[target], now)
        }
        onChange()
    }

    // MARK: Session

    /// `Haptics.selectionAsync()` for a chosen session mode.
    func selectionTick() {
        selection.selectionChanged()
    }

    /// The last Continue: the introduction stops for good and the screen confirms with a success tick.
    func beginSession() {
        stop()
        notification.notificationOccurred(.success)
    }

    /// "Back to the introduction": the last page is shown, and the loop resumes after the quiet period.
    func returnToIntroduction() {
        setManual(4)
        manualMode = true
        restart(M.anchors[4])
        onChange()
    }
}
