import Foundation

/// Deterministic 120 Hz port of sticker-sim.ts. Positions and velocities use screen points and points/second.
struct GlassDiscOnboardingSimulation {
    enum Phase { case hidden, out, leaving }

    private(set) var phase: Phase = .hidden
    private(set) var x: [Double]
    private(set) var y: [Double]
    private(set) var vx: [Double]
    private(set) var vy: [Double]
    let hx: [Double]
    let hy: [Double]
    let r: [Double]
    private(set) var scale: [Double]
    let cell: [Int]
    let order: [Int]
    private var bx = 0.0
    private var by = 0.0
    private var fx = 0.0
    private var fy = 0.0
    private var fvx = 0.0
    private var fvy = 0.0
    private var finger = false
    private var clock = 0.0
    private var remainder = 0.0

    init(width: Double, height: Double) {
        let cloud = GlassDiscOnboardingTheme.Atlas.cloud
        let n = cloud.count
        x = Array(repeating: 0, count: n)
        y = Array(repeating: 0, count: n)
        vx = Array(repeating: 0, count: n)
        vy = Array(repeating: 0, count: n)
        hx = cloud.map { $0.x / GlassDiscOnboardingTheme.Atlas.cloudFrameWidth * width }
        hy = cloud.map { $0.y / GlassDiscOnboardingTheme.Atlas.cloudFrameHeight * height }
        r = cloud.map(\.r)
        scale = Array(repeating: 0, count: n)
        cell = cloud.map(\.cell)
        var ranks = Array(repeating: 0, count: n)
        for i in 0..<n {
            for j in 0..<n where hy[j] > hy[i] || (hy[j] == hy[i] && j < i) {
                ranks[i] += 1
            }
        }
        order = ranks
    }

    mutating func launch(x originX: Double, y originY: Double, reducedMotion: Bool) {
        phase = .out
        clock = 0
        remainder = 0
        bx = originX
        by = originY
        for i in r.indices {
            x[i] = reducedMotion ? hx[i] : originX
            y[i] = reducedMotion ? hy[i] : originY
            vx[i] = 0
            vy[i] = 0
            scale[i] = 0
        }
    }

    mutating func leave() {
        guard phase == .out else { return }
        phase = .leaving
        clock = 0
        finger = false
    }

    mutating func hide() {
        phase = .hidden
        for i in scale.indices { scale[i] = 0 }
    }

    mutating func setFinger(x: Double, y: Double) {
        finger = true
        fx = x
        fy = y
        fvx = 0
        fvy = 0
    }

    mutating func moveFinger(x: Double, y: Double, deltaMS: Double) {
        let seconds = max(GlassDiscOnboardingMotion.Stickers.fingerVelocityMinimumMs, deltaMS) / 1000
        fvx = (x - fx) / seconds
        fvy = (y - fy) / seconds
        fx = x
        fy = y
    }

    mutating func endFinger() { finger = false }

    /// Returns true once every bubble has left on rewind.
    @discardableResult
    mutating func step(ms: Double, height: Double, reducedMotion: Bool) -> Bool {
        guard phase != .hidden else { return false }
        let s = GlassDiscOnboardingMotion.Stickers.self
        let burst = GlassDiscOnboardingMotion.Burst.self
        remainder += min(ms, s.maxFrameMs)
        let dt = s.step
        let dms = dt * 1000
        while remainder >= dms - s.integrationEpsilon {
            remainder -= dms
            clock += dms
            if phase == .leaving {
                var left = 0
                for i in r.indices {
                    if scale[i] == 0 { continue }
                    if reducedMotion {
                        scale[i] = max(0, scale[i] - dms / GlassDiscOnboardingMotion.Fade.reducedMs)
                        if scale[i] > 0 { left += 1 }
                        continue
                    }
                    let start = Double(r.count - 1 - order[i]) * s.exitStaggerMs
                    if clock > start {
                        let goal = hy[i] - height * s.exitLift
                        vy[i] += (s.exitStiffness * (goal - y[i]) - s.exitDamping * vy[i]) * dt
                        y[i] += vy[i] * dt
                    }
                    if y[i] + r[i] * scale[i] > s.exitVisibilityY { left += 1 }
                }
                if left == 0 {
                    hide()
                    return true
                }
                continue
            }
            fvx *= s.fingerVelocityDecay
            fvy *= s.fingerVelocityDecay
            for i in r.indices {
                if reducedMotion {
                    scale[i] = min(1, scale[i] + dms / GlassDiscOnboardingMotion.Fade.reducedMs)
                    continue
                }
                let start = burst.delayMs + Double(order[i]) * burst.staggerMs
                if clock < start { continue }
                if scale[i] == 0 {
                    let dx = hx[i] - bx
                    let dy = hy[i] - by
                    let distance = max(s.launchDistanceMinimum, hypot(dx, dy))
                    vx[i] = dx / distance * burst.launchSpeed
                    vy[i] = dy / distance * burst.launchSpeed
                    scale[i] = burst.startScale
                }
                let travelled = hypot(x[i] - bx, y[i] - by)
                let aged = (clock - start) / burst.growMs
                scale[i] = max(scale[i], min(1, burst.startScale + max(travelled / burst.growOver, aged)))
                let float = sin(clock / s.floatPeriodMs * .pi * 2 + Double(i) * s.floatPhaseStep) * s.float
                vx[i] += (s.stiffness * (hx[i] - x[i]) - s.damping * vx[i]) * dt
                vy[i] += (s.stiffness * (hy[i] + float - y[i]) - s.damping * vy[i]) * dt
                if finger {
                    let dx = x[i] - fx
                    let dy = y[i] - fy
                    let distance = max(s.distanceMinimum, hypot(dx, dy))
                    let reach = s.push + r[i]
                    if distance < reach {
                        let shove = (reach - distance) * s.fingerShove
                        x[i] += dx / distance * shove
                        y[i] += dy / distance * shove
                        vx[i] += dx / distance * shove / dt * s.fingerImpulse
                        vy[i] += dy / distance * shove / dt * s.fingerImpulse
                        let near = 1 - distance / reach
                        vx[i] += fvx * GlassDiscOnboardingMotion.Upgrade.fingerCarry * near * dt * s.fingerCarryStepScale
                        vy[i] += fvy * GlassDiscOnboardingMotion.Upgrade.fingerCarry * near * dt * s.fingerCarryStepScale
                    }
                }
                x[i] += vx[i] * dt
                y[i] += vy[i] * dt
            }
            if reducedMotion { continue }
            for i in r.indices where scale[i] != 0 {
                for j in (i + 1)..<r.count where scale[j] != 0 {
                    let dx = x[j] - x[i]
                    let dy = y[j] - y[i]
                    let distance = max(s.distanceMinimum, hypot(dx, dy))
                    let minDistance = (r[i] * scale[i] + r[j] * scale[j]) * s.overlap
                    if distance >= minDistance { continue }
                    let push = (minDistance - distance) / distance * s.separation * 0.5
                    x[i] -= dx * push
                    y[i] -= dy * push
                    x[j] += dx * push
                    y[j] += dy * push
                }
            }
        }
        return false
    }
}
