import Foundation
import UIKit

// Lava Lamp Loader — the loop's script and the touch simulation (a port of lavaLampMotion.ts).
//
// A pose is a flat array of six blobs × (x, y, r, stretch): five wax blobs (A, B, C, D, E) and the
// one a finger warms (F). x is pt from the lamp's axis in the liquid's own coordinates (the glass
// magnifies it on screen), y is pt from the top of the lamp, stretch > 1 makes a blob tall.
//
// loop = 9 s:
//   0.0 – 1.6  rest: A and B sit on the warm pool, C floats under the cap, D and E drift.
//   1.6 – 2.4  A swells and stretches, dips 3 pt first (anticipation), and necks off the pool.
//   2.4 – 4.6  A rises (ease-in-out) with a sideways sway, tall and thin while it climbs.
//   4.6 – 5.4  A meets C under the cap and they merge.
//   5.4 – 7.6  they split: C, cooled and stretched, sinks to A's old seat (a small squash on
//              landing, B nudged by it) while A shrinks into C's old place.
//   7.6 – 9.0  rest. A and C have traded places with each other's size, so the frame at 9 s is the
//              frame at 0 s.
// The tracks counter clicks up one track at a time from 1,204 to 1,238 during 1.6 – 7.4 and winds
// back during 7.6 – 8.3 while the wax rests.

enum LavaLampLoaderMotion {
    static let blobs = 6
    static let poseLength = blobs * 4
    static let capEnd = 0.13
    static let glassEnd = 0.7
    static let tracksFrom = 1204.0
    static let tracksTo = 1238.0
    static let tracksAll = 2310.0

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func easeInOut(_ x: Double) -> Double {
        let c = clamp01(x)
        return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
    }

    static func easeOut(_ x: Double) -> Double {
        let c = clamp01(x)
        return 1 - pow(1 - c, 3)
    }

    static func mix(_ a: Double, _ b: Double, _ e: Double) -> Double { a + (b - a) * e }

    /// Half-width of the glass at lamp-local y (the same profile the shader draws).
    static func glassHalfWidth(_ y: Double, _ H: Double, _ k: Double) -> Double {
        let s = clamp01((y - H * capEnd) / (H * glassEnd - H * capEnd))
        return (33 + 45 * pow(s, 1.15)) * k
    }

    /// Screen x (from the axis) to the liquid's x at height y: the inverse of the glass's lens.
    static func toLiquidX(_ x: Double, _ y: Double, _ H: Double, _ k: Double) -> Double {
        let hw = glassHalfWidth(y, H, k)
        let u = max(-1, min(1, x / hw))
        return hw * u * (0.74 + 0.26 * u * u)
    }

    private static func put(_ out: inout [Double], _ i: Int, _ x: Double, _ y: Double, _ r: Double, _ w: Double) {
        out[i * 4] = x
        out[i * 4 + 1] = y
        out[i * 4 + 2] = r
        out[i * 4 + 3] = w
    }

