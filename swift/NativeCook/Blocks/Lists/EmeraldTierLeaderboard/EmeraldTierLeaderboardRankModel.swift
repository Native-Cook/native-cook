import UIKit

/// Port of `useEmeraldRankMotion`: the demo owns a clock; input owns a separate, monotonic rank
/// journey. A plain reference type read by the frame loop (no per-frame SwiftUI state).
/// All times are seconds on the `TimelineView` clock (RN used the frame callback's ms timestamp).
final class EmeraldTierRankModel {
    typealias M = EmeraldTierMotion

    // Props (updated by the view every body pass).
    var autoPlay = true
    var previewTime: Double?
    var reduced = false
    /// `Math.max(120, 241.5 * scale)`: screen points that one full phase of drag covers.
    var dragWidth = 241.5

    // Shared values.
    let rankPhase: EmeraldTierAnimator
    var demoTime: Double
    var demoAnchor = -1.0
    var lastTick = -1.0
    var manual = false
    var animating = false
    var dragging = false
    var target = 0.0
    var dragStart = 0.0
    var dragTranslation = 0.0
    var holdRemaining = 2.0
    var generation = 0
    var needsRecovery = false
    var responseAllowed = true
    var hapticArmed = false
    var foreground = true
    var focused = false

    init(previewTime: Double?) {
        let initial = M.clamp(previewTime ?? 0, 0, M.LOOP)
        rankPhase = EmeraldTierAnimator(M.demoPhase(initial / M.LOOP))
        demoTime = initial
    }

    /// `frame = useDerivedValue(() => emeraldRankFrame(rankPhase, reduced, responseAllowed))`
    var frame: M.Frame { M.rankFrame(rankPhase.value, reduced: reduced, allowResponse: responseAllowed) }
    /// `demoRunning = !manual`
    var demoRunning: Bool { !manual }

    // MARK: Haptic

