import Observation
import SwiftUI
import UIKit

/// Which sign-in button finished the story.
enum ConfettiGiftOfferProvider {
    case apple
    case google
}

/// The only state a view watches: whether frames are needed. Everything else is read inside the timeline.
@Observable
final class ConfettiGiftOfferWake {
    var needsFrames = true
}

/// What the offer's countdown shows (ConfettiGiftOffer.tsx:46-58).
enum ConfettiGiftOfferCountdown {
    /// Under the loop it reads the loop clock: 00:00 until the ticket settles, then one tick a second from 59:59.
    case loop(String)
    /// Once touched it counts down from 59:59 on the wall clock, starting at this date.
    case wall(start: Date)
}

/// The skip sheet's state for one frame.
struct ConfettiGiftOfferSheetFrame {
    var visible = false
    /// True while the loop positions the sheet; false once a touch hands it to the spring.
    var autoDriven = true
    var translateY: CGFloat = 0
    var scrim: Double = 0
    /// The whole modal slides left with the offer when the account screen pushes in.
    var outgoingX: CGFloat = 0
}

/// Everything the view draws for one frame, derived from loop time or from the touch-driven values.
struct ConfettiGiftOfferFrame {
    var time = 0.0
    var stage = ConfettiGiftOfferStage.gift
    var manual = false
    var openProgress = 0.0
    var confettiAge = -1.0
    var shift: CGFloat = 0
    var giftOpacity = 1.0
    var offerOpacity = 0.0
    var offerScale = 1.0
    var washOpacity = 0.0
    var signupOpacity = 0.0
    var signupShift: CGFloat = 0
    var countdown = ConfettiGiftOfferCountdown.loop("00:00")
    var sheet = ConfettiGiftOfferSheetFrame()
}

/// Everything the controller needs from the view, handed over each frame.
struct ConfettiGiftOfferConfig {
    var autoPlay: Bool
    var previewTime: Double?
    var reduced: Bool
    var metrics: ConfettiGiftOfferMetrics
    var onContinue: ((ConfettiGiftOfferProvider, Bool) -> Void)?
}

/// The block's state machine: a port of the hooks in ConfettiGiftOffer.tsx (`useSceneClock`, `own`, `armQuiet`,
/// the stage effect, the exit sheet). Reanimated shared values become `ConfettiGiftOfferValue`s that are read
/// at the frame's time, React state becomes plain fields, and an effect that React would run after a commit
/// runs at the end of the event's `transaction`.
final class ConfettiGiftOfferController {
    let wake = ConfettiGiftOfferWake()

    private typealias Motion = ConfettiGiftOfferMotion
    private typealias Stage = ConfettiGiftOfferStage

    // MARK: Configuration

    private var autoPlay = false
    private var previewTime: Double?
    private var reduced = false
    private var metrics = ConfettiGiftOfferMetrics.zero
    private var foreground = true
    private var onContinue: ((ConfettiGiftOfferProvider, Bool) -> Void)?
    private var configured = false

    // MARK: React state

    private var manual = false
    private var manualStage = Stage.gift
    private var accepted = false
    private var manualExitVisible = false
    private var manualStart = Date()
    private var ownedStage: Stage?
    private var heldControls = 0
    private var reading = (dragging: false, momentum: false)
    private var quiet: DispatchWorkItem?

    // MARK: Shared values

    private let giftAlpha = ConfettiGiftOfferValue(1)
    private let offerAlpha = ConfettiGiftOfferValue(0)
    private let signupAlpha = ConfettiGiftOfferValue(0)
    private let burst = ConfettiGiftOfferValue(-1)
    private let openingTime = ConfettiGiftOfferValue(0)
    private let manualPush = ConfettiGiftOfferValue(0)
    private let sheetOffset = ConfettiGiftOfferValue(0)
    private var tracks: [ConfettiGiftOfferValue] {
        [giftAlpha, offerAlpha, signupAlpha, burst, openingTime, manualPush, sheetOffset]
    }

    // MARK: The loop clock (`useSceneClock`)

    private var frozenTime = 0.0
    private var anchor: Double?

    // MARK: Effects

    private struct EffectKey: Equatable {
        var stage: Stage
        var manual: Bool
        var foreground: Bool
        var reduced: Bool
    }

    private struct SheetKey: Equatable {
        var visible: Bool
        var foreground: Bool
        var reduced: Bool
        var travel: Double
    }

