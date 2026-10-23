import Observation
import QuartzCore
import UIKit

enum ReceiptPrinterStage: Int, Equatable {
    case grid
    case menu
    case printing
    case reading
    case retracting
}

struct ReceiptPrinterViewState: Equatable {
    var stage: ReceiptPrinterStage = .grid
    var pressed = -1
    var gridIn = 1.0
    var slotIn = 0.0
    var menuIn = 0.0
    var lift = 0.0
    var feed = 0.0
    var fed = 0.0
    var scroll = 0.0
    var barIn = 0.0
    var indicatorIn = 0.0
    var morph = 0.0
    var morphFrom = 0
    var morphBack = false
    var develop = 0.0
    var cut = 0.0
    var held = 0.0
    var armed = false
    var clock = 0.0
    var reducedMotion = false
}

private enum ReceiptPrinterScalar: Hashable {
    case gridIn
    case slotIn
    case menuIn
    case lift
    case feed
    case barIn
    case indicatorIn
    case morph
    case develop
    case cut
    case held
    case scroll
}

private enum ReceiptPrinterCurve {
    case linear
    case easeOut
    case easeInOut
    case easeOutQuad
    case easeInOutQuad
    case spring(damping: Double, stiffness: Double, mass: Double)
}

private enum ReceiptPrinterCompletion {
    case feed
    case cutDown
    case retractTug
    case retractPaper
    case gridReturn
}

private struct ReceiptPrinterAnimation {
    let key: ReceiptPrinterScalar
    let from: Double
    let to: Double
    let startedAt: Double
    let duration: Double
    let curve: ReceiptPrinterCurve
    let completion: ReceiptPrinterCompletion?
}

@MainActor
@Observable
final class ReceiptPrinterModel {
    /// Product override for this port. The RN source token remains 3500 ms in ReceiptPrinterMotion.AUTOPLAY.
    private static let interactionResumeDelay = 2.0
    private static let sourceTop = 62.0
    private static let animationEpsilon = 0.000_5
    private static let decayStopVelocity = 1.0
    private static let decayDerivativeEpsilon = 0.1
    private static let decaySlopeFactor = 0.1
    private static let reanimatedMaximumDeltaMilliseconds = 64.0
    private static let springEnergyThreshold = 6e-9
    private static let maximumDisplayDelta = 0.25
    private static let paperFootClearance = 48.0
    private static let velocityDismissMinimumScroll = -30.0
    private static let indicatorShowScroll = 2.0
    private static let rubberDenominator = 400.0
    private static let scriptedReleaseVelocityFactor = 0.4
    private static let millisecondsPerSecond = 1_000.0

    private(set) var state = ReceiptPrinterViewState()

    @ObservationIgnored private var autoPlay: Bool
    @ObservationIgnored private var animations: [ReceiptPrinterScalar: ReceiptPrinterAnimation] = [:]
    @ObservationIgnored private var displayLink: CADisplayLink?
    @ObservationIgnored private var previousTimestamp: CFTimeInterval?
    @ObservationIgnored private lazy var displayLinkTarget = ReceiptPrinterDisplayLinkTarget(model: self)
    @ObservationIgnored private var simulationClock = 0.0
    @ObservationIgnored private var paperTop = ReceiptPrinterTokens.SLOT.paperTop
    @ObservationIgnored private var feedVisible = 0.0
    @ObservationIgnored private(set) var maxScroll = 0.0
    @ObservationIgnored private var scriptTime = 0.0
    @ObservationIgnored private var scriptPaused = false
    @ObservationIgnored private var scriptSegment = -1
    @ObservationIgnored private var quietTime = 0.0
    @ObservationIgnored private var realTouchDown = false
    @ObservationIgnored private var scriptedAction = false
    @ObservationIgnored private var dragStart = 0.0
    @ObservationIgnored private var dragScripted = false
    @ObservationIgnored private var decaying = false
    @ObservationIgnored private var scrollVelocity = 0.0
    @ObservationIgnored private var decayElapsedMilliseconds = 0.0
    @ObservationIgnored private var decaySpringActive = false
    @ObservationIgnored private var lastFeedStep = -1

