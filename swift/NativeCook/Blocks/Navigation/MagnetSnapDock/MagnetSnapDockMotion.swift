import Foundation

/*
 The dock's magnetism, the scripted finger and the selection, as functions of time.

 Magnet layout (`layout`): each icon is pulled toward the finger along the vector to it, with a
 Gaussian falloff (130 pt); the icon nearest the finger in x grows to 1.35×, its neighbours less;
 then four relaxation passes along x push overlapping icons apart, so they never touch. Strength
 `s` (0–1) is how present the finger is.

 autoPlay (loop = 8 s):
   0.0 – 1.4   rest: Pen on the plate.
   1.4 – 2.2   a finger lands above the canvas and glides down toward the dock (ease-out).
   2.2 – 3.8   sweeps left → right along the dock (ease-in-out, dipping in the middle).
   3.8 – 4.6   comes back to Brush and lowers onto it.
   4.6 – 5.0   presses Brush; releases at 5.0: Brush clicks onto the plate (spring ω 20, ζ 0.45),
               the plate slides over (ω 14, ζ 0.62), the panel swaps (5.1 – 5.7).
   6.25 – 6.75 a short second touch lands on Pen and releases: Pen clicks back, the panel swaps back.
   7.4 – 8.0   rest.
 In autoPlay the icons follow the finger through a short weighted history of the layout (now and
 50 / 100 / 150 ms ago, heavier for icons further from the finger), which reads like springs with
 follow-through yet stays a pure function of t. Under the finger they ride real springs.
 */
enum MagnetSnapDockMotion {
    static let loop = 8.0
    static let tools = 6
    static let icon = 48.0
    static let dockH = 84.0
    static let restTool = 1
    static let pullRadius = 130.0
    static let grow = 0.35

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(max((t - a) / (b - a), 0), 1)
    }

    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func easeOut(_ x: Double) -> Double {
        1 - pow(1 - x, 3)
    }

    static func springStep(_ tau: Double, _ w: Double, _ z: Double) -> Double {
        if tau <= 0 { return 0 }
        let wd = w * (1 - z * z).squareRoot()
        return 1 - exp(-z * w * tau) * (cos(wd * tau) + z * w / wd * sin(wd * tau))
    }

    private static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double {
        a + (b - a) * k
    }

    /// The scripted finger: (x, y, strength 0–1, ring alpha 0–1).
    static func loopFinger(_ g: MagnetSnapDockGeo, _ t: Double) -> (Double, Double, Double, Double) {
        let hover = g.dockY - 62
        var x = g.w * 0.62
        var y = g.boardTop + 120
        var s = 0.0
        var ring = 0.0
        if t >= 1.4 && t < 5.4 {
            let a = easeOut(seg(t, 1.4, 2.2))
            x = mix(g.w * 0.62, g.xs[0] - 4, a)
            y = mix(g.boardTop + 120, hover, a)
            if t >= 2.2 {
                let b = easeInOut(seg(t, 2.2, 3.8))
                x = mix(g.xs[0] - 4, g.xs[5] + 4, b)
                y = hover + 12 * sin(Double.pi * b)
            }
            if t >= 3.8 {
                let c = easeInOut(seg(t, 3.8, 4.6))
                x = mix(g.xs[5] + 4, g.xs[2], c)
                y = mix(hover, g.dockY - 26, c)
            }
            if t >= 4.6 {
                let d = easeInOut(seg(t, 4.6, 4.9))
                x = g.xs[2]
                y = mix(g.dockY - 26, g.cy, d)
            }
            let on = easeOut(seg(t, 1.4, 1.65))
            let off = easeOut(seg(t, 5.0, 5.35))
            s = on * (1 - off)
            ring = s
        } else if t >= 6.2 && t < 7.2 {
            let a = easeInOut(seg(t, 6.25, 6.6))
            x = mix(g.xs[1] + 18, g.xs[1], a)
            y = mix(g.dockY - 30, g.cy, a)
            s = 0.75 * easeOut(seg(t, 6.25, 6.4)) * (1 - easeOut(seg(t, 6.75, 7.1)))
            ring = s / 0.75
        }
        return (x, y, s, ring)
    }

    /// The selection at loop time t.
    static func loopSelection(_ t: Double) -> MagnetSnapDockSelection {
        if t >= 6.75 { return MagnetSnapDockSelection(from: 2, to: 1, at: 6.75) }
        if t >= 5.0 { return MagnetSnapDockSelection(from: 1, to: 2, at: 5.0) }
        return MagnetSnapDockSelection(from: restTool, to: restTool, at: -10)
    }

    /// Where each icon wants to be for a finger at (fx, fy) with strength s.
    static func layout(_ g: MagnetSnapDockGeo, _ fx: Double, _ fy: Double, _ s: Double) -> MagnetSnapDockTiles {
        var out = MagnetSnapDockTiles()
        let above = max(0, g.cy - fy - 24)
        let nearY = exp(-(above / 150) * (above / 150))
        for i in 0..<tools {
            let dx = fx - g.xs[i]
            let dy = fy - g.cy
            let d = hypot(dx, dy)
            let pull = s * exp(-(d / pullRadius) * (d / pullRadius))
            out.pull[i] = pull
            out.x[i] = g.xs[i] + dx * 0.16 * pull
            out.y[i] = g.cy + max(-30, dy * 0.2 * pull)
            out.scale[i] = 1 + grow * s * nearY * exp(-(dx / 62) * (dx / 62))
        }
        // Neighbours are pushed apart so the grown icons never overlap.
        for _ in 0..<4 {
            for i in 0..<(tools - 1) {
                let need = icon * (out.scale[i] + out.scale[i + 1]) / 2 + 6
                let gap = out.x[i + 1] - out.x[i] - need
                if gap < 0 {
                    out.x[i] += gap / 2
                    out.x[i + 1] -= gap / 2
                }
            }
        }
        return out
    }

    /// The loop's icon layout, with a short weighted history for follow-through.
    static func loopLayout(_ g: MagnetSnapDockGeo, _ t: Double) -> (MagnetSnapDockTiles, (Double, Double, Double, Double)) {
        let now = loopFinger(g, t)
        var nearest = 0
        for i in 1..<tools where abs(g.xs[i] - now.0) < abs(g.xs[nearest] - now.0) { nearest = i }
        // Further from the finger, the more the icon lags (weights over now, −50, −100, −150 ms).
        let weights: [[Double]] = (0..<tools).map { i in
            let lag = min(1, Double(abs(i - nearest)) / 3)
            return [0.55 - 0.25 * lag, 0.25, 0.13 + 0.12 * lag, 0.07 + 0.13 * lag]
        }
        var out = MagnetSnapDockTiles(zero: true)
        for k in 0..<4 {
            let fp = k == 0 ? now : loopFinger(g, max(0, t - Double(k) * 0.05))
            let l = layout(g, fp.0, fp.1, fp.2)
            if k == 0 { out.pull = l.pull }
            for i in 0..<tools {
                out.x[i] += l.x[i] * weights[i][k]
                out.y[i] += l.y[i] * weights[i][k]
                out.scale[i] += l.scale[i] * weights[i][k]
            }
        }
        return (out, now)
    }

    /// The click of a tool landing on the plate: (y offset in pt, scale) at τ after the snap.
    static func clickAt(_ tau: Double) -> (Double, Double) {
        if tau < 0 || tau > 1.2 { return (0, 1) }
        let k = springStep(tau, 20, 0.45)
        return (-6 * (1 - k), 1 + 0.08 * (1 - k))
    }

    /// The plate's x for a selection at time t.
    static func plateX(_ g: MagnetSnapDockGeo, _ sel: MagnetSnapDockSelection, _ t: Double) -> Double {
        mix(g.xs[sel.from], g.xs[sel.to], springStep(t - sel.at, 14, 0.62))
    }

    /// The panel swap's progress (0 → 1) for a selection at time t.
    static func swapAt(_ sel: MagnetSnapDockSelection, _ t: Double) -> Double {
        if sel.from == sel.to { return 1 }
        return easeInOut(seg(t - sel.at, 0.1, 0.7))
    }
}