    private var transactionDepth = 0
    private var effectKey: EffectKey?
    private var effectCleanup: (() -> Void)?
    private var sheetKey: SheetKey?
    private var sheetInitial = 0.0
    private var sheetPanning = false
    /// The sheet's height, measured by the view; until then `height * .598` like the source.
    var panelHeight: CGFloat = 0

    private let impact = UIImpactFeedbackGenerator(style: .light)
    private let notification = UINotificationFeedbackGenerator()

    private static func now() -> Double {
        Date().timeIntervalSinceReferenceDate
    }

    private var interactive: Bool {
        previewTime == nil
    }

    // MARK: - Frames

    /// Evaluates the whole screen at `date`. Called from the timeline, so it changes no observed state.
    func frame(at date: Date, config: ConfettiGiftOfferConfig) -> ConfettiGiftOfferFrame {
        let now = date.timeIntervalSinceReferenceDate
        apply(config)
        for track in tracks {
            if let callback = track.advance(at: now) {
                DispatchQueue.main.async(execute: callback)
            }
        }
        let t = clockTime(now)
        let stage = manual ? manualStage : Motion.stage(at: t)
        let width = metrics.width
        var frame = ConfettiGiftOfferFrame()
        frame.time = t
        frame.stage = stage
        frame.manual = manual

        let push = manual ? manualPush.value(at: now) : Motion.push(t)
        let opening = manual && stage == .opening
        let opened = openingTime.value(at: now)
        frame.shift = reduced ? 0 : -width * push
        frame.signupShift = reduced ? 0 : width * (1 - push)

        // openProgress (tsx:192): the lid. `lidOpen` is only ever 0.
        if manual {
            frame.openProgress = opening ? Motion.fraction(opened, 0, 0.22) : 0
        } else {
            frame.openProgress = t >= 8.75 ? 0 : Motion.fraction(t, 0.333, 0.583)
        }
        // confettiAge (tsx:193).
        if manual {
            frame.confettiAge = opening ? opened - 0.22 : burst.value(at: now)
        } else {
            frame.confettiAge = t >= 4.05 && t < 4.9 ? t - 4.05 : t - 0.583
        }
        // giftStyle (tsx:198).
        if manual {
            frame.giftOpacity = opening ? (opened < 0.22 ? 1 : 0) : giftAlpha.value(at: now)
        } else {
            frame.giftOpacity = Motion.giftAlpha(t)
        }
        // offerStyle (tsx:199-203).
        let reveal: Double
        let alpha: Double
        if manual {
            reveal = opening ? Motion.enter(Motion.fraction(opened + 0.363, 0.617, 1.1)) : 1
            alpha = opening ? Motion.offerAlpha(opened + 0.363) : offerAlpha.value(at: now)
        } else {
            reveal = Motion.enter(Motion.fraction(t, 0.617, 1.1))
            alpha = Motion.offerAlpha(t)
        }
        frame.offerOpacity = alpha
        frame.offerScale = reduced ? 1 : 0.64 + 0.36 * reveal
        // offerWashStyle (tsx:204-207).
        if manual {
            if opening {
                frame.washOpacity = Motion.fraction(opened, 0.22, 0.304)
            } else {
                frame.washOpacity = (stage == .offer || stage == .exit || stage == .signup) ? 1 : 0
            }
        } else {
            frame.washOpacity = Motion.fraction(t, 0.583, 0.667) * (1 - Motion.phase(t, 4.9, 4.92))
        }
        // signupStyle (tsx:208).
        frame.signupOpacity = manual ? signupAlpha.value(at: now) : Motion.signupAlpha(t)
        frame.countdown = manual ? .wall(start: manualStart) : .loop(Self.loopLabel(t))

        // The skip sheet (ExitSheet).
        let visible = stage == .exit || manualExitVisible
        updateSheetEffect(visible: visible, now: now)
        let travel = sheetTravel
        if panelHeight <= 0 { panelHeight = metrics.height * 0.598 }
        var sheet = ConfettiGiftOfferSheetFrame()
        sheet.visible = visible
        sheet.autoDriven = !manual
        let offset = sheetOffset.value(at: now)
        if reduced {
            sheet.translateY = 0
        } else if !manual {
            sheet.translateY = panelHeight * (1 - Motion.sheet(t))
        } else {
            sheet.translateY = offset
        }
        sheet.scrim = manual ? max(0, 1 - offset / travel) : Motion.phase(t, 3.233, 3.4)
        sheet.outgoingX = frame.shift
        frame.sheet = sheet

        settleWake(now: now)
        return frame
    }

