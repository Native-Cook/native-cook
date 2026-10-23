import Observation
import QuartzCore
import SwiftUI

@MainActor
@Observable
final class GiftSlotDropModel {
    enum Phase: Int, Equatable { case choosing, pulling, swiping, inserting, reading, offer, closing }
    enum Haptic { case select, armed, insert, success, light }
    enum DragMode { case undecided, pull, swipe }

    struct ReleasedPose: Equatable { var x = 0.0; var y = 0.0 }
    struct CardPose: Equatable {
        var x = 0.0
        var y = 0.0
        var scale = 1.0
        var turn = 0.0
        var lean = 0.0
        var opacity = 1.0
    }
    struct OfferPose: Equatable {
        var y = 0.0
        var scale = 1.0
        var angle = 0.0
        var opacity = 0.0
        var rising = false
    }
    struct Ghost: Equatable { var x = 0.0; var y = 0.0; var opacity = 0.0 }

    private struct Touch {
        var active = false
        var scripted = false
        var onCard = false
        var mode = DragMode.undecided
        var origin = CGPoint.zero
        var offsetOrigin = 0.0
    }

    private struct SpringValue {
        var value: Double
        var velocity = 0.0
        var target: Double?
        var parameters = GiftSlotDropTokens.Springs.snap

        mutating func set(_ newValue: Double) {
            value = newValue
            velocity = 0
            target = nil
        }

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
                let acceleration = -parameters.stiffness * (value - target) - parameters.damping * velocity
                velocity += acceleration * h
                value += velocity * h
            }
            if abs(value - target) < 0.0005, abs(velocity) < 0.005 { set(target) }
        }
    }

    let autoPlay: Bool
    private(set) var reducedMotion = false
    private(set) var phase = Phase.choosing
    private(set) var selected = GiftSlotDropTokens.startIndex
    private(set) var armed = false
    private(set) var clock = 0.0
    private(set) var releasedAt = -1.0
    private(set) var releasedFrom = ReleasedPose()
    private(set) var closeAt = -1.0
    private(set) var titleAt = -1_000_000.0
    private(set) var titleWas = 0
    private(set) var ghost = Ghost()

    private var layout = GiftSlotDropTokens.ScreenLayout(width: 393, height: 852, top: 59, bottom: 34)
    private var offsetSpring = SpringValue(value: Double(GiftSlotDropTokens.startIndex))
    private var pullXSpring = SpringValue(value: 0)
    private var pullYSpring = SpringValue(value: 0)
    private var liftSpring = SpringValue(value: 1)
    private var touch = Touch()
    private var lastTimestamp: CFTimeInterval?
    private var scriptOn: Bool
    private var scriptTime = 0.0
    private var quietTime = 0.0
    private var scriptTouching = false
    private var successFired = false
    private var haptics: [Haptic] = []

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
        scriptOn = autoPlay
    }

    var offset: Double { offsetSpring.value }
    var pullX: Double { pullXSpring.value }
    var pullY: Double { pullYSpring.value }
    var gift: GiftSlotDropTokens.Gift { GiftSlotDropTokens.gifts[selected] }
    var isChoosing: Bool { phase == .choosing || phase == .pulling || phase == .swiping }
    var showsOffer: Bool { phase == .offer || phase == .closing }
    var screenLayout: GiftSlotDropTokens.ScreenLayout { layout }
    var needsFrames: Bool {
        (autoPlay && !reducedMotion) || touch.active || phase != .choosing || offsetSpring.target != nil
            || pullXSpring.target != nil || pullYSpring.target != nil || liftSpring.target != nil
    }

    func configure(_ next: GiftSlotDropTokens.ScreenLayout, reducedMotion: Bool) {
        layout = next
        self.reducedMotion = reducedMotion
        if reducedMotion {
            scriptOn = false
            ghost.opacity = 0
        } else if autoPlay, phase == .choosing, !scriptTouching {
            scriptOn = true
        }
    }

    func advance(to timestamp: CFTimeInterval) {
        guard let lastTimestamp else {
            self.lastTimestamp = timestamp
            return
        }
        let elapsed = max(0, timestamp - lastTimestamp)
        self.lastTimestamp = timestamp
        guard elapsed > 0 else { return }
        let dt = min(0.064, elapsed)
        let milliseconds = dt * 1_000
        clock += milliseconds
        offsetSpring.advance(dt)
        pullXSpring.advance(dt)
        pullYSpring.advance(dt)
        liftSpring.advance(dt)
        updateTimeline()
        updateScript(milliseconds: milliseconds)
    }

    func touchBegan(at point: CGPoint, scripted: Bool = false) {
        guard phase == .choosing else { return }
        let onCard = abs(point.x - layout.cx) < GiftSlotDropTokens.Layout.cardW / 2 + 12
            && abs(point.y - layout.cardY) < GiftSlotDropTokens.Layout.cardH / 2 + 12
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
            var next = touch.offsetOrigin - Double(dx / layout.spacing)
            if next < 0 { next *= 0.3 }
            let last = Double(GiftSlotDropTokens.gifts.count - 1)
            if next > last { next = last + (next - last) * 0.3 }
            offsetSpring.set(next)
        } else if touch.mode == .pull {
            pullXSpring.set(Double(dx * GiftSlotDropTokens.Drag.sideResist))
            let resisted = dy < 0
                ? dy * GiftSlotDropTokens.Drag.upResist
                : min(dy, GiftSlotDropTokens.Drag.full + (dy - GiftSlotDropTokens.Drag.full) * 0.2)
            pullYSpring.set(Double(resisted))
            setArmed(dy >= GiftSlotDropTokens.Drag.commit)
        }
    }

    func touchEnded(velocity: CGVector, cancelled: Bool = false) {
        guard touch.active else { return }
        let mode = touch.mode
        touch.active = false
        liftSpring.spring(to: 1, parameters: GiftSlotDropTokens.Springs.lift)
        if mode == .swipe {
            let direction = velocity.dx == 0 ? 0.0 : (velocity.dx > 0 ? 1.0 : -1.0)
            let fling = abs(velocity.dx) > GiftSlotDropTokens.Drag.swipeVelocity ? -direction * 0.5 : 0
            snap(to: Int((offset + fling).rounded()), velocity: Double(-velocity.dx / layout.spacing))
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
        if scriptOn, touch.scripted, touch.active {
            setArmed(false)
            touchEnded(velocity: .zero, cancelled: true)
        }
        ghost.opacity = 0
        scriptOn = false
        quietTime = 0
        scriptTouching = true
    }

    func letGo() {
        scriptTouching = false
        quietTime = 0
    }

    func activate() {
        guard phase == .choosing else { return }
        touch.scripted = false
        release()
    }

    func step(_ direction: Int) {
        guard phase == .choosing else { return }
        snap(to: selected + direction, velocity: 0)
    }

    func userClose() {
        takeOver()
        close()
        letGo()
    }

    func close() {
        guard phase == .offer else { return }
        closeAt = clock
        setPhase(.closing)
        emit(.light)
    }

    func drainHaptics() -> [Haptic] {
        defer { haptics.removeAll(keepingCapacity: true) }
        return haptics
    }

    func cardPose(index: Int) -> CardPose {
        let rel = Double(index) - offset
        let distance = min(1, abs(rel))
        var pose = CardPose(
            x: Double(layout.cx) + rel * Double(layout.spacing),
            y: Double(layout.cardY),
            scale: 1 - (1 - GiftSlotDropTokens.Layout.sideScale) * distance,
            turn: Double(-max(-1, min(1, rel)) * GiftSlotDropTokens.Layout.sideTurn)
        )
        let selectedCard = index == selected

        if selectedCard, phase == .pulling {
            let progress = clamp(pullY / Double(GiftSlotDropTokens.Drag.full))
            pose.x += pullX
            pose.y += pullY
            pose.scale *= (1 - (1 - GiftSlotDropTokens.Drag.minScale) * progress) * liftSpring.value
            pose.turn = 0
            pose.lean = reducedMotion ? 0 : progress * Double(GiftSlotDropTokens.Drag.leanDeg)
        } else if selectedCard, phase == .choosing {
            pose.scale *= liftSpring.value
        }

        if phase == .inserting {
            let since = clock - releasedAt
            if selectedCard {
                let progress = reducedMotion ? 1 : easeInOut(clamp(since / GiftSlotDropTokens.Sequence.sink))
                let target = Double(layout.slotY - layout.cardY + GiftSlotDropTokens.Layout.cardH * 0.7)
                let startProgress = clamp(releasedFrom.y / Double(GiftSlotDropTokens.Drag.full))
                pose.x = Double(layout.cx) + releasedFrom.x * (1 - progress)
                pose.y = Double(layout.cardY) + releasedFrom.y + (target - releasedFrom.y) * progress
                pose.scale = 1 - (1 - GiftSlotDropTokens.Drag.minScale) * startProgress
                pose.lean = reducedMotion ? 0 : startProgress * Double(GiftSlotDropTokens.Drag.leanDeg)
                pose.turn = 0
                pose.opacity = reducedMotion ? 1 - clamp(since / GiftSlotDropTokens.reducedMs) : 1
            } else {
                pose.opacity = 1 - clamp(since / 300)
            }
        } else if phase == .reading || phase == .offer {
            pose.opacity = 0
        } else if phase == .closing {
            let delay = reducedMotion ? 0 : GiftSlotDropTokens.Close.out
            let duration = reducedMotion ? GiftSlotDropTokens.reducedMs : GiftSlotDropTokens.Close.back
            let progress = easeOut(clamp((clock - closeAt - delay) / duration))
            pose.opacity = progress
            pose.y += reducedMotion ? 0 : 12 * (1 - progress)
            pose.scale *= 0.94 + 0.06 * progress
        }
        return pose
    }

    func offerPose() -> OfferPose {
        guard releasedAt >= 0, phase == .offer || phase == .closing else { return OfferPose() }
        let since = clock - releasedAt
        let marks = milestones
        let riseTime = since - marks.read
        let riseProgress = reducedMotion ? 1 : clamp(riseTime / GiftSlotDropTokens.Sequence.rise)
        let rise = reducedMotion ? 1 : settle(riseTime, GiftSlotDropTokens.Sequence.rise)
        let startY = Double(layout.slotY + GiftSlotDropTokens.Layout.giftH * 0.55)
        var pose = OfferPose(
            y: startY + (Double(layout.giftY) - startY) * rise,
            scale: Double(GiftSlotDropTokens.Layout.giftW / GiftSlotDropTokens.Layout.cardW) * (0.9 + 0.1 * easeOut(riseProgress)),
            angle: .pi * easeInOut(clamp((since - marks.risen - GiftSlotDropTokens.Sequence.pause) / GiftSlotDropTokens.Sequence.flip)),
            opacity: reducedMotion ? clamp(riseTime / GiftSlotDropTokens.reducedMs) : 1,
            rising: riseProgress < 1
        )
        if phase == .closing {
            let duration = reducedMotion ? GiftSlotDropTokens.reducedMs : GiftSlotDropTokens.Close.out
            let progress = clamp((clock - closeAt) / duration)
            pose.opacity *= 1 - progress
            if !reducedMotion {
                pose.scale *= 1 - 0.04 * progress
                pose.y += 10 * progress
            }
        }
        return pose
    }

    var titleIndex: Int { title(for: phase, armed: armed) }

    func titleProgress() -> (old: Double, current: Double, oldY: CGFloat, currentY: CGFloat) {
        if reducedMotion { return (0, 1, 0, 0) }
        let elapsed = clock - titleAt
        let out = clamp(elapsed / GiftSlotDropTokens.Title.out)
        let incoming = easeOut(clamp((elapsed - GiftSlotDropTokens.Title.out) / GiftSlotDropTokens.Title.inMs))
        if titleWas == titleIndex || elapsed >= GiftSlotDropTokens.Title.out + GiftSlotDropTokens.Title.inMs {
            return (0, 1, 0, 0)
        }
        return (
            1 - out,
            out < 1 ? 0 : incoming,
            -GiftSlotDropTokens.Title.rise * CGFloat(out),
            GiftSlotDropTokens.Title.rise * CGFloat(1 - incoming)
        )
    }

    var carouselOpacity: Double {
        if phase == .inserting { return 1 - clamp((clock - releasedAt) / 300) }
        if phase == .reading || phase == .offer { return 0 }
        if phase == .closing {
            let delay = reducedMotion ? 0 : GiftSlotDropTokens.Close.out
            let duration = reducedMotion ? GiftSlotDropTokens.reducedMs : GiftSlotDropTokens.Close.back
            return easeOut(clamp((clock - closeAt - delay) / duration))
        }
        return 1
    }

    var offerOpacity: Double {
        if phase == .offer { return 1 }
        if phase == .closing {
            let duration = reducedMotion ? GiftSlotDropTokens.reducedMs : GiftSlotDropTokens.Close.out
            return 1 - clamp((clock - closeAt) / duration)
        }
        return 0
    }

    var shelfPose: (opacity: Double, y: CGFloat) {
        let since = releasedAt >= 0 ? clock - releasedAt : -1
        let fade: Double
        if phase == .offer {
            fade = clamp((since - milestones.risen) / 260)
        } else if phase == .closing {
            let delay = reducedMotion ? 0 : GiftSlotDropTokens.Close.out
            let duration = reducedMotion ? GiftSlotDropTokens.reducedMs : GiftSlotDropTokens.Close.back
            fade = 1 - clamp((clock - closeAt - delay) / duration)
        } else {
            fade = 0
        }
        return (1 - fade, reducedMotion ? 0 : 16 * CGFloat(fade))
    }

    var slotGlow: Double {
        let since = releasedAt >= 0 ? clock - releasedAt : -1
        if phase == .pulling {
            return 0.6 * clamp(pullY / Double(GiftSlotDropTokens.Drag.commit)) + (armed ? 0.25 : 0)
        }
        if phase == .inserting { return 0.85 }
        if phase == .reading {
            if reducedMotion { return 0.8 }
            return 0.8 + 0.2 * sin(((since - milestones.sunk) / GiftSlotDropTokens.Sequence.read) * .pi * 2 - .pi / 2)
        }
        if phase == .offer { return 1 - clamp((since - milestones.read) / 600) * 0.6 }
        return reducedMotion ? 0.2 : 0.2 + 0.06 * sin(clock / 2_400 * .pi * 2)
    }

    func detailProgress(_ index: Int) -> Double {
        guard phase == .offer || phase == .closing else { return 0 }
        let since = clock - releasedAt
        let start = milestones.turned + Double(index) * GiftSlotDropTokens.Sequence.stagger
        let incoming = reducedMotion
            ? clamp((since - start) / GiftSlotDropTokens.reducedMs)
            : easeOut(clamp((since - start) / GiftSlotDropTokens.Sequence.detailsIn))
        return incoming * offerOpacity
    }

    private var milestones: (sunk: Double, read: Double, risen: Double, turned: Double) {
        if reducedMotion {
            let read = GiftSlotDropTokens.reducedMs + GiftSlotDropTokens.Sequence.read
            return (GiftSlotDropTokens.reducedMs, read, read, read + GiftSlotDropTokens.reducedMs)
        }
        let sunk = GiftSlotDropTokens.Sequence.sink
        let read = sunk + GiftSlotDropTokens.Sequence.read
        let risen = read + GiftSlotDropTokens.Sequence.rise
        return (sunk, read, risen, risen + GiftSlotDropTokens.Sequence.pause + GiftSlotDropTokens.Sequence.flip)
    }

    private func updateTimeline() {
        if releasedAt >= 0 {
            let since = clock - releasedAt
            if phase == .inserting, since >= milestones.sunk { setPhase(.reading) }
            if phase == .reading, since >= milestones.read { setPhase(.offer) }
            if !successFired, since >= milestones.turned {
                successFired = true
                emit(.success)
            }
        }
        if closeAt >= 0 {
            let duration = reducedMotion
                ? GiftSlotDropTokens.reducedMs
                : GiftSlotDropTokens.Close.out + GiftSlotDropTokens.Close.back
            if clock - closeAt >= duration { resetAfterClose() }
        }
    }

    private func updateScript(milliseconds: Double) {
        guard autoPlay, !reducedMotion else { return }
        if !scriptOn {
            let resting = phase == .offer || (phase == .choosing && abs(offset - offset.rounded()) < 0.01)
            quietTime = !scriptTouching && resting ? quietTime + milliseconds : 0
            if quietTime >= GiftSlotDropTokens.resumeAfterMs, phase == .offer {
                close()
                quietTime = 0
                return
            }
            if quietTime >= GiftSlotDropTokens.resumeAfterMs, selected != GiftSlotDropTokens.startIndex {
                snap(to: GiftSlotDropTokens.startIndex, velocity: 0)
                quietTime = 0
                return
            }
            if quietTime >= GiftSlotDropTokens.resumeAfterMs, phase == .choosing {
                scriptOn = true
                scriptTime = 0
            }
            return
        }

        let previous = scriptTime
        let next = previous + milliseconds
        for swipe in GiftSlotDropTokens.Script.swipes { scriptedSwipe(previous: previous, now: next, swipe: swipe) }
        if previous < GiftSlotDropTokens.Script.pullDown, next >= GiftSlotDropTokens.Script.pullDown {
            touchBegan(at: CGPoint(x: layout.cx + 12, y: layout.cardY + 40), scripted: true)
            ghost = Ghost(x: Double(layout.cx + 12), y: Double(layout.cardY + 40), opacity: 1)
        }
        if next > GiftSlotDropTokens.Script.pullDown, previous < GiftSlotDropTokens.Script.pullUp {
            let progress = ease((next - GiftSlotDropTokens.Script.pullDown - 120) / (GiftSlotDropTokens.Script.pullUp - GiftSlotDropTokens.Script.pullDown - 220))
            let point = CGPoint(
                x: layout.cx + 12 - 14 * CGFloat(progress),
                y: layout.cardY + 40 + GiftSlotDropTokens.Script.pullBy * CGFloat(progress)
            )
            ghost = Ghost(x: Double(point.x), y: Double(point.y), opacity: 1)
            touchMoved(to: point)
        }
        if previous < GiftSlotDropTokens.Script.pullUp, next >= GiftSlotDropTokens.Script.pullUp {
            touchEnded(velocity: .zero)
            ghost.opacity = 0
        }
        if previous < GiftSlotDropTokens.Script.done, next >= GiftSlotDropTokens.Script.done { close() }
        let loopMilliseconds = GiftSlotDropTokens.loop * 1_000
        scriptTime = next >= loopMilliseconds ? next.truncatingRemainder(dividingBy: loopMilliseconds) : next
    }

    private func scriptedSwipe(previous: Double, now: Double, swipe: (at: Double, ms: Double, dx: Double)) {
        let end = swipe.at + swipe.ms
        let startX = Double(layout.cx) - swipe.dx / 2
        let endX = Double(layout.cx) + swipe.dx / 2
        if previous < swipe.at, now >= swipe.at {
            touchBegan(at: CGPoint(x: CGFloat(startX), y: layout.cardY), scripted: true)
            ghost = Ghost(x: startX, y: Double(layout.cardY), opacity: 1)
        }
        if now > swipe.at, previous < end {
            let progress = ease((now - swipe.at) / swipe.ms)
            let point = CGPoint(
                x: CGFloat(startX + (endX - startX) * progress),
                y: layout.cardY + CGFloat(4 * sin(.pi * min(1, (now - swipe.at) / swipe.ms)))
            )
            ghost = Ghost(x: Double(point.x), y: Double(point.y), opacity: 1)
            touchMoved(to: point)
        }
        if previous < end, now >= end {
            let velocity = CGFloat((endX - startX) / swipe.ms * 1_000 * 0.6)
            touchEnded(velocity: CGVector(dx: velocity, dy: 0))
            ghost.opacity = 0
        }
    }

    private func snap(to index: Int, velocity: Double) {
        let target = max(0, min(GiftSlotDropTokens.gifts.count - 1, index))
        offsetSpring.spring(to: Double(target), velocity: velocity, parameters: GiftSlotDropTokens.Springs.snap)
        if target != selected {
            selected = target
            emit(.select)
        }
    }

    private func release() {
        releasedFrom = ReleasedPose(x: pullX, y: pullY)
        releasedAt = clock
        successFired = false
        setPhase(.inserting)
        emit(.insert)
    }

    private func setPhase(_ next: Phase) {
        guard phase != next else { return }
        let before = title(for: phase, armed: armed)
        let after = title(for: next, armed: armed)
        if before != after {
            titleWas = before
            titleAt = clock
        }
        phase = next
    }

    private func setArmed(_ next: Bool) {
        guard armed != next else { return }
        let before = title(for: phase, armed: armed)
        let after = title(for: phase, armed: next)
        if before != after {
            titleWas = before
            titleAt = clock
        }
        armed = next
        if next { emit(.armed) }
    }

    private func title(for phase: Phase, armed: Bool) -> Int {
        if phase == .inserting || phase == .reading { return 2 }
        if phase == .offer { return 3 }
        return armed ? 1 : 0
    }

    private func emit(_ haptic: Haptic) {
        if !touch.scripted { haptics.append(haptic) }
    }

    private func resetAfterClose() {
        closeAt = -1
        releasedAt = -1
        pullXSpring.set(0)
        pullYSpring.set(0)
        setPhase(.choosing)
    }

    private func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    private func ease(_ value: Double) -> Double {
        let c = clamp(value)
        return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
    }
    private func easeOut(_ value: Double) -> Double { cubicBezier(0.23, 1, 0.32, 1, value) }
    private func easeInOut(_ value: Double) -> Double { cubicBezier(0.77, 0, 0.175, 1, value) }

    private func cubicBezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ value: Double) -> Double {
        let target = clamp(value)
        let cx = 3 * x1
        let bx = 3 * (x2 - x1) - cx
        let ax = 1 - cx - bx
        let cy = 3 * y1
        let by = 3 * (y2 - y1) - cy
        let ay = 1 - cy - by
        var t = target
        for _ in 0..<8 {
            let x = ((ax * t + bx) * t + cx) * t - target
            let derivative = (3 * ax * t + 2 * bx) * t + cx
            if abs(x) < 0.00001 || abs(derivative) < 0.000001 { break }
            t -= x / derivative
        }
        t = clamp(t)
        return ((ay * t + by) * t + cy) * t
    }

    private func settle(_ milliseconds: Double, _ duration: Double) -> Double {
        guard milliseconds > 0 else { return 0 }
        let t = milliseconds / duration
        if t >= 1.6 { return 1 }
        return 1 - exp(-5.2 * t) * cos(4.4 * t)
    }
}
