import Foundation

/// One frame clock owns all animation, including interrupted touches and restoration.
final class GlassBladeDrawMotion {
    enum Mode { case opening, loop, idle, draw, step, spring, returning, fade, pulse }
    private(set) var frame = GlassBladeDrawFrame(charge: 0, intro: 0)
    private(set) var mode = Mode.opening
    private(set) var owner = 0
    private(set) var drawing = false
    private(set) var opacity = 0.0
    private(set) var seconds = 0.0
    private(set) var elapsed = 0.0
    var onSelection: (() -> Void)?
    var onImpact: ((Bool) -> Void)?
    var autoPlay = false
    var reduced = false
    private var age = 0.0
    private var idleElapsed = 0.0
    private var base = 0.0
    private var origin = 0.0
    private var target = 0.0
    private var distance = 4.0
    private var duration = 0.7
    private var launch = GlassBladeDrawFrame()
    private var restore: GlassBladeDrawFrame?
    private var restoreElapsed = 0.0
    private var pressedFrom = 0.0
    private var pressElapsed = 0.0
    private var dragged = false
    private var dragOrigin = 0.0
    private var dragVelocity = 0.0
    private var springVelocity = 0.0
    private var tactile = false
    private var landed = false

    func configure(autoPlay: Bool, reduced: Bool) {
        let changed = self.reduced != reduced
        self.autoPlay = autoPlay
        self.reduced = reduced
        frame.still = reduced ? 1 : 0
        if reduced && (changed || mode == .opening) {
            freeze()
            frame = GlassBladeDrawFrame(pos: GlassBladeDrawModel.rounded(frame.pos), still: 1)
        } else if !autoPlay && mode == .loop {
            freeze()
            stepTo(GlassBladeDrawModel.rounded(frame.pos))
        }
    }

    func advance(_ delta: Double) {
        let dt = max(0, min(0.1, delta))
        let previousHero = GlassBladeDrawModel.rounded(frame.pos)
        age += dt
        opacity = GlassBladeDrawTimeline.entrance(age / 0.3)
        if !reduced { seconds += dt }
        elapsed += dt
        if let initial = restore {
            restoreElapsed += dt
            let t = restoreElapsed
            frame.charge = 1 + GlassBladeDrawTimeline.releasedPress(t, from: initial.charge - 1)
            frame.press = GlassBladeDrawTimeline.releasedPress(t, from: initial.press)
            frame.flare = initial.flare * (1 - GlassBladeDrawTimeline.entrance(t / 0.18))
            let fade = GlassBladeDrawTimeline.entrance(t / 0.3)
            frame.ctaEnergy = initial.ctaEnergy + (1 - initial.ctaEnergy) * fade
            frame.intro = initial.intro + (1 - initial.intro) * fade
            if t >= 0.8 { restore = nil }
        }
        switch mode {
        case .opening:
            frame.intro = GlassBladeDrawTimeline.entrance(elapsed / 0.9)
            frame.charge = GlassBladeDrawTimeline.entrance((elapsed - 0.4) / 0.5)
            if elapsed >= 0.9 {
                frame.intro = 1
                frame.charge = 1
                elapsed = 0
                mode = autoPlay && !reduced ? .loop : .idle
            }
        case .loop:
            frame = GlassBladeDrawTimeline.loop(elapsed, base: base)
        case .draw:
            frame = GlassBladeDrawTimeline.draw(elapsed, distance, initial: launch)
            frame.pos += origin
            frame.intro = launch.intro + (1 - launch.intro) * GlassBladeDrawTimeline.entrance(elapsed / 0.3)
            if !landed && elapsed >= GlassBladeDrawTimeline.flareStart(distance) {
                landed = true
                onImpact?(true)
            }
            if elapsed >= GlassBladeDrawTimeline.duration(distance) { finish() }
        case .step, .returning:
            frame.pos = origin + (target - origin) * GlassBladeDrawTimeline.move(elapsed / duration)
            if elapsed >= duration {
                frame.pos = target
                if mode == .returning {
                    base = target
                    frame = GlassBladeDrawFrame(pos: target)
                    elapsed = 0
                    mode = autoPlay && !reduced ? .loop : .idle
                } else { finish() }
            }
        case .spring:
            let decay = 10.5
            let w = sqrt(150 - decay * decay)
            let displacement = origin - target
            let b = (springVelocity + decay * displacement) / w
            let wave = displacement * cos(w * elapsed) + b * sin(w * elapsed)
            frame.pos = target + exp(-decay * elapsed) * wave
            let velocity = exp(-decay * elapsed) *
                ((-displacement * w * sin(w * elapsed) + b * w * cos(w * elapsed)) - decay * wave)
            if abs(frame.pos - target) < 0.001 && abs(velocity) < 0.01 {
                frame.pos = target
                finish()
            }
        case .fade:
            frame.intro = 1
            frame.flare = 0
            frame.press = GlassBladeDrawTimeline.releasedPress(elapsed, from: launch.press)
            frame.ctaEnergy = launch.ctaEnergy + (1 - launch.ctaEnergy) * GlassBladeDrawTimeline.entrance(elapsed / 0.3)
            if elapsed < 0.16 {
                frame.charge = launch.charge * (1 - GlassBladeDrawTimeline.entrance(elapsed / 0.16))
            } else {
                frame.pos = target
                frame.charge = GlassBladeDrawTimeline.entrance((elapsed - 0.16) / 0.24)
            }
            if elapsed >= 0.4 { finish() }
        case .pulse:
            if elapsed < 0.12 {
                frame.charge = launch.charge + (1.15 - launch.charge) * GlassBladeDrawTimeline.entrance(elapsed / 0.12)
            } else {
                frame.charge = 1.15 - 0.15 * GlassBladeDrawTimeline.entrance((elapsed - 0.12) / 0.4)
            }
            if elapsed >= 0.52 { finish() }
        case .idle:
            let atRest = abs(frame.charge - 1) < 0.001 && abs(frame.flare) < 0.001
                && abs(frame.press) < 0.001 && abs(frame.ctaEnergy - 1) < 0.001 && frame.intro > 0.999
            if owner == 0 && autoPlay && !reduced {
                idleElapsed = atRest ? idleElapsed + dt : 0
                if idleElapsed >= 3.5 {
                    origin = frame.pos
                    target = GlassBladeDrawModel.rounded(frame.pos / 9) * 9
                    duration = 0.9
                    elapsed = 0
                    tactile = false
                    mode = .returning
                }
            }
        }
        if owner == 2 {
            pressElapsed += dt
            frame.press = pressedFrom + (1 - pressedFrom) * GlassBladeDrawTimeline.entrance(pressElapsed / 0.11)
        }
        frame.still = reduced ? 1 : 0
        if tactile && previousHero != GlassBladeDrawModel.rounded(frame.pos) { onSelection?() }
    }

