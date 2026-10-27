import Observation
import SwiftUI
import UIKit

/// Owns the loop clock, the position of the row, the springs, the quiet timer and the applied
/// profile of Silver Screen Carousel.
///
/// It mirrors the React Native component: `useSceneClock` (a clock that pauses and keeps its phase),
/// `manualMode` / `manualPosition` / `position` (the row follows the drift, the finger, or a spring),
/// the `waiting` / `quietGeneration` effect (2 s after the last touch the row springs home and the
/// drift restarts), `select`, `apply` and the pan gesture's callbacks. Nothing here runs per frame:
/// views call `position(at:)`, `trackOpacity(at:)` and `flash(at:)` with the timeline's date.
@MainActor
@Observable
final class SilverScreenCarouselModel {
    private typealias Motion = SilverScreenCarouselMotion
    private typealias Profiles = SilverScreenCarouselProfiles

    /// What moves the row: the drift (`manualMode` false), the finger or a rest (`manualPosition`
    /// held), or a spring (`manualPosition` animating).
    private enum Mode {
        case auto
        case held(Double)
        case spring(SilverScreenCarouselSpring)
    }

    // MARK: Inputs

    let autoPlay: Bool
    /// `previewTime`: freezes the loop at this moment and switches the touch off.
    let previewTime: Double?
    /// Reduce Motion: no drift, no spring, no flash.
    private(set) var reduced = false
    /// The app is in the foreground (`AppState` is active).
    private(set) var foreground = true
    @ObservationIgnored var onSelect: ((String) -> Void)?
    @ObservationIgnored var onApply: ((String) -> Void)?

    // MARK: State the views read

    /// The card in the gate (`selected`): follows the rounded position, and is set at once by a pick.
    private(set) var selected = 0
    /// The name of the applied profile (`applied`), or nil.
    private(set) var applied: String?
    /// The row left the autoplay because of a touch or an apply (`manual`).
    private(set) var manual = false

    private var mode = Mode.auto
    /// Loop time at the moment the clock started running, or the frozen time while it is stopped.
    private var clockBase = 0.0
    /// When the clock last started running; nil while it is stopped.
    private var clockStart: Date?
    /// When the shutter flash fired; nil when there is none.
    private var flashStart: Date?

    // MARK: Private state

    private var waiting = false
    private var dragging = false
    private var dragStart = 0.0
    private var springGeneration = 0
    private var resumeTask: Task<Void, Never>?
    private var springTask: Task<Void, Never>?
    private var flashTask: Task<Void, Never>?
    private let selectionHaptic = UISelectionFeedbackGenerator()
    private let notificationHaptic = UINotificationFeedbackGenerator()

