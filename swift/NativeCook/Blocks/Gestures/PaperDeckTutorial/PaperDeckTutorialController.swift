import Observation
import SwiftUI
import UIKit

/// The sizes the touch logic needs, as the screen lays them out.
struct PaperDeckTutorialLayout: Equatable {
    var screenWidth: Double
    var cardWidth: Double
    var cardHeight: Double
}

/// Owns the loop clock, the manual state, the drag and the timers of Paper Deck Tutorial.
///
/// It mirrors the React Native component's shared values (`clock`, `manual`, `manualStep`, `x`,
/// `grab`, `holding`) and its handlers. The clock is worked out from a start date, so the loop keeps a
/// constant period; a finger anywhere on the screen freezes the deck where it is and puts it in
/// manual mode; 2 s after the last finger leaves, a short spring brings the card back to rest and the
/// loop picks up again from the anchor of the card on top. Nothing here runs per frame: the views
/// read `pose(at:)` from their timelines.
@MainActor
@Observable
final class PaperDeckTutorialController {

    // MARK: Values

    /// What the loop clock is doing.
    private enum LoopClock {
        case still(Double)
        case loop(origin: Date, offset: Double)
    }

    /// What the card's travel `x` is doing.
    private enum Travel {
        case still(Double)
        case spring(PaperDeckTutorialSpring)
    }

    /// The state of the card's pan: React Native Gesture Handler's `Pan` with
    /// `activeOffsetX([-10, 10])` and `failOffsetY([-18, 18])`.
    private enum Pan {
        case idle
        case pending
        case active
        case failed
    }

    private var clock: LoopClock
    private var travel: Travel = .still(0)

    /// `manual`: the deck is held by a finger or by the user's own cards instead of the clock.
    private(set) var manual: Bool
    /// `manualStep`: the card on top in manual mode, 0 to 6.
    private(set) var manualStep = 0
    /// `grab`: 1 when a card was taken by its upper part, -0.7 by its lower part (it pivots the other way).
    private(set) var grab = 1.0
    private var holding = false

    // MARK: Inputs

    @ObservationIgnored private var autoPlay: Bool
    @ObservationIgnored private var previewTime: Double?
    @ObservationIgnored private var onComplete: (() -> Void)?
    @ObservationIgnored private var layout = PaperDeckTutorialLayout(screenWidth: 0, cardWidth: 0, cardHeight: 0)
    var reduced = false
    var foreground = true

    // MARK: Working state

    @ObservationIgnored private var pan = Pan.idle
    /// `origin`: the card's travel when the pan began.
    @ObservationIgnored private var panOrigin = 0.0
    @ObservationIgnored private var resumeTimer: Task<Void, Never>?
    @ObservationIgnored private var springEnd: Task<Void, Never>?
    @ObservationIgnored private let impact = UIImpactFeedbackGenerator(style: .light)

