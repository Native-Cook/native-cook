import Foundation

/*
 Iron Filings Magnet — layout, the magnets' script and the field. A port of the React Native
 ironMotion.ts.

 Each bar magnet is two poles (N +1 at the right end in its own frame, S −1 at the left), and the
 field at a point is the sum over poles of q·r / (|r|² + ε²)^1.5. A filing lines up with the field
 (it is a needle: θ and θ + π are the same), more firmly where the field is strong, and is pulled a
 few points towards the nearest pole, so filings crowd at the ends.

 autoPlay timeline (loop = 9 s):
   0.0 – 1.4   rest: magnet A in the middle under the paper, filings along its dipole lines.
   1.4 – 5.4   A traces a figure eight (x = sin 2πs, y = sin 4πs, s ease-in-out sine) and turns
               ±17° with it; each filing follows 30 – 520 ms late, later the farther it is.
   5.4 – 6.4   magnet B slides in from the right edge, north end first (ease-out cubic): two north
               poles face each other; A is pushed 40 pt left with a small overshoot (5.55 – 6.6).
   6.4 – 7.2   hold: the repulsion pattern, field lines bending away from the gap.
   7.2 – 8.2   B slides back out (ease-in-out cubic); A returns to the middle (7.3 – 8.2).
   8.2 – 9.0   rest.
 */
enum IronFilingsMagnetMotion {
    static let loop = 9.0
    static let filings = 900

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double {
        let u = 1 - x
        return 1 - u * u * u
    }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func easeInOutSine(_ x: Double) -> Double { 0.5 - 0.5 * cos(Double.pi * x) }
    static func easeOutBack(_ x: Double) -> Double {
        let c1 = 1.2
        let c3 = c1 + 1
        let u = x - 1
        return 1 + c3 * u * u * u + c1 * u * u
    }

    /// Magnet poses at loop time t: [ax, ay, aAngle, bx, by, bAngle]. B's angle π puts its N on the left.
    static func magnetsAt(_ g: IronFilingsMagnetGeo, _ t: Double) -> [Double] {
        let s = easeInOutSine(seg(t, 1.4, 5.4))
        let ax0 = g.cx + g.paperW * 0.24 * sin(2 * Double.pi * s)
        let ay = g.cy + (g.fieldBottom - g.fieldTop) * 0.13 * sin(4 * Double.pi * s)
        let aAngle = 0.3 * sin(2 * Double.pi * s)
        let push = 40 * g.u * (easeOutBack(seg(t, 5.55, 6.6)) - easeInOutCubic(seg(t, 7.3, 8.2)))
        let inX = g.cx + 104 * g.u
        let bx = g.parkX + (inX - g.parkX) * (easeOutCubic(seg(t, 5.4, 6.4)) - easeInOutCubic(seg(t, 7.2, 8.2)))
        return [ax0 - push, ay, aAngle, bx, g.cy, Double.pi]
    }

    /// Field at (x, y) from both magnets.
    static func fieldAt(_ x: Double, _ y: Double, _ m: [Double], _ half: Double, _ soft: Double) -> (Double, Double) {
        var bx = 0.0
        var by = 0.0
        for k in 0..<2 {
            let mx = m[k * 3]
            let my = m[k * 3 + 1]
            let ca = cos(m[k * 3 + 2]) * half
            let sa = sin(m[k * 3 + 2]) * half
            for q in [-1.0, 1.0] {
                let dx = x - (mx + q * ca)
                let dy = y - (my + q * sa)
                let r2 = dx * dx + dy * dy + soft
                let inv = q / (r2 * sqrt(r2))
                bx += dx * inv
                by += dy * inv
            }
        }
        return (bx, by)
    }

    /// The nearest pole to (x, y): dx, dy pointing from the point to the pole, and the distance.
    static func nearestPole(_ x: Double, _ y: Double, _ m: [Double], _ half: Double) -> (Double, Double, Double) {
        var best = 1e9
        var bx = 0.0
        var by = 0.0
        for k in 0..<2 {
            let ca = cos(m[k * 3 + 2]) * half * 0.9
            let sa = sin(m[k * 3 + 2]) * half * 0.9
            for q in [-1.0, 1.0] {
                let dx = m[k * 3] + q * ca - x
                let dy = m[k * 3 + 1] + q * sa - y
                let d = hypot(dx, dy)
                if d < best {
                    best = d
                    bx = dx
                    by = dy
                }
            }
        }
        return (bx, by, best)
    }

    /// Wraps an angle difference into (−π/2, π/2]: a filing has no front or back.
    static func axial(_ d: Double) -> Double {
        var a = d.truncatingRemainder(dividingBy: Double.pi)
        if a > Double.pi / 2 { a -= Double.pi }
        if a <= -Double.pi / 2 { a += Double.pi }
        return a
    }

    private static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// Filings scattered on a jittered grid over the field: x, y, resting angle, length (pt).
    struct Filings {
        var x: [Double] = []
        var y: [Double] = []
        var a0: [Double] = []
        var len: [Double] = []
    }

    static func scatter(_ g: IronFilingsMagnetGeo) -> Filings {
        let left = g.paperX + 14
        let right = g.paperX + g.paperW - 14
        let w = right - left
        let h = g.fieldBottom - g.fieldTop
        let cols = max(1, Int((sqrt(Double(filings) * w / h)).rounded()))
        let rows = Int(ceil(Double(filings) / Double(cols)))
        var out = Filings()
        for i in 0..<filings {
            let c = Double(i % cols)
            let r = Double(i / cols)
            let fi = Double(i)
            out.x.append(left + (c + 0.15 + 0.7 * hash(fi)) * w / Double(cols))
            out.y.append(g.fieldTop + (r + 0.15 + 0.7 * hash(fi + 1000)) * h / Double(rows))
            out.a0.append(hash(fi + 2000) * Double.pi)
            out.len.append((7 + 5 * hash(fi + 3000)) * g.u)
        }
        return out
    }
}

/// Screen layout, in points.
struct IronFilingsMagnetGeo: Equatable {
    let w: Double
    let h: Double
    let u: Double
    let paperX: Double
    let paperY: Double
    let paperW: Double
    let paperH: Double
    /// The scattered field: top and bottom of the filings' area.
    let fieldTop: Double
    let fieldBottom: Double
    let cx: Double
    let cy: Double
    /// Half length and half thickness of a bar magnet.
    let half: Double
    let thick: Double
    /// Magnet B's resting x (mostly past the right edge).
    let parkX: Double
    let footerY: Double

    init(w: Double, h: Double, top: Double, bottom: Double) {
        let u = min(w / 393, (h - top - bottom) / 759)
        let paperY = top + 4
        let paperH = h - bottom - 8 - paperY
        let fieldTop = paperY + 118 * u
        let fieldBottom = paperY + paperH - 96 * u
        self.w = w
        self.h = h
        self.u = u
        self.paperX = 12
        self.paperY = paperY
        self.paperW = w - 24
        self.paperH = paperH
        self.fieldTop = fieldTop
        self.fieldBottom = fieldBottom
        self.cx = w / 2
        self.cy = (fieldTop + fieldBottom) / 2
        self.half = 44 * u
        self.thick = 13 * u
        self.parkX = w + 30 * u
        self.footerY = paperY + paperH - 64 * u
    }
}
