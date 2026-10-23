import Observation
import QuartzCore
import SwiftUI

@MainActor
@Observable
final class GiftSlotDropModel {
    enum Phase: Int, Equatable { case choosing, pulling, swiping, inserting, activating, offer, closing }
    enum Haptic { case select, armed, insert, success, light }
    enum DragMode { case undecided, pull, swipe }

    struct ReleasedPose: Equatable { var x = 0.0, y = 0.0, scale = 1.0 }
    struct CardPose: Equatable { var x = 0.0, y = 0.0, scale = 1.0, turn = 0.0, opacity = 1.0, lean = 0.0, roll = 0.0 }
    struct OfferPose: Equatable { var y = 0.0, scale = 1.0, opacity = 0.0, roll = 0.0 }
    struct ConfettiPiece: Equatable { var x = 0.0, y = 0.0, vx = 0.0, vy = 0.0, angle = 0.0, spin = 0.0, life = 0.0; var color = 0 }

    private struct Touch {
        var active = false, scripted = false, onCard = false
        var mode = DragMode.undecided
        var origin = CGPoint.zero
        var offsetOrigin = 0.0
    }

    private struct SpringValue {
        var value: Double
        var velocity = 0.0
        var target: Double?
        var parameters = GiftSlotDropTokens.Springs.snap

        mutating func set(_ newValue: Double) { value = newValue; velocity = 0; target = nil }
        mutating func spring(to target: Double, velocity: Double? = nil, parameters: GiftSlotDropTokens.Spring) {
            self.target = target
            self.parameters = parameters
            if let velocity { self.velocity = velocity }
        }
        mutating func advance(_ dt: Double) {
            guard let target else { return }
            let steps = max(1, Int(ceil(dt / (1.0 / 120.0))))
            let h = dt / Double(steps)
            for _ in 0..<steps {
                let acceleration = (-parameters.stiffness * (value - target) - parameters.damping * velocity) / parameters.mass
                velocity += acceleration * h
                value += velocity * h
            }
            if abs(value - target) < 0.0005, abs(velocity) < 0.005 { set(target) }
        }
    }

    let autoPlay: Bool
    var reducedMotion = false
    private(set) var phase = Phase.choosing
    private(set) var selected = GiftSlotDropTokens.startIndex
    private(set) var armed = false
    private(set) var clock = 0.0
    private(set) var releasedAt = -1.0
    private(set) var releasedFrom = ReleasedPose()
    private(set) var closeAt = -1.0
    private(set) var confetti: [ConfettiPiece] = []