    /// `clock` starts at the first card (1.1 s) unless the block plays by itself, and at the
    /// previewed phase when a preview time is given; `manual` is true without `autoPlay`.
    init(autoPlay: Bool, previewTime: Double?) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        if let previewTime {
            clock = .still(PaperDeckTutorialScript.previewPhase(previewTime))
            manual = false
        } else if autoPlay {
            clock = .still(0)
            manual = false
        } else {
            clock = .still(1.1)
            manual = true
        }
    }

    func configure(autoPlay: Bool, previewTime: Double?, onComplete: (() -> Void)?) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onComplete = onComplete
    }

    func updateLayout(_ layout: PaperDeckTutorialLayout) {
        self.layout = layout
    }

    // MARK: Reading

    private var now: Date { Date() }

    /// The loop time `t`, in seconds, at `date`.
    func time(at date: Date) -> Double {
        switch clock {
        case .still(let value):
            return value
        case .loop(let start, let offset):
            let elapsed = max(0, date.timeIntervalSince(start))
            return (offset + elapsed).truncatingRemainder(dividingBy: PaperDeckTutorial.loop)
        }
    }

    private func travelValue(at date: Date) -> Double {
        switch travel {
        case .still(let value):
            return value
        case .spring(let run):
            return run.value(at: date)
        }
    }

    /// `pose`: the deck at `date` on a screen `screenWidth` points wide.
    func pose(at date: Date, screenWidth: Double) -> PaperDeckTutorialPose {
        if manual {
            return PaperDeckTutorialPose(
                step: manualStep,
                x: reduced ? 0 : travelValue(at: date),
                flip: 1,
                time: manualStep == 6 ? 15.6 : 2
            )
        }
        return PaperDeckTutorialScript.autoPose(t: time(at: date), screenWidth: screenWidth)
    }

    /// `pose` for the event handlers, which know the screen's size from `updateLayout`.
    private func currentPose(at date: Date) -> PaperDeckTutorialPose {
        pose(at: date, screenWidth: layout.screenWidth)
    }

    /// Whether the timelines have to keep ticking: the loop runs or a spring is in flight.
    var isMoving: Bool {
        if previewTime != nil { return false }
        if case .loop = clock { return true }
        if case .spring = travel { return true }
        return false
    }

    // MARK: The component's effect

    /// The component's effects: whenever an input changes, stop what runs and start what is due.
    func sync() {
        cancelTimers()
        freeze()
        if let previewTime {
            // `play` with a preview time: hold the previewed phase.
            clock = .still(PaperDeckTutorialScript.previewPhase(previewTime))
            manual = reduced
            return
        }
        if reduced {
            // Reduced Motion: stop where the deck is and show the card on top as a still page.
            let step = currentPose(at: now).step
            travel = .still(0)
            manualStep = step
            manual = true
            return
        }
        play()
    }

    func stop() {
        cancelTimers()
        freeze()
    }

    /// `play`: restart the loop from the cover, unless something stops it.
    private func play() {
        guard autoPlay, !reduced, foreground, manualStep < 6 else { return }
        manual = false
        clock = .loop(origin: now, offset: 0)
    }

    /// `cancelAnimation(clock)` and `cancelAnimation(x)`: stop both where they are.
    private func freeze() {
        let date = now
        clock = .still(time(at: date))
        travel = .still(travelValue(at: date))
    }

    private func cancelTimers() {
        resumeTimer?.cancel()
        resumeTimer = nil
        springEnd?.cancel()
        springEnd = nil
    }

    private func schedule(after seconds: Double, _ work: @escaping @MainActor () -> Void) -> Task<Void, Never> {
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(seconds))
            guard !Task.isCancelled else { return }
            work()
        }
    }

    // MARK: Springs

    /// `x.set(withSpring(target, { duration: 400, dampingRatio: 0.8, velocity }, callback))`.
    private func startSpring(to target: Double, velocity: Double, then completion: @escaping @MainActor (PaperDeckTutorialController) -> Void) {
        let date = now
        let run = PaperDeckTutorialSpring(from: travelValue(at: date), target: target, velocity: velocity, start: date)
        travel = .spring(run)
        springEnd?.cancel()
        springEnd = schedule(after: run.settle) { [weak self] in
            guard let self else { return }
            self.travel = .still(target)
            completion(self)
        }
    }

    // MARK: Holding the deck

    /// `buttonBegin`: a finger lands, so the deck stops where it is and is held in manual mode.
    private func beginHold() {
        holding = true
        resumeTimer?.cancel()
        resumeTimer = nil
        springEnd?.cancel()
        springEnd = nil
        let date = now
        let current = currentPose(at: date)
        manualStep = current.step
        travel = .still(current.x)
        manual = true
        clock = .still(time(at: date))
    }

    /// `buttonEnd`: the finger leaves. Two seconds later the card settles and the loop continues.
    private func endHold() {
        holding = false
        queueResume()
    }

    /// `queueResume`.
    private func queueResume() {
        guard autoPlay, !reduced, foreground, previewTime == nil else { return }
        resumeTimer?.cancel()
        resumeTimer = schedule(after: 2) { [weak self] in
            guard let self, !self.holding, self.manualStep < 6 else { return }
            self.startSpring(to: 0, velocity: 0) { controller in
                controller.resumeLoop()
            }
        }
    }

    /// The end of `queueResume`'s spring: the loop continues from the anchor of the card on top.
    private func resumeLoop() {
        guard !holding else { return }
        let anchor = PaperDeckTutorialScript.anchors[manualStep]
        grab = 1
        clock = .loop(origin: now, offset: anchor)
        manual = false
    }

    // MARK: Touch

    /// A finger lands anywhere on the screen; `cardPoint` is where, in the card's own coordinates,
    /// when it landed on the card (the pan's `onBegin`), nil elsewhere (the scroll view's `onTouchStart`).
    func touchBegan(cardPoint: CGPoint?) {
        guard previewTime == nil else { return }
        impact.prepare()
        if let cardPoint {
            grab = cardPoint.y > layout.cardHeight * 0.62 ? -0.7 : 1
        }
        beginHold()
        if cardPoint != nil {
            panOrigin = travelValue(at: now)
            pan = .pending
        } else {
            pan = .idle
        }
    }

    /// The finger moves; `translation` is measured from where it landed.
    func touchMoved(translation: CGSize) {
        guard previewTime == nil else { return }
        switch pan {
        case .pending:
            if abs(translation.height) >= 18 {
                pan = .failed
            } else if abs(translation.width) >= 10 {
                pan = .active
                follow(Double(translation.width))
            }
        case .active:
            follow(Double(translation.width))
        case .idle, .failed:
            break
        }
    }

    /// The finger leaves (or the system takes the touch away): the pan's `onEnd`, then `onFinalize`.
    func touchEnded(velocityX: Double, cancelled: Bool) {
        guard previewTime == nil else { return }
        if pan == .active {
            release(velocityX: velocityX, success: !cancelled)
        }
        pan = .idle
        endHold()
    }

    /// `onUpdate`: the card follows the finger, with rubber-band resistance beyond 0.75 of the screen.
    private func follow(_ translationX: Double) {
        guard manualStep < 6, !reduced else { return }
        let next = panOrigin + translationX
        let limit = layout.screenWidth * 0.75
        if abs(next) > limit {
            let sign: Double = next < 0 ? -1 : 1
            travel = .still(sign * (limit + (abs(next) - limit) * 0.45))
        } else {
            travel = .still(next)
        }
    }

    /// `onEnd`: throw the card off or spring it back.
    private func release(velocityX: Double, success: Bool) {
        if !success {
            if reduced {
                travel = .still(0)
            } else {
                startSpring(to: 0, velocity: 0) { _ in }
            }
            return
        }
        guard manualStep < 6 else { return }
        let x = travelValue(at: now)
        let commit = abs(x) > layout.cardWidth * 0.26 || abs(velocityX) > 650
        if !commit {
            startSpring(to: 0, velocity: velocityX) { _ in }
            return
        }
        impact.impactOccurred()
        let fast = abs(velocityX) > 650
        let heading: Double = fast ? (velocityX < 0 ? -1 : 1) : (x < 0 ? -1 : 1)
        if reduced {
            advanceStep()
            return
        }
        startSpring(to: heading * layout.screenWidth * 1.4, velocity: velocityX) { controller in
            controller.advanceStep()
        }
    }

    /// The card has left: the next one is on top, and the tutorial is complete after the sixth.
    private func advanceStep() {
        let next = min(6, manualStep + 1)
        manualStep = next
        travel = .still(0)
        if next == 6 {
            onComplete?()
        }
    }

    // MARK: Buttons and accessibility

    /// The gear (`Restart tutorial`): back to the first card.
    func restart() {
        guard previewTime == nil else { return }
        beginHold()
        manualStep = 0
        travel = .still(0)
        endHold()
    }

    /// The close disc (`Finish tutorial`): straight to the choice deck.
    func finish() {
        guard previewTime == nil else { return }
        beginHold()
        manualStep = 6
        travel = .still(0)
        onComplete?()
        endHold()
    }

    /// The card's `increment` and `decrement` accessibility actions (`advance`).
    func accessibilityAdvance(back: Bool) {
        guard previewTime == nil else { return }
        beginHold()
        let current = manualStep
        if back || current >= 6 || reduced {
            if back {
                manualStep = max(0, current - 1)
            } else {
                manualStep = current >= 6 ? 0 : current + 1
            }
            travel = .still(0)
            if !back && current == 5 {
                onComplete?()
            }
        } else {
            impact.impactOccurred()
            let target = PaperDeckTutorialScript.directions[current] * layout.screenWidth * 1.4
            startSpring(to: target, velocity: 0) { controller in
                controller.manualStep = current + 1
                controller.travel = .still(0)
                if current == 5 {
                    controller.onComplete?()
                }
            }
        }
        endHold()
    }
}