/// From one tool to another, snapped at `at` on the clock the scene uses.
struct MagnetSnapDockSelection {
    var from: Int
    var to: Int
    var at: Double
}

/// Six tiles: centre, scale and how strongly each is pulled (for the field lines).
struct MagnetSnapDockTiles {
    var x = [Double](repeating: 0, count: 6)
    var y = [Double](repeating: 0, count: 6)
    var scale = [Double](repeating: 1, count: 6)
    var pull = [Double](repeating: 0, count: 6)

    init(zero: Bool = false) {
        if zero { scale = [Double](repeating: 0, count: 6) }
    }
}

struct MagnetSnapDockGeo: Equatable {
    let w: Double
    let h: Double
    let dockX: Double
    let dockY: Double
    let dockW: Double
    /// Rest centres of the icons, and the dock's centre line.
    let xs: [Double]
    let cy: Double
    let panelTop: Double
    let panelH: Double
    let boardTop: Double

    init(w: Double, h: Double, top: Double, bottom: Double) {
        let M = MagnetSnapDockMotion.self
        let dockW = min(w - 24, 372)
        let dockX = (w - dockW) / 2
        let dockY = h - max(bottom, 12) - 12 - M.dockH
        let pad = 10.0
        let pitch = (dockW - 2 * pad) / Double(M.tools)
        self.w = w
        self.h = h
        self.dockX = dockX
        self.dockY = dockY
        self.dockW = dockW
        xs = (0..<M.tools).map { dockX + pad + pitch * (Double($0) + 0.5) }
        cy = dockY + M.dockH / 2
        panelH = 196
        panelTop = dockY - 16 - 196
        boardTop = top + 100
    }
}