    private var offsetSpring = SpringValue(value: Double(GiftSlotDropTokens.startIndex))
    private var pullXSpring = SpringValue(value: 0)
    private var pullYSpring = SpringValue(value: 0)
    private var liftSpring = SpringValue(value: 1)
    private var touch = Touch()
    private var width: CGFloat = 393
    private var lastTimestamp: CFTimeInterval?
    private var phaseStartedAt = 0.0
    private var scriptInterruptedByUser = false
    private var scriptTime = 0.0
    private var quietTime = 0.0
    private var scriptTouching = false
    private var firedConfetti = false
    private var haptics: [Haptic] = []

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
    }

    var offset: Double { offsetSpring.value }
    var pullX: Double { pullXSpring.value }
    var pullY: Double { pullYSpring.value }
    var gift: GiftSlotDropTokens.Gift { GiftSlotDropTokens.gifts[selected] }
    var isChoosing: Bool { phase == .choosing || phase == .pulling || phase == .swiping }
    var isActivating: Bool { phase == .inserting || phase == .activating }
    var showsOffer: Bool { phase == .offer || phase == .closing }
    var needsFrames: Bool {
        autoPlay || touch.active || phase != .choosing || offsetSpring.target != nil || pullXSpring.target != nil
            || pullYSpring.target != nil || liftSpring.target != nil || confetti.contains { $0.life > 0 }
    }

    func configure(width: CGFloat, reducedMotion: Bool) {
        self.width = width
        self.reducedMotion = reducedMotion
    }

    func advance(to timestamp: CFTimeInterval) {
        guard let lastTimestamp else { self.lastTimestamp = timestamp; return }
        let elapsed = max(0, timestamp - lastTimestamp)
        self.lastTimestamp = timestamp
        guard elapsed > 0 else { return }
        let dt = min(0.1, elapsed)
        let milliseconds = elapsed * 1_000
        clock += milliseconds
        offsetSpring.advance(dt)
        pullXSpring.advance(dt)
        pullYSpring.advance(dt)
        liftSpring.advance(dt)
        updateTimeline(dt: dt)
        updateScript(milliseconds: milliseconds)
    }

    func touchBegan(at point: CGPoint, scripted: Bool = false) {
        guard phase == .choosing else { return }
        let onCard = abs(point.x - width / 2) < GiftSlotDropTokens.Layout.cardW / 2 + 12
            && abs(point.y - GiftSlotDropTokens.Layout.cardY) < GiftSlotDropTokens.Layout.cardH / 2 + 12
        touch = Touch(active: true, scripted: scripted, onCard: onCard, mode: .undecided, origin: point, offsetOrigin: offset)
        offsetSpring.set(offset)
        if onCard { liftSpring.spring(to: GiftSlotDropTokens.Drag.lift, parameters: GiftSlotDropTokens.Springs.lift) }
    }

    func touchMoved(to point: CGPoint) {
        guard touch.active else { return }
        let dx = point.x - touch.origin.x
        let dy = point.y - touch.origin.y
        if touch.mode == .undecided, hypot(dx, dy) > GiftSlotDropTokens.Drag.decide {
            touch.mode = abs(dx) > abs(dy) || !touch.onCard || dy < 0 ? .swipe : .pull
            setPhase(touch.mode == .pull ? .pulling : .swiping)
            if touch.mode == .swipe { liftSpring.spring(to: 1, parameters: GiftSlotDropTokens.Springs.lift) }
        }
        if touch.mode == .swipe {
            var next = touch.offsetOrigin - Double(dx / GiftSlotDropTokens.Layout.spacing)
            if next < 0 { next *= 0.3 }
            let last = Double(GiftSlotDropTokens.gifts.count - 1)
            if next > last { next = last + (next - last) * 0.3 }
            offsetSpring.set(next)
        } else if touch.mode == .pull {
            pullXSpring.set(Double(dx * 0.3))
            pullYSpring.set(Double(dy < 0 ? dy * GiftSlotDropTokens.Drag.upResist : dy))
            setArmed(dy >= GiftSlotDropTokens.Drag.commit)
        }
    }

    func touchEnded(velocity: CGVector, cancelled: Bool = false) {
        guard touch.active else { return }
        let mode = touch.mode
        touch.active = false
        liftSpring.spring(to: 1, parameters: GiftSlotDropTokens.Springs.lift)
        if mode == .swipe {
            let fling = abs(velocity.dx) > GiftSlotDropTokens.Drag.swipeVelocity ? (velocity.dx < 0 ? 0.5 : -0.5) : 0
            snap(to: Int((offset + Double(fling)).rounded()), velocity: Double(-velocity.dx / GiftSlotDropTokens.Layout.spacing))
            setPhase(.choosing)
        } else if mode == .pull, armed, !cancelled {
            setArmed(false)
            release()
        } else if mode == .pull {
            pullXSpring.spring(to: 0, velocity: Double(velocity.dx), parameters: GiftSlotDropTokens.Springs.back)
            pullYSpring.spring(to: 0, velocity: Double(velocity.dy), parameters: GiftSlotDropTokens.Springs.back)
            setArmed(false)
            setPhase(.choosing)
        } else {
            setPhase(.choosing)
        }
    }

    func takeOver() {
        if !scriptInterruptedByUser, touch.scripted, touch.active {
            setArmed(false)
            touchEnded(velocity: .zero, cancelled: true)
        }
        scriptInterruptedByUser = true
        quietTime = 0
        scriptTouching = true
    }

    func letGo() { scriptTouching = false; quietTime = 0 }

    func activate() {
        guard phase == .choosing else { return }
        touch.scripted = false
        pullXSpring.set(0)
        pullYSpring.set(0)
        release()
    }

    func step(_ direction: Int) {
        guard phase == .choosing else { return }
        snap(to: selected + direction, velocity: 0)
    }

    func close() {
        close(at: clock)
    }

    private func close(at timestamp: Double) {
        guard phase == .offer else { return }
        closeAt = timestamp
        setPhase(.closing)
        emit(.light)
    }

    func userClose() {
        takeOver()
        close()
        letGo()
    }

    func drainHaptics() -> [Haptic] { defer { haptics.removeAll(keepingCapacity: true) }; return haptics }

    func cardPose(index: Int) -> CardPose {
        let layout = GiftSlotDropTokens.Layout.self
        let rel = Double(index) - offset
        let selectedCard = index == selected
        var pose = CardPose(
            x: Double(width) / 2 + rel * Double(layout.spacing), y: Double(layout.cardY),
            scale: 1 - (1 - Double(layout.sideScale)) * min(1, abs(rel)),
            turn: max(-1, min(1, -rel)) * Double(layout.turnDeg)
        )
        if selectedCard, phase == .choosing, releasedAt < 0, !reducedMotion {
            pose.y += Double(GiftSlotDropTokens.Ambient.floatPt) * sin(2 * .pi * clock / GiftSlotDropTokens.Ambient.floatMs)
        }
        if selectedCard, [.choosing, .pulling, .swiping].contains(phase), releasedAt < 0 {
            pose.x += pullX; pose.y += pullY
            pose.scale *= pullScale(pullY) * liftSpring.value
            pose.lean = clamp(pullY / Double(GiftSlotDropTokens.Drag.full)) * Double(GiftSlotDropTokens.Drag.leanDeg)
            pose.roll = reducedMotion ? 0 : max(-Double(GiftSlotDropTokens.Ambient.rollMax), min(Double(GiftSlotDropTokens.Ambient.rollMax), pullX * GiftSlotDropTokens.Ambient.rollPerPt))
        }
        if releasedAt >= 0 {
            let elapsed = clock - releasedAt
            if selectedCard {
                if reducedMotion {
                    pose.x = Double(width) / 2 + releasedFrom.x; pose.y = Double(layout.cardY) + releasedFrom.y; pose.scale = releasedFrom.scale
                    pose.opacity = 1 - clamp(elapsed / GiftSlotDropTokens.reducedMs)
                } else {
                    let progress = 1 - pow(1 - clamp(elapsed / GiftSlotDropTokens.Insert.centreMs), 3)
                    pose.lean = Double(GiftSlotDropTokens.Drag.leanDeg)
                    let tall = Double(layout.cardH) * GiftSlotDropTokens.Drag.minScale * cos(Double(GiftSlotDropTokens.Drag.leanDeg) * .pi / 180)
                    let above = Double(layout.slotY) - tall / 2
                    pose.x = Double(width) / 2 + releasedFrom.x * (1 - progress)
                    pose.y = Double(layout.cardY) + releasedFrom.y + (above - Double(layout.cardY) - releasedFrom.y) * progress
                    pose.scale = releasedFrom.scale + (GiftSlotDropTokens.Drag.minScale - releasedFrom.scale) * progress
                    let sink = clamp((elapsed - GiftSlotDropTokens.Insert.centreMs) / GiftSlotDropTokens.Insert.sinkMs)
                    let eased = cubicInOut(sink)
                    pose.y += tall * 1.1 * eased
                    if elapsed > GiftSlotDropTokens.Insert.centreMs + GiftSlotDropTokens.Insert.sinkMs { pose.opacity = 0 }
                }
            } else { pose.opacity = 1 - clamp(elapsed / 250) }
            if closeAt >= 0 {
                let delay = reducedMotion ? 0 : GiftSlotDropTokens.Close.ms
                let duration = reducedMotion ? GiftSlotDropTokens.reducedMs : GiftSlotDropTokens.Close.returnMs
                let returnProgress = clamp((clock - closeAt - delay) / duration)
                pose.x = Double(width) / 2 + rel * Double(layout.spacing); pose.y = Double(layout.cardY)
                pose.scale = (0.94 + 0.06 * returnProgress); pose.opacity = returnProgress; pose.lean = 0; pose.roll = 0
            }
        }
        return pose
    }

    func offerPose() -> OfferPose {
        guard releasedAt >= 0 else { return OfferPose() }
        let elapsed = clock - releasedAt - milestones.printStart
        guard elapsed >= 0 else { return OfferPose() }
        var pose = OfferPose(opacity: 1)
        if reducedMotion {
            pose.y = Double(GiftSlotDropTokens.Layout.offerY); pose.scale = 1
            pose.opacity = clamp(elapsed / GiftSlotDropTokens.reducedMs); pose.roll = 1
        } else {
            var fed = 0.0
            for index in 0..<GiftSlotDropTokens.Print.feeds {
                let start = Double(index) * (GiftSlotDropTokens.Print.feedMs + GiftSlotDropTokens.Print.feedGap)
                fed += GiftSlotDropTokens.Print.feedShare * backOut((elapsed - start) / GiftSlotDropTokens.Print.feedMs)
            }
            let end = Double(GiftSlotDropTokens.Print.feeds) * (GiftSlotDropTokens.Print.feedMs + GiftSlotDropTokens.Print.feedGap)
            let startScale = GiftSlotDropTokens.Print.startScale
            let fedY = Double(GiftSlotDropTokens.Layout.slotY) - fed * Double(GiftSlotDropTokens.Layout.offerH) * startScale + Double(GiftSlotDropTokens.Layout.offerH) * startScale / 2
            let rise = spring((elapsed - end) / 1_000, stiffness: 170, damping: 20)
            pose.y = fedY + (Double(GiftSlotDropTokens.Layout.offerY) - fedY) * rise
            pose.scale = startScale + (1 - startScale) * rise
            let roll = clamp((elapsed - end + 100) / GiftSlotDropTokens.Print.rollMs)
            pose.roll = 1 - pow(1 - roll, 3)
        }
        if closeAt >= 0 {
            let duration = reducedMotion ? GiftSlotDropTokens.reducedMs : GiftSlotDropTokens.Close.ms
            let progress = clamp((clock - closeAt) / duration)
            pose.opacity *= 1 - progress
            if !reducedMotion { pose.scale *= 1 - 0.06 * progress * progress; pose.y -= 24 * progress * progress }
        }
        return pose
    }

    var slotGlow: Double {
        if releasedAt < 0 {
            return 0.5 + 0.5 * clamp(pullY / Double(GiftSlotDropTokens.Drag.commit))
                + (reducedMotion ? 0 : 0.08 * sin(2 * .pi * clock / GiftSlotDropTokens.Ambient.slotBreathMs))
        }
        let elapsed = clock - releasedAt
        if elapsed < milestones.sunk { return 1 }
        if elapsed < milestones.sunk + GiftSlotDropTokens.Insert.flashMs { return 1 + 0.6 * (1 - (elapsed - milestones.sunk) / GiftSlotDropTokens.Insert.flashMs) }
        if elapsed < milestones.printStart { return 0.85 + 0.25 * sin(((elapsed - milestones.sunk) / GiftSlotDropTokens.Activate.dotMs) * .pi) }
        if closeAt >= 0 { return 0.5 * clamp((clock - closeAt) / GiftSlotDropTokens.Close.returnMs) }
        return 1 - 0.9 * clamp((elapsed - milestones.printed) / 600)
    }

    var placeholderOpacity: Double { releasedAt >= 0 ? (phase == .inserting || phase == .activating ? 1 : 0) : clamp(pullY / 40) }
    var selectionFrameOpacity: Double { releasedAt >= 0 ? 0 : clamp(1 - abs(Double(selected) - offset) * 2) * (1 - clamp(pullY / 60)) }
    var warmOpacity: Double { GiftSlotDropTokens.Colors.stageWarm * clamp(pullY / Double(GiftSlotDropTokens.Drag.full)) }
    var offerPercent: Int { Int((50 * offerPose().roll).rounded()) }

    func detailProgress(_ index: Int) -> Double {
        guard phase == .offer else { return 0 }
        let delay = reducedMotion ? GiftSlotDropTokens.reducedMs : Double(GiftSlotDropTokens.Print.feeds) * (GiftSlotDropTokens.Print.feedMs + GiftSlotDropTokens.Print.feedGap) + GiftSlotDropTokens.Print.detailsDelay
        return clamp((clock - phaseStartedAt - delay - Double(index) * GiftSlotDropTokens.Print.stagger) / 320)
    }

    private var milestones: (sunk: Double, printStart: Double, printed: Double, risen: Double) {
        if reducedMotion {
            let printStart = GiftSlotDropTokens.reducedMs + GiftSlotDropTokens.Activate.ms
            let printed = printStart + GiftSlotDropTokens.reducedMs
            return (GiftSlotDropTokens.reducedMs, printStart, printed, printed)
        }
        let sunk = GiftSlotDropTokens.Insert.centreMs + GiftSlotDropTokens.Insert.sinkMs
        let printStart = sunk + GiftSlotDropTokens.Activate.ms
        let printed = printStart + Double(GiftSlotDropTokens.Print.feeds) * (GiftSlotDropTokens.Print.feedMs + GiftSlotDropTokens.Print.feedGap)
        return (sunk, printStart, printed, printed + 700)
    }

    private func updateTimeline(dt: Double) {
        if releasedAt >= 0 {
            let elapsed = clock - releasedAt
            if phase == .inserting, elapsed >= milestones.sunk { setPhase(.activating) }
            if phase == .activating, elapsed >= milestones.printStart { setPhase(.offer) }
            if !firedConfetti, elapsed >= milestones.printed { firedConfetti = true; spawnConfetti(); emit(.success) }
        }
        if closeAt >= 0 {
            let duration = reducedMotion ? GiftSlotDropTokens.reducedMs : GiftSlotDropTokens.Close.ms + GiftSlotDropTokens.Close.returnMs
            if clock - closeAt >= duration { resetAfterClose() }
        }
        guard !reducedMotion, !confetti.isEmpty else { return }
        for index in confetti.indices where confetti[index].life > 0 {
            confetti[index].vy += GiftSlotDropTokens.Print.gravity * dt
            confetti[index].vx *= 0.985
            confetti[index].x += confetti[index].vx * dt
            confetti[index].y += confetti[index].vy * dt
            confetti[index].angle += confetti[index].spin * dt
            confetti[index].life -= dt
        }
    }

    private func updateScript(milliseconds: Double) {
        guard autoPlay else { return }
        if scriptInterruptedByUser {
            let resting = phase == .offer || (phase == .choosing && abs(offset - offset.rounded()) < 0.01)
            quietTime = !scriptTouching && resting ? quietTime + milliseconds : 0
            if quietTime >= GiftSlotDropTokens.resumeAfterMs, phase == .offer, !scriptTouching { close(); quietTime = 0; return }
            if quietTime >= GiftSlotDropTokens.resumeAfterMs, selected != GiftSlotDropTokens.startIndex { snap(to: GiftSlotDropTokens.startIndex, velocity: 0); quietTime = 0; return }
            if quietTime >= GiftSlotDropTokens.resumeAfterMs, phase == .choosing {
                scriptInterruptedByUser = false
                scriptTime = 0
            }
            return
        }
        let previous = scriptTime
        let next = GiftSlotDropTokens.qaHoldAt.map { previous >= $0 ? previous : previous + milliseconds } ?? previous + milliseconds
        for swipe in GiftSlotDropTokens.Script.swipes { scriptedSwipe(previous: previous, now: next, swipe: swipe) }
        if previous < GiftSlotDropTokens.Script.pullDown, next >= GiftSlotDropTokens.Script.pullDown {
            touchBegan(at: CGPoint(x: width / 2 + 12, y: GiftSlotDropTokens.Layout.cardY + 30), scripted: true)
        }
        if next > GiftSlotDropTokens.Script.pullDown, previous < GiftSlotDropTokens.Script.pullUp {
            let value = ease((next - GiftSlotDropTokens.Script.pullDown - 120) / (GiftSlotDropTokens.Script.pullUp - GiftSlotDropTokens.Script.pullDown - 220))
            touchMoved(to: CGPoint(x: width / 2 + 12 - CGFloat(23 * value), y: GiftSlotDropTokens.Layout.cardY + 30 + GiftSlotDropTokens.Script.pullBy * CGFloat(value)))
        }
        if previous < GiftSlotDropTokens.Script.pullUp, next >= GiftSlotDropTokens.Script.pullUp { touchEnded(velocity: .zero) }
        if previous < GiftSlotDropTokens.Script.done, next >= GiftSlotDropTokens.Script.done {
            close(at: clock - (next - GiftSlotDropTokens.Script.done))
        }
        let loopMilliseconds = GiftSlotDropTokens.loop * 1_000
        scriptTime = next >= loopMilliseconds ? next.truncatingRemainder(dividingBy: loopMilliseconds) : next
    }

    private func scriptedSwipe(previous: Double, now: Double, swipe: (at: Double, ms: Double, dx: Double)) {
        let end = swipe.at + swipe.ms
        let startX = Double(width) / 2 - swipe.dx / 2
        let endX = Double(width) / 2 + swipe.dx / 2
        if previous < swipe.at, now >= swipe.at { touchBegan(at: CGPoint(x: CGFloat(startX), y: GiftSlotDropTokens.Layout.cardY), scripted: true) }
        if now > swipe.at, previous < end {
            let progress = ease((now - swipe.at) / swipe.ms)
            touchMoved(to: CGPoint(x: CGFloat(startX + (endX - startX) * progress), y: GiftSlotDropTokens.Layout.cardY + CGFloat(4 * sin(.pi * min(1, (now - swipe.at) / swipe.ms)))))
        }
        if previous < end, now >= end {
            let velocity = CGFloat((endX - startX) / swipe.ms * 1_000 * 0.6)
            touchEnded(velocity: CGVector(dx: velocity, dy: 0))
        }
    }

    private func snap(to index: Int, velocity: Double) {
        let target = max(0, min(GiftSlotDropTokens.gifts.count - 1, index))
        offsetSpring.spring(to: Double(target), velocity: velocity, parameters: GiftSlotDropTokens.Springs.snap)
        if target != selected { selected = target; emit(.select) }
    }

    private func release() {
        releasedFrom = ReleasedPose(x: pullX, y: pullY, scale: pullScale(pullY) * liftSpring.value)
        releasedAt = clock; firedConfetti = false; confetti.removeAll(keepingCapacity: true)
        setPhase(.inserting); emit(.insert)
    }

    private func setPhase(_ next: Phase) { guard phase != next else { return }; phase = next; phaseStartedAt = clock }
    private func setArmed(_ next: Bool) { guard armed != next else { return }; armed = next; if next { emit(.armed) } }
    private func emit(_ haptic: Haptic) { if !touch.scripted { haptics.append(haptic) } }

    private func resetAfterClose() {
        closeAt = -1; releasedAt = -1; pullXSpring.set(0); pullYSpring.set(0); confetti.removeAll(keepingCapacity: true); setPhase(.choosing)
    }

    private func spawnConfetti() {
        guard !reducedMotion else { return }
        confetti = (0..<GiftSlotDropTokens.Print.confetti).map { number in
            let angle = -.pi / 2 + (hash(number, 1) - 0.5) * GiftSlotDropTokens.Random.confettiSaltRange
            let speed = GiftSlotDropTokens.Random.speedMin + hash(number, 2) * GiftSlotDropTokens.Random.speedRange
            return ConfettiPiece(
                x: Double(width) / 2 + (hash(number, 3) - 0.5) * Double(GiftSlotDropTokens.Layout.slotW) * 0.8,
                y: Double(GiftSlotDropTokens.Layout.slotY) - 4,
                vx: cos(angle) * speed, vy: sin(angle) * speed, angle: hash(number, 4) * .pi,
                spin: (hash(number, 5) - 0.5) * GiftSlotDropTokens.Random.spinRange,
                life: GiftSlotDropTokens.Print.confettiLife * (0.7 + 0.3 * hash(number, 6)), color: Int(hash(number, 7) * 3)
            )
        }
    }

    private func hash(_ number: Int, _ salt: Int) -> Double {
        let value = sin(Double(number) * GiftSlotDropTokens.Random.multiplier + Double(salt) * GiftSlotDropTokens.Random.saltMultiplier) * GiftSlotDropTokens.Random.scale
        return value - floor(value)
    }
    private func pullScale(_ y: Double) -> Double { 1 - (1 - GiftSlotDropTokens.Drag.minScale) * clamp(y / Double(GiftSlotDropTokens.Drag.full)) }
    private func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    private func ease(_ value: Double) -> Double { let c = clamp(value); return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2 }
    private func cubicInOut(_ value: Double) -> Double { value < 0.5 ? 4 * value * value * value : 1 - pow(-2 * value + 2, 3) / 2 }
    private func backOut(_ value: Double) -> Double { let c = clamp(value), k = GiftSlotDropTokens.Print.feedEaseBack; return 1 + (k + 1) * pow(c - 1, 3) + k * pow(c - 1, 2) }
    private func spring(_ time: Double, stiffness: Double, damping: Double) -> Double {
        guard time > 0 else { return 0 }
        let frequency = sqrt(stiffness), ratio = min(0.999, damping / (2 * frequency)), damped = frequency * sqrt(1 - ratio * ratio)
        return 1 - exp(-ratio * frequency * time) * (cos(damped * time) + ratio * frequency / damped * sin(damped * time))
    }
}