    private let selectionFeedback = UISelectionFeedbackGenerator()
    private let mediumFeedback = UIImpactFeedbackGenerator(style: .medium)
    private let lightFeedback = UIImpactFeedbackGenerator(style: .light)

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
    }

    func configure(autoPlay: Bool, reducedMotion: Bool) {
        let reducedChanged = state.reducedMotion != reducedMotion
        self.autoPlay = autoPlay
        state.reducedMotion = reducedMotion

        if reducedMotion {
            animations.removeAll()
            decaying = false
            scriptPaused = true
            realTouchDown = false
            quietTime = 0
            state.stage = .reading
            state.gridIn = 0
            state.slotIn = 1
            state.menuIn = 0
            state.lift = 0
            state.feed = 1
            state.fed = receiptHeight
            state.scroll = min(max(0, state.scroll), maxScroll)
            state.morph = 0
            state.morphBack = false
            state.develop = 0
            state.cut = 0
            state.held = 0
            state.armed = false
            updateBarImmediately()
            updateIndicator(animated: false)
        } else if reducedChanged, autoPlay {
            resetToGrid(restartScript: true)
        } else if !autoPlay {
            scriptPaused = true
        } else if state.stage == .grid {
            scriptPaused = false
        }

        if needsClock {
            startClockIfNeeded()
        } else {
            stopClock()
        }
    }

    func layout(height: Double, safeTop: Double) {
        let islandTop = safeTop >= 51
            ? ReceiptPrinterTokens.ISLAND.top
            : safeTop + ReceiptPrinterTokens.ISLAND.noIslandGap
        paperTop = islandTop + ReceiptPrinterTokens.ISLAND.openH - ReceiptPrinterTokens.ISLAND.tuck
        feedVisible = max(0, height - paperTop)
        maxScroll = max(
            0,
            paperTop + ReceiptPrinterTokens.RECEIPT.length
                - (ReceiptPrinterTokens.BAR.top + safeTop - Self.sourceTop - Self.paperFootClearance)
        )
        if state.stage == .reading, !decaying {
            state.scroll = min(maxScroll, state.scroll)
        }
    }

    func startClockIfNeeded() {
        guard needsClock, displayLink == nil else { return }
        let link = CADisplayLink(target: displayLinkTarget, selector: #selector(ReceiptPrinterDisplayLinkTarget.frame(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 60, preferred: 60)
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stopClock() {
        displayLink?.invalidate()
        displayLink = nil
        previousTimestamp = nil
    }

    func touchBegan() {
        guard !realTouchDown else { return }
        realTouchDown = true
        pauseAutoplay()
    }

    func touchMoved() {
        guard realTouchDown else { return }
        quietTime = 0
    }

    func touchEnded() {
        guard realTouchDown else { return }
        realTouchDown = false
        quietTime = 0
        startClockIfNeeded()
    }

    /// Used by controls whose pressed callbacks arrive only when UIKit/SwiftUI commits the tap.
    func noteUserAction() {
        scriptPaused = true
        scriptSegment = -1
        quietTime = 0
        startClockIfNeeded()
    }

    func openMenu(_ index: Int, scripted: Bool) {
        guard state.stage == .grid else { return }
        if !scripted { noteUserAction() }
        scriptedAction = scripted
        state.pressed = index
        state.stage = .menu
        animate(
            .lift,
            to: 1,
            duration: .infinity,
            curve: .spring(
                damping: ReceiptPrinterMotion.PRESS.menuIn.damping,
                stiffness: ReceiptPrinterMotion.PRESS.menuIn.stiffness,
                mass: ReceiptPrinterMotion.PRESS.menuIn.mass
            )
        )
        animate(
            .menuIn,
            to: 1,
            duration: .infinity,
            curve: .spring(
                damping: ReceiptPrinterMotion.PRESS.menuIn.damping,
                stiffness: ReceiptPrinterMotion.PRESS.menuIn.stiffness,
                mass: ReceiptPrinterMotion.PRESS.menuIn.mass
            )
        )
        feedback(.tick, scripted: scripted)
        startClockIfNeeded()
    }

    func closeMenu(scripted: Bool = false) {
        guard state.stage == .menu else { return }
        if !scripted { noteUserAction() }
        state.stage = .grid
        let duration = seconds(ReceiptPrinterMotion.PRESS.menuOutMs)
        animate(.menuIn, to: 0, duration: duration, curve: .easeOut)
        animate(.lift, to: 0, duration: duration, curve: .easeOut)
        startClockIfNeeded()
    }

    func printReceipt(scripted: Bool, tile: Int = -1) {
        guard state.stage == .grid || state.stage == .menu else { return }
        if !scripted { noteUserAction() }
        scriptedAction = scripted
        if tile >= 0 { state.pressed = tile }
        state.morphFrom = max(0, state.pressed)
        state.morphBack = false
        state.stage = .printing
        feedback(.open, scripted: scripted)

        cancel(.feed)
        cancel(.scroll)
        decaying = false
        lastFeedStep = -1
        state.scroll = 0
        state.barIn = 0
        state.indicatorIn = 0
        state.feed = 0
        state.fed = 0
        state.morph = 0
        state.develop = 0
        state.cut = 0
        state.armed = false

        let slow = ReceiptPrinterMotion.QA_SLOWMO
        if !state.reducedMotion {
            animate(.morph, to: 1, duration: seconds(ReceiptPrinterMotion.UPGRADE.morphMs * slow), curve: .easeOut)
        }
        animate(.develop, to: 1, duration: seconds(ReceiptPrinterMotion.UPGRADE.developFadeMs), curve: .easeOut)
        animate(.menuIn, to: 0, duration: seconds(ReceiptPrinterMotion.PRESS.menuOutMs), curve: .easeOut)
        animate(.lift, to: 0, duration: seconds(ReceiptPrinterMotion.PRESS.menuOutMs), curve: .easeOut)
        animate(.gridIn, to: 0, duration: seconds(ReceiptPrinterMotion.SCREEN.gridOutMs), curve: .easeOut)
        if state.reducedMotion {
            cancel(.slotIn)
            state.slotIn = 1
        } else {
            animate(
                .slotIn,
                to: 1,
                duration: .infinity,
                curve: .spring(
                    damping: ReceiptPrinterMotion.ISLAND_SPRING.damping,
                    stiffness: ReceiptPrinterMotion.ISLAND_SPRING.stiffness,
                    mass: ReceiptPrinterMotion.ISLAND_SPRING.mass
                )
            )
        }

        if state.reducedMotion {
            animate(
                .feed,
                to: 1,
                duration: seconds(ReceiptPrinterMotion.SCROLL.barMs),
                curve: .easeInOutQuad,
                completion: .feed
            )
        } else {
            animate(
                .feed,
                to: 1,
                duration: seconds(ReceiptPrinterMotion.FEED.ms * slow),
                curve: .linear,
                delay: seconds(ReceiptPrinterMotion.ISLAND_LEAD_MS * slow),
                completion: .feed
            )
        }
        startClockIfNeeded()
    }

    /// Grabbing remains enabled during PRINTING, intentionally fixing RN's accidental pointer-events gate.
    func dragBegan(scripted: Bool) {
        if !scripted { noteUserAction() }
        if state.stage == .printing {
            cancel(.feed)
            state.feed = 1
            state.fed = receiptHeight
            animate(.develop, to: 0, duration: seconds(ReceiptPrinterMotion.UPGRADE.developFadeMs), curve: .easeOut)
            state.stage = .reading
        }
        guard state.stage == .reading else { return }
        cancel(.scroll)
        decaying = false
        dragStart = state.scroll
        dragScripted = scripted
        animate(.held, to: 1, duration: seconds(ReceiptPrinterMotion.UPGRADE.liftMs), curve: .easeInOutQuad)
        startClockIfNeeded()
    }

    func dragMoved(translationY: Double) {
        guard state.stage == .reading else { return }
        if !dragScripted { touchMoved() }
        state.scroll = rubberBand(dragStart - translationY)
        let armed = state.scroll < -ReceiptPrinterMotion.SCROLL.dismissBy
        if armed != state.armed {
            state.armed = armed
            if armed { feedback(.tick, scripted: dragScripted) }
        }
        updateIndicator(animated: true)
        startClockIfNeeded()
    }

    func dragEnded(velocityY: Double) {
        animate(.held, to: 0, duration: seconds(ReceiptPrinterMotion.UPGRADE.liftMs), curve: .easeInOutQuad)
        state.armed = false
        guard state.stage == .reading else { return }
        let scroll = state.scroll
        if scroll < -ReceiptPrinterMotion.SCROLL.dismissBy
            || (scroll < Self.velocityDismissMinimumScroll
                && velocityY > ReceiptPrinterMotion.SCROLL.dismissVelocity) {
            retract(scripted: dragScripted)
            return
        }
        scrollVelocity = -velocityY
        decayElapsedMilliseconds = 0
        decaySpringActive = false
        decaying = true
        startClockIfNeeded()
    }

    func accessibilityScroll(by delta: Double) {
        noteUserAction()
        guard state.stage == .reading else { return }
        cancel(.scroll)
        decaying = false
        state.scroll = min(maxScroll, max(0, state.scroll + delta))
        updateIndicator(animated: true)
        startClockIfNeeded()
    }

    func retract(scripted: Bool) {
        guard state.stage == .reading else { return }
        if !scripted { noteUserAction() }
        scriptedAction = scripted
        state.stage = .retracting
        feedback(.tear, scripted: scripted)
        cancel(.scroll)
        decaying = false
        animate(.barIn, to: 0, duration: seconds(ReceiptPrinterMotion.SCROLL.barMs), curve: .easeInOutQuad)
        animate(.indicatorIn, to: 0, duration: 0.15, curve: .easeOut)

        if state.reducedMotion {
            completeRetraction()
        } else {
            animate(
                .scroll,
                to: state.scroll - ReceiptPrinterMotion.UPGRADE.tug,
                duration: seconds(ReceiptPrinterMotion.UPGRADE.tugMs),
                curve: .easeOutQuad,
                completion: .retractTug
            )
        }
        startClockIfNeeded()
    }

    func advanceForTesting(by delta: Double) {
        advance(by: delta, decayElapsed: delta)
    }

    fileprivate func frame(at timestamp: CFTimeInterval) {
        guard let previousTimestamp else {
            self.previousTimestamp = timestamp
            return
        }
        self.previousTimestamp = timestamp
        let wallDelta = max(0, timestamp - previousTimestamp)
        advance(by: min(Self.maximumDisplayDelta, wallDelta), decayElapsed: wallDelta)
    }

    private func advance(by delta: Double, decayElapsed: Double) {
        guard delta > 0 else { return }
        simulationClock += delta
        if !state.reducedMotion && (state.stage == .printing || state.stage == .reading) {
            state.clock = simulationClock
        }
        updateAnimations()
        updateFeed()
        updateDecay(delta: delta, elapsedDelta: decayElapsed)
        updateBar(delta: delta)
        // The loop clock runs on real elapsed time; only the physics above use the clamped delta, so a stalled
        // frame on a slow machine never stretches the loop.
        updateAutoplay(delta: decayElapsed)
        if !needsClock { stopClock() }
    }

    private func updateAnimations() {
        guard !animations.isEmpty else { return }
        let snapshot = Array(animations.values)
        var completions: [ReceiptPrinterCompletion] = []
        for animation in snapshot {
            guard animations[animation.key]?.startedAt == animation.startedAt else { continue }
            let elapsed = max(0, simulationClock - animation.startedAt)
            let progress = animation.duration <= 0 ? 1 : min(1, elapsed / animation.duration)
            let eased = easedProgress(progress, curve: animation.curve, elapsed: elapsed)
            set(animation.key, animation.from + (animation.to - animation.from) * eased)

            let animationSettled: Bool
            if case let .spring(damping, stiffness, mass) = animation.curve {
                animationSettled = ReceiptPrinterEasing.springEnergyRatio(
                    elapsed,
                    damping: damping,
                    stiffness: stiffness,
                    mass: mass
                ) <= Self.springEnergyThreshold
            } else {
                animationSettled = progress >= 1
            }
            if animationSettled {
                set(animation.key, animation.to)
                animations.removeValue(forKey: animation.key)
                if let completion = animation.completion { completions.append(completion) }
            }
        }
        for completion in completions { complete(completion) }
    }

    private func updateFeed() {
        guard state.stage == .printing else { return }
        if state.reducedMotion {
            state.fed = state.feed * receiptHeight
            return
        }
        state.fed = stepped(state.feed) * feedVisible
        let step = Int(floor(state.feed * Double(ReceiptPrinterMotion.FEED.steps)))
        if step != lastFeedStep {
            lastFeedStep = step
            feedback(.tick, scripted: scriptedAction)
        }
    }

    private func updateDecay(delta: Double, elapsedDelta: Double) {
        guard decaying, state.stage == .reading else { return }
        // Mirrors Reanimated 4's installed rubberBandDecay.ts recurrence, including its elapsed-since-start
        // exponential and 64 ms frame clamp. It intentionally is not the common pow(deceleration, dt) model.
        let frameMilliseconds = min(
            max(delta * Self.millisecondsPerSecond, 0),
            Self.reanimatedMaximumDeltaMilliseconds
        )
        decayElapsedMilliseconds += elapsedDelta * Self.millisecondsPerSecond
        let lower = 0.0
        let upper = maxScroll
        let nearest = abs(state.scroll - lower) < abs(state.scroll - upper) ? lower : upper
        let derivative = state.scroll < lower || state.scroll > upper ? state.scroll - nearest : 0
        let decay = exp(
            -(1 - ReceiptPrinterMotion.SCROLL.deceleration)
                * decayElapsedMilliseconds
                * Self.decaySlopeFactor
        )
        let velocity = scrollVelocity * decay - derivative * ReceiptPrinterMotion.SCROLL.rubber

        if abs(derivative) > Self.decayDerivativeEpsilon {
            decaySpringActive = true
        } else if decaySpringActive {
            state.scroll = nearest
            decaying = false
            scrollVelocity = 0
            updateIndicator(animated: true)
            return
        } else if abs(velocity) < Self.decayStopVelocity {
            decaying = false
            scrollVelocity = 0
            updateIndicator(animated: true)
            return
        }

        state.scroll += velocity * frameMilliseconds / Self.millisecondsPerSecond
        scrollVelocity = velocity
        updateIndicator(animated: true)
    }

    private func updateBar(delta: Double) {
        guard state.stage == .reading else { return }
        let target = state.scroll > maxScroll - ReceiptPrinterMotion.SCROLL.barWithin ? 1.0 : 0.0
        let amount = delta / seconds(ReceiptPrinterMotion.SCROLL.barMs)
        if target > state.barIn {
            state.barIn = min(target, state.barIn + amount)
        } else if target < state.barIn {
            state.barIn = max(target, state.barIn - amount)
        }
    }

    private func updateAutoplay(delta: Double) {
        guard autoPlay, !state.reducedMotion else { return }
        if scriptPaused {
            if realTouchDown {
                quietTime = 0
                return
            }
            quietTime += delta
            guard quietTime >= Self.interactionResumeDelay else { return }
            restoreRestForAutoplay()
            return
        }

        let previous = scriptTime
        let debugFreeze = ReceiptPrinterMotion.DEBUG_FREEZE_MS / Self.millisecondsPerSecond
        let next: Double
        if debugFreeze >= 0, previous >= debugFreeze {
            next = previous
        } else {
            next = previous + delta / ReceiptPrinterMotion.QA_SLOWMO
        }

        let autoplay = ReceiptPrinterMotion.AUTOPLAY.self
        cross(previous, next, seconds(autoplay.pressAt)) { openMenu(0, scripted: true) }
        cross(previous, next, seconds(autoplay.pickAt)) { printReceipt(scripted: true) }
        updateScriptedScroll(from: previous, to: next)
        cross(previous, next, seconds(autoplay.tearAt)) { retract(scripted: true) }

        let loop = ReceiptPrinterMotion.LOOP_MS / Self.millisecondsPerSecond
        if next >= loop {
            scriptTime = next.truncatingRemainder(dividingBy: loop)
            scriptSegment = -1
        } else {
            scriptTime = next
        }
    }

    private func updateScriptedScroll(from previous: Double, to current: Double) {
        let scrolls = ReceiptPrinterMotion.AUTOPLAY.scrolls
        var segment = -1
        for index in scrolls.indices {
            let item = scrolls[index]
            if current >= seconds(item.start), current < seconds(item.end) { segment = index }
        }
        if segment != scriptSegment {
            if scriptSegment >= 0 {
                let item = scrolls[scriptSegment]
                dragMoved(translationY: -item.by)
                let duration = seconds(item.end - item.start)
                let velocityY = (-item.by / duration) * Self.scriptedReleaseVelocityFactor
                dragEnded(velocityY: velocityY)
            }
            if segment >= 0 { dragBegan(scripted: true) }
        }
        if segment >= 0 {
            let item = scrolls[segment]
            let unit = min(1, max(0, (current - seconds(item.start)) / seconds(item.end - item.start)))
            let smooth = unit * unit * (3 - 2 * unit)
            dragMoved(translationY: -item.by * smooth)
        }
        scriptSegment = segment
    }

    private func restoreRestForAutoplay() {
        switch state.stage {
        case .grid:
            scriptTime = 0
            scriptSegment = -1
            scriptPaused = false
            quietTime = 0
        case .menu:
            closeMenu(scripted: true)
        case .printing:
            break
        case .reading:
            retract(scripted: true)
        case .retracting:
            break
        }
    }

    private func complete(_ completion: ReceiptPrinterCompletion) {
        switch completion {
        case .feed:
            state.feed = 1
            state.fed = receiptHeight
            state.stage = .reading
            animate(.develop, to: 0, duration: seconds(ReceiptPrinterMotion.UPGRADE.developFadeMs), curve: .easeInOutQuad)
            if !state.reducedMotion {
                animate(
                    .cut,
                    to: 1,
                    duration: seconds(ReceiptPrinterMotion.UPGRADE.cutDownMs),
                    curve: .easeOutQuad,
                    completion: .cutDown
                )
                feedback(.open, scripted: scriptedAction)
            }
            if scriptPaused, quietTime >= Self.interactionResumeDelay { restoreRestForAutoplay() }
        case .cutDown:
            animate(
                .cut,
                to: 0,
                duration: .infinity,
                curve: .spring(
                    damping: ReceiptPrinterMotion.UPGRADE.cutSpring.damping,
                    stiffness: ReceiptPrinterMotion.UPGRADE.cutSpring.stiffness,
                    mass: ReceiptPrinterMotion.UPGRADE.cutSpring.mass
                )
            )
        case .retractTug:
            animate(
                .scroll,
                to: paperTop + ReceiptPrinterTokens.RECEIPT.length,
                duration: seconds(ReceiptPrinterMotion.RETRACT.ms),
                curve: .easeInOut,
                completion: .retractPaper
            )
        case .retractPaper:
            completeRetraction()
        case .gridReturn:
            state.stage = .grid
            if scriptPaused, quietTime >= Self.interactionResumeDelay { restoreRestForAutoplay() }
        }
    }

    private func completeRetraction() {
        state.fed = 0
        state.feed = 0
        state.scroll = 0
        state.cut = 0
        state.held = 0
        state.armed = false
        if state.reducedMotion {
            cancel(.slotIn)
            state.slotIn = 0
        } else {
            animate(
                .slotIn,
                to: 0,
                duration: .infinity,
                curve: .spring(
                    damping: ReceiptPrinterMotion.ISLAND_SPRING.damping,
                    stiffness: ReceiptPrinterMotion.ISLAND_SPRING.stiffness,
                    mass: ReceiptPrinterMotion.ISLAND_SPRING.mass
                )
            )
        }
        state.morph = 0
        if !state.reducedMotion {
            state.morphBack = true
            state.morph = 1
            animate(
                .morph,
                to: 0,
                duration: seconds(ReceiptPrinterMotion.POLISH.returnMs * ReceiptPrinterMotion.QA_SLOWMO),
                curve: .easeOut
            )
        }
        animate(
            .gridIn,
            to: 1,
            duration: seconds(ReceiptPrinterMotion.SCREEN.gridInMs),
            curve: .easeOut,
            completion: .gridReturn
        )
    }

    private func resetToGrid(restartScript: Bool) {
        animations.removeAll()
        decaying = false
        state = ReceiptPrinterViewState(reducedMotion: false)
        scriptedAction = false
        dragScripted = false
        lastFeedStep = -1
        realTouchDown = false
        quietTime = 0
        scriptSegment = -1
        scriptTime = 0
        scriptPaused = !restartScript
    }

    private func pauseAutoplay() {
        if scriptSegment >= 0 {
            animate(.held, to: 0, duration: seconds(ReceiptPrinterMotion.UPGRADE.liftMs), curve: .easeInOutQuad)
            state.armed = false
        }
        scriptPaused = true
        scriptSegment = -1
        quietTime = 0
        startClockIfNeeded()
    }

    private func updateBarImmediately() {
        state.barIn = state.scroll > maxScroll - ReceiptPrinterMotion.SCROLL.barWithin ? 1 : 0
    }

    private func updateIndicator(animated: Bool) {
        let target = state.stage == .reading && state.scroll > Self.indicatorShowScroll ? 1.0 : 0.0
        if animated {
            if abs(value(.indicatorIn) - target) > Self.animationEpsilon,
               animations[.indicatorIn]?.to != target {
                animate(.indicatorIn, to: target, duration: 0.15, curve: .easeOut)
            }
        } else {
            cancel(.indicatorIn)
            state.indicatorIn = target
        }
    }

    private func animate(
        _ key: ReceiptPrinterScalar,
        to target: Double,
        duration: Double,
        curve: ReceiptPrinterCurve,
        delay: Double = 0,
        completion: ReceiptPrinterCompletion? = nil
    ) {
        let from = value(key)
        if abs(from - target) < Self.animationEpsilon {
            set(key, target)
            if let completion { complete(completion) }
            return
        }
        animations[key] = ReceiptPrinterAnimation(
            key: key,
            from: from,
            to: target,
            startedAt: simulationClock + max(0, delay),
            duration: max(0, duration),
            curve: curve,
            completion: completion
        )
    }

    private func cancel(_ key: ReceiptPrinterScalar) {
        animations.removeValue(forKey: key)
    }

    private func value(_ key: ReceiptPrinterScalar) -> Double {
        switch key {
        case .gridIn: state.gridIn
        case .slotIn: state.slotIn
        case .menuIn: state.menuIn
        case .lift: state.lift
        case .feed: state.feed
        case .barIn: state.barIn
        case .indicatorIn: state.indicatorIn
        case .morph: state.morph
        case .develop: state.develop
        case .cut: state.cut
        case .held: state.held
        case .scroll: state.scroll
        }
    }

    private func set(_ key: ReceiptPrinterScalar, _ value: Double) {
        switch key {
        case .gridIn: state.gridIn = value
        case .slotIn: state.slotIn = value
        case .menuIn: state.menuIn = value
        case .lift: state.lift = value
        case .feed: state.feed = value
        case .barIn: state.barIn = value
        case .indicatorIn: state.indicatorIn = value
        case .morph: state.morph = value
        case .develop: state.develop = value
        case .cut: state.cut = value
        case .held: state.held = value
        case .scroll: state.scroll = value
        }
    }

    private func easedProgress(_ progress: Double, curve: ReceiptPrinterCurve, elapsed: Double) -> Double {
        switch curve {
        case .linear:
            progress
        case .easeOut:
            ReceiptPrinterEasing.cubicBezier(progress, control: ReceiptPrinterMotion.POLISH.easeOut)
        case .easeInOut:
            ReceiptPrinterEasing.cubicBezier(progress, control: ReceiptPrinterMotion.POLISH.easeInOut)
        case .easeOutQuad:
            1 - (1 - progress) * (1 - progress)
        case .easeInOutQuad:
            progress < 0.5
                ? 2 * progress * progress
                : 1 - pow(-2 * progress + 2, 2) / 2
        case let .spring(damping, stiffness, mass):
            ReceiptPrinterEasing.spring(elapsed, damping: damping, stiffness: stiffness, mass: mass)
        }
    }

    private func rubberBand(_ proposed: Double) -> Double {
        if proposed < 0 {
            let distance = -proposed
            return -(distance * ReceiptPrinterMotion.SCROLL.rubber)
                / (1 + distance / Self.rubberDenominator)
        }
        if proposed > maxScroll {
            let distance = proposed - maxScroll
            return maxScroll + (distance * ReceiptPrinterMotion.SCROLL.rubber)
                / (1 + distance / Self.rubberDenominator)
        }
        return proposed
    }

    private func stepped(_ progress: Double) -> Double {
        let steps = Double(ReceiptPrinterMotion.FEED.steps)
        let x = min(1, max(0, progress)) * steps
        let index = min(steps - 1, floor(x))
        let unit = min(1, (x - index) / ReceiptPrinterMotion.FEED.push)
        return (index + 1 - pow(1 - unit, 3)) / steps
    }

    private func cross(_ from: Double, _ to: Double, _ threshold: Double, action: () -> Void) {
        if from < threshold, to >= threshold { action() }
    }

    private func seconds(_ milliseconds: Double) -> Double {
        milliseconds / Self.millisecondsPerSecond
    }

    private var receiptHeight: Double {
        ReceiptPrinterTokens.RECEIPT.length + ReceiptPrinterTokens.RECEIPT.edge.depth
    }

    private var needsClock: Bool {
        if !animations.isEmpty || decaying || realTouchDown { return true }
        if autoPlay, !state.reducedMotion { return true }
        if state.stage == .printing || state.stage == .retracting { return true }
        if state.stage == .reading, !state.reducedMotion { return true }
        if scriptPaused, autoPlay, !state.reducedMotion { return true }
        return false
    }

    private enum Feedback {
        case tick
        case open
        case tear
    }

    private func feedback(_ feedback: Feedback, scripted: Bool) {
        guard !scripted else { return }
        switch feedback {
        case .tick:
            selectionFeedback.selectionChanged()
        case .open:
            mediumFeedback.impactOccurred()
        case .tear:
            lightFeedback.impactOccurred()
        }
    }
}

private enum ReceiptPrinterEasing {
    static func cubicBezier(_ progress: Double, control: [Double]) -> Double {
        guard control.count == 4 else { return progress }
        let x1 = control[0]
        let y1 = control[1]
        let x2 = control[2]
        let y2 = control[3]
        var parameter = progress
        for _ in 0..<6 {
            let x = bezier(parameter, a: x1, b: x2) - progress
            let derivative = bezierDerivative(parameter, a: x1, b: x2)
            if abs(derivative) < 0.000_001 { break }
            parameter = min(1, max(0, parameter - x / derivative))
        }
        return bezier(parameter, a: y1, b: y2)
    }

    static func spring(_ elapsed: Double, damping: Double, stiffness: Double, mass: Double) -> Double {
        let omega = sqrt(stiffness / mass)
        let ratio = damping / (2 * sqrt(stiffness * mass))
        if ratio >= 1 {
            return 1 - exp(-omega * elapsed) * (1 + omega * elapsed)
        }
        let damped = omega * sqrt(1 - ratio * ratio)
        let envelope = exp(-ratio * omega * elapsed)
        return 1 - envelope * (cos(damped * elapsed) + ratio * omega / damped * sin(damped * elapsed))
    }

    static func springEnergyRatio(_ elapsed: Double, damping: Double, stiffness: Double, mass: Double) -> Double {
        let displacement = spring(elapsed, damping: damping, stiffness: stiffness, mass: mass) - 1
        let sample = 0.000_1
        let velocity = (
            spring(elapsed + sample, damping: damping, stiffness: stiffness, mass: mass)
                - spring(elapsed, damping: damping, stiffness: stiffness, mass: mass)
        ) / sample
        let energy = 0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity
        return energy / (0.5 * stiffness)
    }

    private static func bezier(_ t: Double, a: Double, b: Double) -> Double {
        let inverse = 1 - t
        return 3 * inverse * inverse * t * a + 3 * inverse * t * t * b + t * t * t
    }

    private static func bezierDerivative(_ t: Double, a: Double, b: Double) -> Double {
        let inverse = 1 - t
        return 3 * inverse * inverse * a + 6 * inverse * t * (b - a) + 3 * t * t * (1 - b)
    }
}

@MainActor
private final class ReceiptPrinterDisplayLinkTarget: NSObject {
    weak var model: ReceiptPrinterModel?

    init(model: ReceiptPrinterModel) {
        self.model = model
    }

    @objc func frame(_ link: CADisplayLink) {
        model?.frame(at: link.timestamp)
    }
}