    /// The pose at loop time t.
    static func loopPose(_ t: Double, _ L: LavaLampLoaderLayout) -> [Double] {
        var out = [Double](repeating: 0, count: poseLength)
        // The wax is drawn 1.2 × the lamp's scale.
        let k = L.k * 1.2
        let capEnd = L.H * Self.capEnd
        let glassEnd = L.H * Self.glassEnd
        let ph = 2 * Double.pi * t / LavaLampLoader.loop
        // Seats: A low left, B low right, C high.
        let ax0 = -16 * k
        let ay0 = glassEnd - 34 * k
        let cx0 = 7 * k
        let cy0 = capEnd + 58 * k

        // A: swell, rise, merge, settle into C's seat.
        var ax = ax0
        var ay = ay0
        var ar = 25 * k
        var aw = 0.85
        let sw = seg(t, 1.6, 2.4)
        let dip = sin(Double.pi * seg(t, 1.6, 1.85)) * 3 * k
        ar = mix(25 * k, 27 * k, easeOut(sw))
        aw = mix(0.85, 1.15, easeInOut(sw))
        ay = ay0 + dip - 12 * k * easeInOut(seg(t, 1.8, 2.4))
        let rise = seg(t, 2.4, 4.6)
        if rise > 0 {
            let e = easeInOut(rise)
            ay = mix(ay0 - 12 * k, capEnd + 84 * k, e)
            ax = mix(ax0, -2 * k, e) + 7 * k * sin(Double.pi * e)
            aw = 1.12 + 0.12 * sin(Double.pi * e) - 0.07 * e
        }
        let merge = easeInOut(seg(t, 4.6, 5.4))
        if t >= 4.6 {
            ax = mix(-2 * k, 3 * k, merge)
            ay = mix(capEnd + 84 * k, capEnd + 74 * k, merge)
            aw = 1.05
        }
        let split = easeInOut(seg(t, 5.4, 7.6))
        if t >= 5.4 {
            ax = mix(3 * k, cx0, split)
            ay = mix(capEnd + 74 * k, cy0, split)
            ar = mix(27 * k, 23 * k, split)
            aw = mix(1.05, 1.0, split)
        }

        // C: waits under the cap, drifts down to meet A, then sinks to A's seat.
        var cx = cx0 + 1.5 * k * sin(ph)
        var cy = cy0 + 2 * k * sin(ph + 1)
        var cr = 23 * k
        var cw = 1.0
        if t >= 4.6 {
            cx = mix(cx0, 5 * k, merge)
            cy = mix(cy0, capEnd + 66 * k, merge)
        }
        if t >= 5.4 {
            let fall = seg(t, 5.4, 7.6)
            let e = easeInOut(fall)
            cx = mix(5 * k, ax0, e) - 6 * k * sin(Double.pi * e)
            cy = mix(capEnd + 66 * k, ay0, e)
            cr = mix(23 * k, 25 * k, e)
            // Tall while it sinks, a small squash when it lands, then its resting shape.
            let land = seg(t, 7.0, 7.6)
            cw = mix(1.0, 1.25, sin(Double.pi * min(1, fall * 1.15))) * (1 - land) + land * (0.85 - 0.08 * sin(Double.pi * land))
        }

        // B rests, and is nudged when C lands next to it.
        let nudge = sin(Double.pi * seg(t, 7.1, 7.9))
        let bx = 21 * k + 2.5 * k * nudge
        let by = glassEnd - 24 * k + 0.8 * k * sin(2 * ph)
        // D and E: small drifting drops.
        let dx = -9 * k + 3 * k * sin(ph + 0.6)
        let dy = L.H * 0.42 + 6 * k * sin(ph)
        let ex = 15 * k + 2 * k * sin(2 * ph + 2)
        let ey = capEnd + 128 * k + 5 * k * sin(2 * ph + 0.4)

        put(&out, 0, ax, ay, ar, aw)
        put(&out, 1, bx, by, 21 * k, 0.85)
        put(&out, 2, cx, cy, cr, cw)
        put(&out, 3, dx, dy, 11 * k, 1.05)
        put(&out, 4, ex, ey, 9 * k, 1.0)
        put(&out, 5, 0, glassEnd, 0, 1)
        return out
    }

    /// The tracks counter at loop time t: whole tracks, each one clicking in over 0.12 s.
    static func tracksAt(_ t: Double) -> Double {
        let n = tracksTo - tracksFrom
        if t < 7.6 {
            let x = seg(t, 1.6, 7.4) * n
            let whole = floor(x)
            let f = clamp01((x - whole) / 0.7)
            return tracksFrom + whole + easeOut(f)
        }
        return mix(tracksTo, tracksFrom, easeInOut(seg(t, 7.6, 8.3)))
    }
}

struct LavaLampLoaderLayout: Equatable {
    let cx: Double
    let top: Double
    let H: Double
    let k: Double
    let shelf: Double

    init(width: Double, height: Double, insetTop: Double, insetBottom: Double) {
        top = insetTop + 136
        let shelfY = height - insetBottom - 150
        H = max(240, shelfY - top)
        k = min(H / 480, (width / 2 - 20) / 116)
        cx = width / 2
        shelf = top + H
    }
}

/// A touch's state: each blob's position and velocity, its anchor, and the finger.
struct LavaLampLoaderSim {
    var pos: [Double]
    var vel: [Double]
    var anchor: [Double]
    /// Finger in liquid coordinates, its velocity, and whether it is down.
    var fx: Double
    var fy: Double
    var fvx = 0.0
    var fvy = 0.0
    var down = true
    /// Set once the warmed blob reaches the cap (one haptic): 0 not yet, 1 just now, 2 felt.
    var topped = 0