    private func finish() {
        mode = .idle
        drawing = false
        tactile = false
        landed = false
        idleElapsed = 0
    }

    private func freeze() {
        restore = nil
        mode = .idle
        elapsed = 0
        idleElapsed = 0
        drawing = false
        tactile = false
        landed = false
    }

    private func restoreLight() {
        restore = frame
        restoreElapsed = 0
    }

    private func fadeTo(_ next: Double, drawing: Bool = false) {
        target = next
        launch = frame
        restore = nil
        elapsed = 0
        mode = .fade
        self.drawing = drawing
    }

    private func stepTo(_ next: Double) {
        if reduced { fadeTo(next); return }
        restoreLight()
        origin = frame.pos
        target = next
        duration = 0.7 * sqrt(max(1, abs(next - origin)))
        elapsed = 0
        mode = .step
    }

    func beginFan() {
        guard owner == 0 else { return }
        freeze()
        owner = 1
        dragOrigin = frame.pos
        dragVelocity = 0
        dragged = false
    }

    func moveFan(x: Double, y: Double, velocity: Double, scale: Double) {
        guard owner == 1 else { return }
        dragVelocity = velocity
        if !dragged && abs(x) <= 3 && abs(y) <= 3 { return }
        if !dragged {
            dragged = true
            tactile = true
            restoreLight()
        }
        let old = GlassBladeDrawModel.rounded(frame.pos)
        frame.pos = dragOrigin + x / (78 * scale)
        if old != GlassBladeDrawModel.rounded(frame.pos) { onSelection?() }
    }

    func endFan(x: Double, width: Double, scale: Double, cancelled: Bool) {
        guard owner == 1 else { return }
        owner = 0
        idleElapsed = 0
        if cancelled || dragged {
            let velocity = cancelled ? 0 : dragVelocity / (78 * scale)
            let travel = max(-4, min(4, velocity * 0.16))
            let next = GlassBladeDrawModel.rounded(frame.pos + travel)
            if reduced { fadeTo(next); return }
            restoreLight()
            origin = frame.pos
            target = next
            springVelocity = velocity
            mode = .spring
            elapsed = 0
        } else {
            let slots = max(-3, min(3, GlassBladeDrawModel.rounded((x - width / 2) / (78 * scale))))
            if slots != 0 || reduced { stepTo(GlassBladeDrawModel.rounded(frame.pos) - slots) }
            else {
                restoreLight()
                launch = frame
                elapsed = 0
                mode = .pulse
            }
        }
    }

    func beginBar() {
        guard owner == 0 else { return }
        freeze()
        owner = 2
        pressedFrom = frame.press
        pressElapsed = 0
        onImpact?(false)
    }

    func endBar(cancelled: Bool, blades: Int? = nil) {
        guard owner == 2 else { return }
        owner = 0
        if cancelled { stepTo(GlassBladeDrawModel.rounded(frame.pos)) }
        else { startDraw(blades: blades) }
    }

    private func startDraw(blades: Int? = nil) {
        let next = GlassBladeDrawModel.rounded(frame.pos) + Double(blades ?? Int.random(in: 3...5))
        tactile = true
        if reduced { fadeTo(next, drawing: true); return }
        origin = frame.pos
        distance = next - origin
        launch = frame
        elapsed = 0
        drawing = true
        landed = false
        mode = .draw
    }

    func accessibilityStep(_ delta: Int) {
        guard owner == 0 else { return }
        freeze()
        stepTo(GlassBladeDrawModel.rounded(frame.pos) + Double(delta))
    }

    func accessibilityDraw() {
        guard owner == 0 else { return }
        freeze()
        onImpact?(false)
        startDraw()
    }
}