    /// "Offer expire in" under the loop clock: `00:00` until the ticket settles, then 59:59 counting down a second a tick.
    private static func loopLabel(_ t: Double) -> String {
        if t < 1.1 { return "00:00" }
        let tick = Int((t - 1.1).rounded(.down))
        return "59:" + String(format: "%02d", 59 - (tick % 60))
    }

    private func apply(_ config: ConfettiGiftOfferConfig) {
        let reducedChanged = configured && config.reduced != reduced
        autoPlay = config.autoPlay
        previewTime = config.previewTime
        reduced = config.reduced
        metrics = config.metrics
        onContinue = config.onContinue
        if !configured {
            configured = true
            if let previewTime { frozenTime = Motion.normalize(previewTime, period: Motion.loop) }
            if reduced { frozenTime = Motion.settled }
            flushEffects()
        } else if reducedChanged {
            flushEffects()
        }
    }

    /// `useSceneClock`: the time the frame shows. It runs only while autoPlay owns it; a touch, Reduce Motion,
    /// a backgrounded app or a preview time freeze it.
    private func clockTime(_ now: Double) -> Double {
        if let previewTime {
            frozenTime = Motion.normalize(previewTime, period: Motion.loop)
            return frozenTime
        }
        if reduced {
            frozenTime = Motion.settled
            return frozenTime
        }
        if autoPlay && !manual && foreground {
            let start = anchor ?? now - frozenTime
            anchor = start
            frozenTime = max(0, now - start).truncatingRemainder(dividingBy: Motion.loop)
            return frozenTime
        }
        anchor = nil
        return frozenTime
    }

    private var clockRunning: Bool {
        autoPlay && !manual && foreground && !reduced && previewTime == nil
    }

    private func wantsFrames() -> Bool {
        clockRunning || sheetPanning || tracks.contains { $0.isRunning }
    }