    init(pose: [Double], fx: Double, fy: Double) {
        pos = pose
        vel = [Double](repeating: 0, count: LavaLampLoaderMotion.blobs * 2)
        anchor = pose
        self.fx = fx
        self.fy = fy
        // The finger's blob starts where the finger lands, with no wax yet.
        pos[20] = fx
        pos[21] = fy
        pos[22] = 0
        pos[23] = 1
    }

    /// One step of the hand-driven wax: springs home, pushed away from the finger and along its drag.
    mutating func step(_ dt: Double, _ L: LavaLampLoaderLayout) {
        typealias M = LavaLampLoaderMotion
        let k = L.k
        let capEnd = L.H * M.capEnd
        let glassEnd = L.H * M.glassEnd
        let sigma = 60 * k
        for i in 0..<5 {
            let x = pos[i * 4]
            let y = pos[i * 4 + 1]
            var vx = vel[i * 2]
            var vy = vel[i * 2 + 1]
            var ax = 5 * (anchor[i * 4] - x) - 3.2 * vx
            var ay = 5 * (anchor[i * 4 + 1] - y) - 3.2 * vy
            if down {
                let dx = x - fx
                let dy = y - fy
                let d = (dx * dx + dy * dy).squareRoot() + 1
                let fall = exp(-(d * d) / (2 * sigma * sigma))
                ax += (dx / d) * 1500 * k * fall + (fvx - vx) * 5 * fall
                ay += (dy / d) * 1500 * k * fall + (fvy - vy) * 5 * fall
            }
            vx += ax * dt
            vy += ay * dt
            var nx = x + vx * dt
            var ny = y + vy * dt
            let r = pos[i * 4 + 2]
            ny = max(capEnd + 22 * k + r * 0.4, min(glassEnd - 8 * k, ny))
            let lim = max(0, M.glassHalfWidth(ny, L.H, k) * 0.74 - r * 0.45)
            nx = max(-lim, min(lim, nx))
            pos[i * 4] = nx
            pos[i * 4 + 1] = ny
            vel[i * 2] = vx
            vel[i * 2 + 1] = vy
            // A pushed blob stretches along its speed a little.
            pos[i * 4 + 3] = anchor[i * 4 + 3] * (1 + min(0.25, abs(vy) / (400 * k)))
        }
        // The warmed blob: grows under a held finger and follows it; let go, it rises to the cap.
        let R = 27 * k
        var bx = pos[20]
        var by = pos[21]
        var vx = vel[10]
        var vy = vel[11]
        if down {
            let nx = bx + (fx - bx) * min(1, dt * 10)
            let ny = by + (fy - by) * min(1, dt * 10)
            vx = (nx - bx) / max(dt, 1e-3)
            vy = (ny - by) / max(dt, 1e-3)
            bx = nx
            by = ny
            pos[22] += (R - pos[22]) * min(1, dt * 2.6)
        } else {
            vy += -130 * k * dt
            vy *= exp(-1.6 * dt)
            vx *= exp(-2.5 * dt)
            vx += -bx * 0.8 * dt
            bx += vx * dt
            by += vy * dt
            let ceil = capEnd + 18 * k + pos[22] * 0.7
            if by < ceil {
                by = ceil
                vy = 0
                if topped == 0 { topped = 1 }
            }
        }
        let lim = max(0, M.glassHalfWidth(by, L.H, k) * 0.74 - pos[22] * 0.4)
        pos[20] = max(-lim, min(lim, bx))
        pos[21] = max(capEnd + 14 * k, min(glassEnd + 10 * k, by))
        vel[10] = vx
        vel[11] = vy
        pos[23] = 1 + min(0.4, max(0, -vy) / (260 * k))
    }
}

/// What one frame draws.
struct LavaLampLoaderFrame {
    var pose: [Double]
    var tracks: Double
    var glow: Double
    var phase: Double
    var spin: Double
}

/// The loop clock, the touch simulation and the hand-off between them, stepped once a frame.
@MainActor
final class LavaLampLoaderEngine {
    private typealias M = LavaLampLoaderMotion
    private enum Mode { case auto, user, back }

    /// Seconds after the last touch before the lamp eases back, and how long that takes.
    private static let idle = 2.0
    private static let returnFor = 0.9