    init(autoPlay: Bool, previewTime: Double?) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        selected = Motion.index(for: position(at: Date()))
    }

    /// With a preview time the screen is a still frame and ignores every touch.
    var interactive: Bool {
        return previewTime == nil
    }

    // MARK: Reading the motion

    /// `time` of `useSceneClock`: the loop time at `date`.
    func sceneTime(at date: Date) -> Double {
        if let previewTime {
            return Motion.normalizeSceneTime(previewTime, period: Motion.period)
        }
        guard let clockStart else { return clockBase }
        let elapsed = max(0, date.timeIntervalSince(clockStart))
        return (clockBase + elapsed).truncatingRemainder(dividingBy: Motion.period)
    }

    /// `position`: where the row is, in cards.
    func position(at date: Date) -> Double {
        switch mode {
        case .auto:
            return Motion.autoPosition(sceneTime(at: date))
        case .held(let value):
            return value
        case .spring(let spring):
            return spring.value(at: date)
        }
    }

    /// `manualMode`.
    private var isManualMode: Bool {
        if case .auto = mode { return false }
        return true
    }

    /// The opacity of the row: it fades out and back in at the end of the loop.
    func trackOpacity(at date: Date) -> Double {
        if isManualMode || reduced { return 1 }
        let t = sceneTime(at: date)
        let fadeOut = Motion.phase(t, Motion.sourceEnd, Motion.fadeOutEnd)
        let fadeIn = Motion.phase(t, Motion.fadeOutEnd, Motion.fadeInEnd)
        return 1 - fadeOut + fadeIn
    }

    /// `flash`: the opacity of the shutter over the card in the gate.
    func flash(at date: Date) -> Double {
        guard let flashStart else { return 0 }
        let progress = min(1, max(0, date.timeIntervalSince(flashStart) / Motion.flashDuration))
        return Motion.flashPeak * (1 - Motion.settle(progress))
    }

    /// Whether the timeline has to keep ticking: the drift runs, a spring settles or the shutter fades.
    var isMoving: Bool {
        if previewTime != nil { return false }
        if flashStart != nil { return true }
        switch mode {
        case .auto:
            return clockStart != nil
        case .held:
            return false
        case .spring:
            return true
        }
    }

    /// The reaction of `useAnimatedReaction`: `selected` follows the rounded position.
    func syncSelected(_ index: Int) {
        if selected != index {
            selected = index
        }
    }

    // MARK: Lifecycle

    func configure(reduced: Bool, foreground: Bool) {
        self.reduced = reduced
        self.foreground = foreground
        sync()
    }

    func setReduced(_ value: Bool) {
        reduced = value
        sync()
    }

    /// `AppState`: leaving the foreground stops the clock and cancels a spring where it is.
    func setForeground(_ value: Bool) {
        foreground = value
        freezeSpring()
        sync()
    }

    func stop() {
        resumeTask?.cancel()
        resumeTask = nil
        flashTask?.cancel()
        flashTask = nil
        freezeSpring()
        if clockStart != nil {
            clockBase = sceneTime(at: Date())
            clockStart = nil
        }
    }

    // MARK: Clock and timer

    /// The effects of the React Native component: whenever an input changes, stop what runs and
    /// start what is due.
    private func sync() {
        syncClock()
        rearm()
    }

    /// `useSceneClock`'s effect: the clock runs while the autoplay is untouched, in the foreground,
    /// and Reduce Motion is off. It keeps its phase while stopped.
    private func syncClock() {
        if previewTime != nil {
            clockStart = nil
            return
        }
        let now = Date()
        let shouldRun = autoPlay && !manual && applied == nil && foreground && !reduced
        if shouldRun {
            if clockStart == nil {
                clockStart = now
            }
        } else {
            if clockStart != nil {
                clockBase = sceneTime(at: now)
                clockStart = nil
            }
            if reduced {
                clockBase = 0
            }
        }
    }

    /// The `waiting` effect: 2 s after the last touch the row springs home.
    private func rearm() {
        resumeTask?.cancel()
        resumeTask = nil
        guard previewTime == nil, waiting, manual, autoPlay, !reduced, applied == nil, foreground else { return }
        resumeTask = schedule(after: Motion.quietDelay) { [weak self] in
            self?.startSpring(to: 0, velocity: 0, resumes: true)
        }
    }

    private func schedule(after seconds: Double, _ work: @escaping @MainActor () -> Void) -> Task<Void, Never> {
        return Task { @MainActor in
            try? await Task.sleep(for: .seconds(seconds))
            guard !Task.isCancelled else { return }
            work()
        }
    }

    /// `clearResume`.
    private func clearResume() {
        waiting = false
        sync()
    }

    /// `armResume`: the quiet timer starts again from now.
    private func armResume() {
        guard interactive, !dragging else { return }
        waiting = true
        sync()
    }

    /// `resume`: the drift starts again from the first card.
    private func resume() {
        springTask?.cancel()
        springTask = nil
        clockBase = 0
        mode = .auto
        manual = false
        waiting = false
        sync()
    }

    // MARK: Springs

    /// `manualPosition.set(withSpring(target, SPRING))` from where the row is now.
    private func startSpring(to target: Double, velocity: Double, resumes: Bool) {
        let now = Date()
        let spring = SilverScreenCarouselSpring(
            from: position(at: now), to: target, velocity: velocity, start: now, resumes: resumes)
        mode = .spring(spring)
        springGeneration += 1
        let generation = springGeneration
        springTask?.cancel()
        springTask = schedule(after: spring.restTime) { [weak self] in
            self?.finishSpring(generation)
        }
    }

    /// The spring's callback: `finished` is true only if nothing replaced the spring.
    private func finishSpring(_ generation: Int) {
        guard generation == springGeneration, case .spring(let spring) = mode else { return }
        springTask = nil
        if spring.resumes {
            resume()
        } else {
            mode = .held(spring.to)
        }
    }

    /// `cancelAnimation(manualPosition)`: a spring stops where it is.
    private func freezeSpring() {
        guard case .spring = mode else { return }
        springTask?.cancel()
        springTask = nil
        mode = .held(position(at: Date()))
    }

    /// `manualPosition.set(value)`: the row is at `value`, whatever it was doing.
    private func hold(_ value: Double) {
        springTask?.cancel()
        springTask = nil
        mode = .held(value)
    }

    /// `manualPosition.set(reduced ? target : withSpring(target, SPRING))`.
    private func move(to target: Double, velocity: Double = 0) {
        if reduced {
            hold(target)
        } else {
            startSpring(to: target, velocity: velocity, resumes: false)
        }
    }

    // MARK: Touch

    /// `begin`: a touch takes the row from the autoplay.
    private func begin() {
        waiting = false
        manual = true
        applied = nil
        sync()
    }

    /// `commit`: a profile was chosen.
    private func commit(_ index: Int) {
        selected = index
        onSelect?(Profiles.all[index].id)
        selectionHaptic.selectionChanged()
    }

    /// The pan's `onStart`: the row stops and follows the finger from where it was.
    func panStarted() {
        guard interactive else { return }
        let current = position(at: Date())
        hold(current)
        dragStart = current
        dragging = true
        begin()
    }

    /// The pan's `onUpdate`: 1:1 with the finger, rubber-banding past the ends.
    func panMoved(translationX: Double, step: Double) {
        guard interactive, dragging else { return }
        let raw = dragStart - translationX / step
        let bound = min(3, max(0, raw))
        let over = raw - bound
        let stretch = over / (1 + abs(over) * 4) * 0.5
        hold(bound + stretch)
    }

    /// The pan's `onEnd`: settles on a spring that carries the finger's speed (cards a second),
    /// or goes back where the drag began when the gesture was cancelled.
    func panEnded(velocityX: Double, step: Double, success: Bool) {
        guard interactive, dragging else { return }
        if !success {
            let rollback = min(3, max(0, dragStart.rounded()))
            move(to: rollback)
            return
        }
        let current = position(at: Date())
        let velocity = -velocityX / step
        let next = min(3, max(0, (current + velocity * 0.18).rounded()))
        move(to: next, velocity: velocity)
        commit(Int(next))
    }

    /// The pan's `onFinalize`.
    func panFinished() {
        guard interactive else { return }
        dragging = false
        armResume()
    }

    /// `onScrollBeginDrag` (`pauseReading`): the page is being scrolled, so the drift stops.
    func scrollBegan() {
        guard interactive else { return }
        waiting = false
        hold(position(at: Date()))
        manual = true
        sync()
    }

    /// `onScrollEndDrag` and `onMomentumScrollEnd`.
    func scrollEnded() {
        armResume()
    }

    /// `onMomentumScrollBegin`.
    func momentumBegan() {
        guard interactive else { return }
        clearResume()
    }

    // MARK: Buttons

    /// `select`: the arrows, the dots and the accessibility actions.
    func select(_ index: Int) {
        guard interactive else { return }
        let current = position(at: Date())
        begin()
        hold(current)
        move(to: Double(index))
        commit(index)
        armResume()
    }

    /// Steps the profile in the gate by one (`increment` / `decrement`).
    func stepProfile(by delta: Int) {
        select(min(3, max(0, selected + delta)))
    }

    /// `apply`: the row snaps to the card in the gate, the shutter fires and the arrow becomes a check.
    func apply() {
        guard interactive else { return }
        waiting = false
        let index = Motion.index(for: position(at: Date()))
        hold(Double(index))
        manual = true
        applied = Profiles.all[index].name
        onApply?(Profiles.all[index].id)
        fireShutter()
        notificationHaptic.notificationOccurred(.success)
        sync()
    }

    /// "Try another": the badge goes, and 2 s later the row drifts again.
    func tryAnother() {
        guard interactive else { return }
        applied = nil
        armResume()
    }

    /// `fireShutter`: one flash over the frame in the gate.
    private func fireShutter() {
        guard !reduced else { return }
        let stamp = Date()
        flashStart = stamp
        flashTask?.cancel()
        flashTask = schedule(after: Motion.flashDuration + 0.05) { [weak self] in
            guard let self, self.flashStart == stamp else { return }
            self.flashStart = nil
        }
    }
}