    /// Pauses the timeline once nothing moves (an unpaused timeline would keep the CPU busy).
    private func settleWake(now: Double) {
        guard !wantsFrames(), wake.needsFrames else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self, !self.wantsFrames() else { return }
            self.wake.needsFrames = false
        }
    }

    private func requestFrames() {
        if !wake.needsFrames { wake.needsFrames = true }
    }

    // MARK: - Transactions and the stage effect

    /// One event: whatever it sets is committed together, then the stage effect runs once if its inputs changed.
    private func transaction(_ body: () -> Void) {
        transactionDepth += 1
        body()
        transactionDepth -= 1
        if transactionDepth == 0 {
            flushEffects()
            requestFrames()
        }
    }

    private func flushEffects() {
        guard transactionDepth == 0 else { return }
        let key = EffectKey(stage: manualStage, manual: manual, foreground: foreground, reduced: reduced)
        guard key != effectKey else { return }
        effectKey = key
        runStageEffect()
    }

    /// The big `useEffect` (tsx:163-181): after the stage (or manual, foreground, Reduce Motion) changes,
    /// the touch-driven alphas animate to where the stage wants them.
    private func runStageEffect() {
        effectCleanup?()
        effectCleanup = nil
        let now = Self.now()
        guard foreground else {
            heldControls = 0
            reading = (false, false)
            clearQuiet()
            for track in tracks where track !== sheetOffset { track.cancel(at: now) }
            return
        }
        guard manual else { return }
        let duration = reduced ? 0 : 0.22
        let stage = manualStage
        if stage == .opening {
            let remaining = max(0, 0.74 - openingTime.value(at: now))
            openingTime.timing(to: 0.74, duration: reduced ? 0 : remaining, easing: .linear, now: now) { [weak self] in
                self?.finishOpening()
            }
            effectCleanup = { [weak self] in
                self?.openingTime.cancel(at: Self.now())
            }
            return
        }
        giftAlpha.timing(to: stage == .gift ? 1 : 0, duration: duration, easing: .out, now: now)
        if stage == .signup {
            signupAlpha.set(1)
            offerAlpha.timing(to: 0, duration: 0, easing: .linear, delay: reduced ? 0 : 0.4, now: now)
            let travel = reduced ? 0 : max(0, 1 - manualPush.value(at: now)) * 0.4
            manualPush.timing(to: 1, duration: travel, easing: .out, now: now) { [weak self] in
                self?.finishPush()
            }
        } else {
            offerAlpha.timing(to: stage == .offer || stage == .exit ? 1 : 0, duration: duration, easing: .out, now: now)
            signupAlpha.timing(to: 0, duration: duration, easing: .out, now: now)
        }
    }

    /// `finishOpening` (tsx:161): the gift is gone, the ticket is up and the confetti keeps falling.
    private func finishOpening() {
        transaction {
            let now = Self.now()
            giftAlpha.set(0)
            offerAlpha.set(1)
            burst.set(reduced ? -1 : 0.52)
            if !reduced { burst.timing(to: 1.7, duration: 1.18, easing: .linear, now: now) }
            ownedStage = .offer
            manualStage = .offer
            armQuiet(.offer)
        }
    }

    /// `finishPush` (tsx:162).
    private func finishPush() {
        transaction {
            manualExitVisible = false
        }
    }

    // MARK: - Ownership (touch pauses autoplay; two quiet seconds resume it)

    private func clearQuiet() {
        quiet?.cancel()
        quiet = nil
    }

    private func armQuiet(_ next: Stage) {
        clearQuiet()
        if heldControls > 0 || reading.dragging || reading.momentum || !autoPlay || reduced || !foreground || previewTime != nil { return }
        if next != .gift && next != .offer { return }
        let item = DispatchWorkItem { [weak self] in
            self?.transaction { self?.resumeFrom(next) }
        }
        quiet = item
        DispatchQueue.main.asyncAfter(deadline: .now() + 2, execute: item)
    }

    /// `resumeFrom` (tsx:141): back to the loop, at the start of the gift or of the settled offer.
    private func resumeFrom(_ next: Stage) {
        guard next == .gift || next == .offer else { return }
        frozenTime = next == .gift ? 0 : 1.5
        anchor = nil
        ownedStage = nil
        manual = false
    }

    private var currentStage: Stage {
        manual ? manualStage : Motion.stage(at: clockTime(Self.now()))
    }

    /// `own` (tsx:143): a touch takes the screen over from the loop, freezing every value where the loop had it.
    private func own(_ next: Stage) {
        clearQuiet()
        if !manual {
            let t = clockTime(Self.now())
            giftAlpha.set(Motion.giftAlpha(t))
            offerAlpha.set(Motion.offerAlpha(t))
            signupAlpha.set(Motion.signupAlpha(t))
            manualPush.set(Motion.push(t))
            manualStart = Date()
        }
        ownedStage = next
        manual = true
        manualStage = next
    }

    // MARK: - Touch entry points

    func pressIn() {
        guard interactive else { return }
        transaction {
            heldControls += 1
            clearQuiet()
            if !manual { own(currentStage) }
        }
    }

    func pressOut() {
        guard interactive, heldControls > 0 else { return }
        transaction {
            heldControls -= 1
            if heldControls == 0 { armQuiet(ownedStage ?? currentStage) }
        }
    }

    /// `go` (tsx:160).
    private func go(_ next: Stage) {
        own(next)
        if next == .gift || next == .offer {
            manualExitVisible = false
            let now = Self.now()
            if reduced || next == .gift {
                manualPush.set(0)
            } else {
                manualPush.timing(to: 0, duration: 0.22, easing: .out, now: now)
            }
        }
        armQuiet(next)
    }

    /// "Open now" (tsx:185).
    func open() {
        guard interactive else { return }
        transaction {
            own(.opening)
            openingTime.set(0)
            manualPush.set(0)
            impact.impactOccurred()
        }
    }

    /// "Back to offer".
    func stay() {
        guard interactive else { return }
        transaction { go(.offer) }
    }

    /// The offer's close chip.
    func leave() {
        guard interactive else { return }
        transaction { go(.exit) }
    }

    /// "Claim your offer" pushes the account screen in with the offer accepted.
    func claim() {
        guard interactive else { return }
        transaction { pushToSignup(accepted: true, retainSheet: false) }
    }

    /// "Skip this offer forever".
    func skip() {
        guard interactive else { return }
        transaction { pushToSignup(accepted: false, retainSheet: true) }
    }

    private func pushToSignup(accepted offerAccepted: Bool, retainSheet: Bool) {
        accepted = offerAccepted
        manualExitVisible = retainSheet
        own(.signup)
    }

    /// "Replay the gift".
    func reset() {
        guard interactive else { return }
        transaction {
            accepted = false
            go(.gift)
        }
    }

    /// A sign-in button (tsx:214): the demo ends here.
    func finish(_ provider: ConfettiGiftOfferProvider) {
        guard interactive else { return }
        transaction {
            own(.complete)
            onContinue?(provider, accepted)
            notification.notificationOccurred(.success)
        }
    }

    // MARK: Reading (a drag on a scene's scroll view)

    func readingDragBegan() {
        guard interactive else { return }
        transaction {
            reading.dragging = true
            own(currentStage)
        }
    }

    func readingDragEnded() {
        guard interactive else { return }
        transaction {
            reading.dragging = false
            armQuiet(currentStage)
        }
    }

    func readingMomentumBegan() {
        guard interactive else { return }
        transaction {
            reading.momentum = true
            clearQuiet()
        }
    }

    func readingMomentumEnded() {
        guard interactive else { return }
        transaction {
            reading.momentum = false
            armQuiet(currentStage)
        }
    }

    // MARK: - The skip sheet

    /// `maxHeight = max(240, height - inset.top - 20)`, `travel = maxHeight + 32` (tsx:104).
    private var sheetTravel: Double {
        Double(max(240, metrics.height - metrics.top - 20) + 32)
    }

    /// The sheet's effect (tsx:106): it springs up when it becomes visible, and parks off screen when it is not.
    private func updateSheetEffect(visible: Bool, now: Double) {
        let key = SheetKey(visible: visible, foreground: foreground, reduced: reduced, travel: sheetTravel)
        guard key != sheetKey else { return }
        sheetOffset.cancel(at: now)
        sheetKey = key
        if !visible || !foreground {
            if !visible { sheetOffset.set(key.travel) }
            return
        }
        if reduced {
            sheetOffset.set(0)
        } else {
            sheetOffset.spring(to: 0, duration: 0.4, dampingRatio: 0.8, now: now)
        }
    }

    /// The scrim, the system back gesture and the accessibility escape (`close`, tsx:107).
    func closeSheet() {
        guard interactive else { return }
        transaction {
            let now = Self.now()
            if !manual { sheetOffset.set(Double(panelHeight) * (1 - Motion.sheet(clockTime(now)))) }
            own(.exit)
            if reduced {
                pushToSignup(accepted: false, retainSheet: true)
            } else {
                sheetOffset.spring(to: sheetTravel, duration: 0.3, dampingRatio: 1, clamp: true, now: now) { [weak self] in
                    self?.skip()
                }
            }
        }
    }

    /// Pan `onStart` (tsx:108).
    func sheetPanBegan() {
        guard interactive else { return }
        transaction {
            let now = Self.now()
            sheetOffset.cancel(at: now)
            if !manual { sheetOffset.set(Double(panelHeight) * (1 - Motion.sheet(clockTime(now)))) }
            sheetInitial = sheetOffset.value(at: now)
            sheetPanning = true
            own(.exit)
        }
    }

    /// Pan `onUpdate`: follows the finger, and drags against a rubber band above the rest position.
    func sheetPanChanged(translationY: Double) {
        guard interactive, sheetPanning else { return }
        let next = sheetInitial + translationY
        sheetOffset.set(next >= 0 ? next : next / (1 + abs(next) / 80) * 0.35)
        requestFrames()
    }

    /// Pan `onEnd`: past 90 pt or faster than 700 pt/s it leaves, otherwise it springs home.
    func sheetPanEnded(velocityY: Double, success: Bool) {
        guard interactive, sheetPanning else { return }
        sheetPanning = false
        transaction {
            let now = Self.now()
            if !success {
                returnSheet(now: now, velocity: 0)
                return
            }
            if sheetOffset.value(at: now) > 90 || velocityY > 700 {
                if reduced {
                    pushToSignup(accepted: false, retainSheet: true)
                } else {
                    sheetOffset.spring(to: sheetTravel, duration: 0.4, dampingRatio: 1, velocity: velocityY, clamp: true, now: now) { [weak self] in
                        self?.skip()
                    }
                }
            } else {
                returnSheet(now: now, velocity: velocityY)
            }
        }
    }

    private func returnSheet(now: Double, velocity: Double) {
        if reduced {
            sheetOffset.set(0)
        } else {
            sheetOffset.spring(to: 0, duration: 0.4, dampingRatio: 0.8, velocity: velocity, now: now)
        }
    }

    /// `beginReading` (tsx:112): a drag on the sheet's own scroll view takes the sheet over.
    func sheetReadingBegan() {
        guard interactive else { return }
        transaction {
            if !manual { sheetOffset.spring(to: 0, duration: 0.4, dampingRatio: 0.8, now: Self.now()) }
            own(.exit)
        }
    }

    // MARK: - Lifecycle

    /// `AppState`: leaving the foreground cancels every animation and every pending resume.
    func setForeground(_ active: Bool) {
        guard active != foreground else { return }
        let was = foreground
        foreground = active
        transaction {
            if active && !was && manual { armQuiet(manualStage) }
        }
    }

    func stop() {
        clearQuiet()
        let now = Self.now()
        for track in tracks { track.cancel(at: now) }
    }
}