    private var mode = Mode.auto
    private var clock = 0.0
    private var now = 0.0
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var last: Date?
    private var pose = [Double](repeating: 0, count: LavaLampLoaderMotion.poseLength)
    private var from = [Double](repeating: 0, count: LavaLampLoaderMotion.poseLength)
    private var sim: LavaLampLoaderSim?
    private var tracks = LavaLampLoaderMotion.tracksFrom
    private var tracksFromValue = LavaLampLoaderMotion.tracksFrom
    private var warm = 0.0
    private var spin = 0.0
    private var layout: LavaLampLoaderLayout?
    /// Whether a finger is on the screen (the drag gesture has begun and not ended).
    var fingerDown = false
    private let haptic = UIImpactFeedbackGenerator(style: .light)

    func step(to date: Date, layout L: LavaLampLoaderLayout, auto: Bool, reduced: Bool) -> LavaLampLoaderFrame {
        layout = L
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 0.05)
        now += dt
        let at = now
        var out = pose
        switch mode {
        case .auto:
            let t = auto ? (clock + dt).truncatingRemainder(dividingBy: LavaLampLoader.loop) : 0
            clock = t
            out = M.loopPose(t, L)
            tracks = auto ? M.tracksAt(t) : M.tracksFrom
            warm *= exp(-dt * 3)
        case .user:
            if var s = sim {
                s.step(dt, L)
                if s.topped == 1 {
                    s.topped = 2
                    haptic.impactOccurred()
                }
                for i in 0..<M.poseLength { out[i] = s.pos[i] }
                warm += ((s.down ? 1 : 0) - warm) * min(1, dt * 3)
                sim = s
                if !s.down && at - lastTouch > Self.idle {
                    from = out
                    tracksFromValue = tracks
                    returnAt = at
                    mode = .back
                }
            }
        case .back:
            let e = M.easeInOut((at - returnAt) / Self.returnFor)
            let rest = M.loopPose(0, L)
            for i in 0..<M.poseLength { out[i] = from[i] + (rest[i] - from[i]) * e }
            tracks = tracksFromValue + (M.tracksFrom - tracksFromValue) * e
            warm *= exp(-dt * 3)
            if e >= 1 {
                mode = .auto
                clock = 0
            }
        }
        pose = out
        if !reduced { spin = (spin + dt * 2.4).truncatingRemainder(dividingBy: Double.pi * 2) }
        return LavaLampLoaderFrame(
            pose: out,
            tracks: tracks,
            glow: 1 + 0.18 * warm,
            phase: 2 * Double.pi * clock / LavaLampLoader.loop,
            spin: spin
        )
    }

    /// A screen point to the liquid's coordinates, or nil outside the glass.
    private func liquidPoint(_ p: CGPoint, _ L: LavaLampLoaderLayout) -> (Double, Double)? {
        let ly = p.y - L.top
        if ly < L.H * M.capEnd - 20 || ly > L.H * M.glassEnd + 30 { return nil }
        let y = max(L.H * M.capEnd, min(L.H * M.glassEnd, ly))
        return (M.toLiquidX(p.x - L.cx, y, L.H, L.k), ly)
    }

    func touchBegan(_ point: CGPoint) {
        guard let L = layout, let p = liquidPoint(point, L) else { return }
        lastTouch = now
        if mode == .user, var s = sim {
            s.down = true
            s.fx = p.0
            s.fy = p.1
            // The warmed blob picks up again under the new touch.
            s.pos[20] = p.0
            s.pos[21] = p.1
            sim = s
        } else {
            sim = LavaLampLoaderSim(pose: pose, fx: p.0, fy: p.1)
            mode = .user
        }
        haptic.impactOccurred()
    }

    func touchMoved(_ point: CGPoint, velocity: CGSize) {
        guard let L = layout, var s = sim, mode == .user, s.down else { return }
        let y = max(L.top + L.H * M.capEnd, min(L.top + L.H * M.glassEnd, point.y))
        guard let p = liquidPoint(CGPoint(x: point.x, y: y), L) else { return }
        s.fx = p.0
        s.fy = p.1
        s.fvx = velocity.width
        s.fvy = velocity.height
        sim = s
        lastTouch = now
    }

    func touchEnded() {
        guard var s = sim, mode == .user else { return }
        s.down = false
        s.fvx = 0
        s.fvy = 0
        s.topped = 0
        sim = s
        lastTouch = now
    }
}