    private func haptic() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if UIAccessibility.isVoiceOverRunning && focused && foreground {
            UIAccessibility.post(notification: .announcement,
                                 argument: "Theo Park is first with 1050 points. Ari Vale second, Milo Chen third, Juno Reed fourth.")
        }
    }

    // MARK: Journeys

    func settle(_ destination: Double, velocity: Double, notify: Bool) {
        generation += 1
        let owner = generation
        rankPhase.cancel()
        manual = true
        animating = true
        dragging = false
        target = destination
        responseAllowed = destination == 1
        hapticArmed = notify && destination == 1
        demoAnchor = -1
        holdRemaining = 2
        rankPhase.spring(to: destination, duration: 420, dampingRatio: abs(velocity) > 0.35 ? 0.88 : 1,
                         velocity: M.clamp(velocity, -4, 4), overshootClamping: true) { [weak self] finished in
            guard let self, finished, self.generation == owner else { return }
            self.animating = false
            self.holdRemaining = 2
        }
    }

    func takeLead(preserveHaptic: Bool) {
        let current = rankPhase.value
        generation += 1
        let owner = generation
        rankPhase.cancel()
        manual = true
        animating = true
        dragging = false
        target = 1
        responseAllowed = true
        if !preserveHaptic { hapticArmed = true }
        demoAnchor = -1
        holdRemaining = 2
        // Constant phase speed feeds the same eased choreography used by the demo.
        rankPhase.timing(to: 1, duration: max(120, M.TAKEOVER_MS * (1 - M.clamp(current)))) { [weak self] finished in
            guard let self, finished, self.generation == owner else { return }
            self.animating = false
            self.holdRemaining = 2
        }
    }

    func replay() {
        if previewTime != nil || !focused || !foreground { return }
        if reduced {
            generation += 1
            rankPhase.cancel()
            manual = true; animating = false; dragging = false; target = 1
            responseAllowed = false; demoAnchor = -1; holdRemaining = 2
            hapticArmed = rankPhase.value < 0.5
            rankPhase.set(1)
            return
        }
        let continuing = manual && animating && target == 1
        if rankPhase.value < 0.999 || animating {
            takeLead(preserveHaptic: continuing)
            return
        }
        // A completed replay travels back visibly; another tap can redirect this return instantly.
        generation += 1
        let owner = generation
        rankPhase.cancel()
        manual = true; animating = true; dragging = false; target = 0
        responseAllowed = false; hapticArmed = false; demoAnchor = -1
        rankPhase.timing(to: 0, duration: 900) { [weak self] finished in
            guard let self else { return }
            if finished && self.generation == owner { self.takeLead(preserveHaptic: false) }
        }
    }

    // MARK: Lifecycle

    func suspend() {
        needsRecovery = manual || animating || dragging
        generation += 1
        rankPhase.cancel()
        animating = false; dragging = false; hapticArmed = false
        demoAnchor = -1; lastTick = -1
    }

    func recover() {
        if previewTime != nil || !focused || !foreground { return }
        demoAnchor = -1; lastTick = -1
        if !needsRecovery { return }
        needsRecovery = false
        if reduced { rankPhase.set(M.snapTarget(rankPhase.value, 0)); return }
        settle(M.snapTarget(rankPhase.value, 0), velocity: 0, notify: false)
    }

    func setFocused(_ value: Bool) {
        focused = value
        if value { recover() } else { suspend() }
    }

    func setForeground(_ value: Bool) {
        foreground = value
        if value { recover() } else { suspend() }
    }

    /// `useEffect` on `previewTime`: a frozen frame of the demo loop.
    func applyPreviewTime() {
        guard let previewTime else { return }
        generation += 1
        rankPhase.cancel()
        manual = false; animating = false; dragging = false; hapticArmed = false
        let seconds = M.clamp(previewTime, 0, M.LOOP)
        needsRecovery = false
        demoTime = seconds; demoAnchor = -1
        responseAllowed = seconds < M.DEMO_RETURN_START
        rankPhase.set(M.demoPhase(seconds / M.LOOP))
    }

    /// `useEffect` on `reduced`: show the final, legible state and stop the loop.
    func applyReduced() {
        if !reduced || previewTime != nil { return }
        generation += 1
        rankPhase.cancel()
        rankPhase.set(M.snapTarget(rankPhase.value, 0))
        manual = true; animating = false; dragging = false; hapticArmed = false
        responseAllowed = false; target = rankPhase.value >= 0.5 ? 1 : 0
        holdRemaining = 2; demoAnchor = -1
    }

    // MARK: Frame callback

    /// One step of the frame clock. Call once per displayed frame with the timeline's time in seconds.
    func tick(now: Double) {
        rankPhase.step(now: now)
        runFrameCallback(now: now)
        // useAnimatedReaction: the haptic fires once the crown has seated.
        if hapticArmed && !dragging && rankPhase.value >= EmeraldTierRecast.SEAT_PHASE {
            if focused && foreground { hapticArmed = false; haptic() }
        }
    }

    private func runFrameCallback(now: Double) {
        if previewTime != nil || reduced || !focused || !foreground { return }
        let previous = lastTick
        let elapsed = previous < 0 ? 0 : max(0, now - previous)
        lastTick = now
        if dragging || animating || !autoPlay { return }
        if manual {
            let remaining = max(0, holdRemaining - elapsed)
            holdRemaining = remaining
            if remaining > 0 { return }
            // Resume at an identical rest pose, then use the demo's guided return.
            demoTime = target == 1 ? M.DEMO_RETURN_START : 0
            demoAnchor = -1
            manual = false
        }
        if demoAnchor < 0 { demoAnchor = now - demoTime }
        let seconds = (now - demoAnchor).truncatingRemainder(dividingBy: M.LOOP)
        demoTime = seconds
        responseAllowed = seconds < M.DEMO_RETURN_START
        rankPhase.set(M.demoPhase(seconds / M.LOOP))
    }

    // MARK: Pan (activeOffsetY +-8, failOffsetX +-14, one pointer)

    /// `translationY` is the touch's total vertical travel in screen points.
    func panStart(translationY: Double) {
        if !focused || !foreground { return }
        generation += 1
        rankPhase.cancel()
        manual = true; animating = false; dragging = true; hapticArmed = false
        responseAllowed = false; demoAnchor = -1
        dragStart = M.unrubberPhase(rankPhase.value)
        dragTranslation = translationY
    }

    func panUpdate(translationY: Double) {
        if !dragging || !focused || !foreground { return }
        rankPhase.set(M.rubberPhase(dragStart + -(translationY - dragTranslation) / dragWidth))
    }

    func panEnd(velocityY: Double) {
        if !dragging { return }
        let velocity = -velocityY / dragWidth
        settle(M.snapTarget(rankPhase.value, velocity), velocity: velocity, notify: true)
    }

    /// onFinalize without a successful end: cancellation settles too; it cannot leave the two portraits suspended together.
    func panCancel() {
        if !dragging { return }
        settle(M.snapTarget(rankPhase.value, 0), velocity: 0, notify: false)
    }
}
