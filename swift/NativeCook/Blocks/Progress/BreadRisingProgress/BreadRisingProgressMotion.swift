import SwiftUI

/// Clock and shapes of the proofing loaf. Every value is a pure function of the timeline t
/// (seconds in [0, loop)); the finger scrubs the same timeline. Coordinates are local to the hero:
/// (0, 0) is the middle of the bowl's rim line, y down, pt.
enum BreadRisingProgressMotion {
    static let loop = 9.0

    // Beats (s).
    static let proofStart = 1.3
    static let proofEnd = 4.0
    static let shapeEnd = 4.8
    static let bakeEnd = 7.0
    static let readyEnd = 7.8
    static let resetEnd = 8.6
    /// Where a released scrub settles: raw, risen, shaped, baked.
    static let stops = [proofStart, proofEnd, shapeEnd, bakeEnd]

    // The glass bowl: rim line at y = rimY, a half-ellipse below it.
    static let rimY = 0.0
    static let bowlRX = 128.0
    static let bowlRY = 146.0
    static let wall = 6.0
    /// Raw dough sits this far below the rim; risen dough domes this far above it.
    static let rawLevel = 66.0
    static let rawDome = 22.0
    static let risenDome = 72.0
    // The boule on the stone.
    static let baseY = 140.0
    static let bouleRX = 118.0
    static let bouleTop = 112.0
    static let bouleFoot = 18.0
    static let bouleCY = baseY - bouleFoot
    /// Points on each half of the dough's outline (the bottom and the dome).
    static let half = 22

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    /// Ease-out that passes the target by about 6 % and settles: the dough "pops" into a boule.
    static func easeOutBack(_ x: Double) -> Double {
        let c = 1.3
        let m = x - 1
        return 1 + (c + 1) * m * m * m + c * m * m
    }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    struct Bread {
        /// Proof rise, shape morph (overshoots), bake, ready badge (overshoots), each 0…1.
        var rise = 0.0
        var shape = 0.0
        var bake = 0.0
        var ready = 0.0
        /// Bowl offset (pt down) and opacity; stone and oven opacity.
        var bowlY = 0.0
        var bowlA = 1.0
        var stone = 0.0
        var oven = 0.0
        var steam = 0.0
        /// Seconds on the stage timer.
        var timer = 5400.0
        /// Stage for the segmented control (0 proof … 2 bake, then 3 = proof again) and the status (0…4).
        var stage = 0.0
        var status = 0.0
        /// Overall progress 0…1.
        var progress = 0.0
        var t = 0.0
    }

    /// The whole loop, or a scrubbed position on it.
    static func breadAt(_ t: Double) -> Bread {
        let back = easeInOut(seg(t, readyEnd, resetEnd))
        var b = Bread()
        b.rise = easeInOut(seg(t, proofStart, proofEnd)) * (1 - back)
        b.shape = (t < shapeEnd ? easeOutBack(seg(t, proofEnd, shapeEnd)) : 1) * (1 - back)
        b.bake = easeInOut(seg(t, shapeEnd + 0.1, bakeEnd)) * (1 - back)
        let leave = easeInOut(seg(t, proofEnd, proofEnd + 0.5))
        let come = easeOut(seg(t, readyEnd + 0.3, resetEnd + 0.2))
        let bowlGone = leave * (1 - come)
        b.oven = easeInOut(seg(t, 4.6, 5.3)) * (1 - easeInOut(seg(t, readyEnd, readyEnd + 0.6)))
        b.steam = easeInOut(seg(t, 5.4, 6.3)) * (1 - easeInOut(seg(t, 7.3, 8.0)))
        b.ready = easeOutBack(seg(t, bakeEnd, bakeEnd + 0.38)) * (1 - easeInOut(seg(t, readyEnd, readyEnd + 0.25)))
        // Timer: proof 1:30:00 counts down, the bake timer 0:45:00 rolls in and counts down, then the
        // proof timer rolls back in.
        var timer = 5400.0
        if t >= proofStart && t < proofEnd {
            timer = 5400 * (1 - easeInOut(seg(t, proofStart, proofEnd)))
        } else if t >= proofEnd && t < shapeEnd {
            timer = 2700 * easeInOut(seg(t, proofEnd + 0.1, shapeEnd))
        } else if t >= shapeEnd && t < readyEnd {
            timer = 2700 * (1 - easeInOut(seg(t, shapeEnd + 0.1, bakeEnd)))
        } else if t >= readyEnd {
            timer = 5400 * easeInOut(seg(t, readyEnd, resetEnd))
        }
        b.timer = timer
        b.stage = easeInOut(seg(t, proofEnd, proofEnd + 0.3)) + easeInOut(seg(t, shapeEnd, shapeEnd + 0.3))
            + easeInOut(seg(t, readyEnd, readyEnd + 0.4))
        b.status = b.stage + easeInOut(seg(t, bakeEnd, bakeEnd + 0.3))
        b.progress = (0.5 * easeInOut(seg(t, proofStart, proofEnd)) + 0.1 * seg(t, proofEnd, shapeEnd)
            + 0.4 * easeInOut(seg(t, shapeEnd, bakeEnd))) * (1 - back)
        b.bowlY = 90 * bowlGone
        b.bowlA = 1 - bowlGone
        b.stone = min(1, leave) * (1 - easeInOut(seg(t, readyEnd + 0.2, resetEnd)))
        b.t = t
        return b
    }

    /// The bowl's inner wall: half-width at height y.
    static func innerHalf(_ y: Double) -> Double {
        let ry = bowlRY - wall
        let k = (y - rimY) / ry
        return (bowlRX - wall) * max(0, 1 - k * k).squareRoot()
    }

    /// The dough's outline as 2·half points: the bottom from left to right, then the dome from right
    /// to left. In the bowl it follows the glass; as a boule it is a flat-footed dome. `shape` blends
    /// the two point for point.
    static func doughPoints(rise: Double, shape: Double) -> [CGPoint] {
        let level = rimY + rawLevel * (1 - rise)
        let dome = rawDome + (risenDome - rawDome) * rise * rise
        let xl = max(innerHalf(level), 20) * 0.98
        let ry = bowlRY - wall
        var out: [CGPoint] = []
        out.reserveCapacity(half * 2)
        for k in 0..<half {
            let u = Double(k) / Double(half - 1)
            // In the bowl: along the glass.
            let bx = -xl + 2 * xl * u
            let bk = bx / (bowlRX - wall)
            let by = rimY + ry * max(0, 1 - bk * bk).squareRoot()
            // As a boule: a flat foot with rounded ends.
            let a = Double.pi + Double.pi * u
            let s = abs(sin(a))
            let px = bouleRX * cos(a)
            let py = bouleCY + bouleFoot * pow(s, 0.3)
            out.append(CGPoint(x: bx + (px - bx) * shape, y: by + (py - by) * shape))
        }
        for k in 0..<half {
            let a = Double.pi * (Double(k) + 0.5) / Double(half)
            let s = sin(a)
            let bx = xl * cos(a)
            let by = level - dome * pow(s, 0.75)
            let px = bouleRX * cos(a)
            let py = bouleCY - bouleTop * pow(s, 0.85)
            out.append(CGPoint(x: bx + (px - bx) * shape, y: by + (py - by) * shape))
        }
        return out
    }

    /// Top of the dough (smallest y), for gradients and the steam.
    static func doughTop(rise: Double, shape: Double) -> Double {
        let level = rimY + rawLevel * (1 - rise)
        let dome = rawDome + (risenDome - rawDome) * rise * rise
        let bowl = level - dome
        let boule = bouleCY - bouleTop
        return bowl + (boule - bowl) * shape
    }

    static let bubbles = 12

    /// Bubble i on the rising dome: where it sits, its radius, and its pop ring (radius and alpha)
    /// as it bursts. Bubbles live only during the proof.
    static func bubbleAt(_ i: Int, _ t: Double, _ rise: Double) -> (x: Double, y: Double, r: Double, ring: Double, ringA: Double) {
        let fi = Double(i)
        let born = proofStart + 0.3 + 2.1 * hash(fi * 3.3)
        let life = 0.55 + 0.35 * hash(fi * 7.7)
        let g = (t - born) / life
        let a = Double.pi * (0.18 + 0.64 * hash(fi * 5.1))
        let level = rimY + rawLevel * (1 - rise)
        let dome = rawDome + (risenDome - rawDome) * rise * rise
        let xl = max(innerHalf(level), 20) * 0.98
        let x = xl * cos(a) * 0.96
        let y = level - dome * pow(sin(a), 0.75) + 2.5
        let rmax = 3.2 + 4.2 * hash(fi * 9.1)
        if g <= 0 || g >= 1 || t > proofEnd { return (x, y, 0, 0, 0) }
        let r = g < 0.82 ? rmax * easeOut(g / 0.82) : 0
        let p = (g - 0.82) / 0.18
        return (x, y, r, g >= 0.82 ? rmax * (1 + 1.4 * p) : 0, g >= 0.82 ? 1 - p : 0)
    }

    /// One odometer column: unit in seconds, base 10 or 6. Rests on whole digits at whole seconds.
    static func column(_ v: Double, _ unit: Double, _ base: Double) -> Double {
        let whole = floor(v / unit)
        let within = v - whole * unit
        let roll = unit == 1 ? within : clamp01(within - (unit - 1))
        let pos = whole + roll
        return pos - floor(pos / base) * base
    }
}

/// The hero's paths, built once.
enum BreadRisingProgressArt {
    typealias M = BreadRisingProgressMotion

    /// The bowl's rim seen from slightly above: a flat ellipse.
    static let rimRY = 15.0

    /// An arc of the rim ellipse from `from` to `to` degrees (clockwise on screen, 0 = right).
    private static func rimArc(_ p: inout Path, from: Double, to: Double, move: Bool) {
        let n = 64
        for k in 0...n {
            let a = (from + (to - from) * Double(k) / Double(n)) * .pi / 180
            let q = CGPoint(x: M.bowlRX * cos(a), y: M.rimY + rimRY * sin(a))
            if k == 0 && move { p.move(to: q) } else { p.addLine(to: q) }
        }
    }

    /// A closed path through the dough's points, smoothed with quadratic segments through midpoints.
    static func dough(_ pts: [CGPoint]) -> Path {
        let n = pts.count
        func mid(_ i: Int) -> CGPoint {
            let a = pts[i % n]
            let b = pts[(i + 1) % n]
            return CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
        }
        var p = Path()
        p.move(to: mid(0))
        for i in 1...n { p.addQuadCurve(to: mid(i), control: pts[i % n]) }
        p.closeSubpath()
        return p
    }

    static let back: Path = {
        var p = Path()
        rimArc(&p, from: 180, to: 360, move: true)
        return p
    }()

    /// The glass in front of the dough: the bowl's body from the near half of the rim down to the foot.
    static let front: Path = {
        let rx = M.bowlRX
        let ry = M.bowlRY
        let y0 = M.rimY
        var p = Path()
        p.move(to: CGPoint(x: -rx, y: y0))
        p.addCurve(to: CGPoint(x: 0, y: y0 + ry), control1: CGPoint(x: -rx, y: y0 + ry * 0.75), control2: CGPoint(x: -rx * 0.55, y: y0 + ry))
        p.addCurve(to: CGPoint(x: rx, y: y0), control1: CGPoint(x: rx * 0.55, y: y0 + ry), control2: CGPoint(x: rx, y: y0 + ry * 0.75))
        rimArc(&p, from: 0, to: 180, move: true)
        return p
    }()

    static let rim: Path = {
        var p = Path()
        rimArc(&p, from: 0, to: 180, move: true)
        return p
    }()

    /// Two long reflections down the glass, curved with the wall.
    static let streaks: Path = {
        let rx = M.bowlRX
        let ry = M.bowlRY
        let y0 = M.rimY
        var p = Path()
        p.move(to: CGPoint(x: -rx + 14, y: y0 + 18))
        p.addCurve(to: CGPoint(x: -rx * 0.42, y: y0 + ry - 14), control1: CGPoint(x: -rx + 12, y: y0 + 70), control2: CGPoint(x: -rx + 34, y: y0 + 112))
        p.move(to: CGPoint(x: rx - 22, y: y0 + 24))
        p.addCurve(to: CGPoint(x: rx - 44, y: y0 + 96), control1: CGPoint(x: rx - 22, y: y0 + 52), control2: CGPoint(x: rx - 30, y: y0 + 74))
        return p
    }()

    /// The glass foot ring.
    static let foot = Path(ellipseIn: CGRect(x: -48, y: M.rimY + M.bowlRY - 9, width: 96, height: 16))

    /// Seeded speckles (flour on raw dough, blisters on the crust) over the dough's area.
    static func speckles(seed: Double, count: Int, rMin: Double, rMax: Double) -> Path {
        var p = Path()
        for i in 0..<count {
            let fi = Double(i)
            let x = (M.hash(seed + fi * 1.37) - 0.5) * 240
            let y = -80 + M.hash(seed + fi * 2.71) * (M.bouleCY + 10)
            let r = rMin + (rMax - rMin) * M.hash(seed + fi * 4.13)
            p.addEllipse(in: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
        }
        return p
    }

    static let flour = speckles(seed: 3, count: 70, rMin: 0.6, rMax: 1.4)
    static let blisters = speckles(seed: 41, count: 90, rMin: 0.8, rMax: 2.2)

    /// The score across the boule's top as it blooms: a lens that opens by `open` (0…1).
    static func score(top: Double, open: Double) -> Path {
        let y0 = top + 38
        let w = 1 + 13 * open
        var p = Path()
        p.move(to: CGPoint(x: -62, y: y0 + 18))
        p.addCurve(to: CGPoint(x: 64, y: y0 - 2), control1: CGPoint(x: -30, y: y0 - 6 - w * 0.6), control2: CGPoint(x: 30, y: y0 - 14 - w * 0.6))
        p.addCurve(to: CGPoint(x: -62, y: y0 + 18), control1: CGPoint(x: 30, y: y0 - 14 + w), control2: CGPoint(x: -30, y: y0 - 6 + w))
        p.closeSubpath()
        return p
    }

    /// The ear: the score's lifted upper lip, a lit edge.
    static func ear(top: Double, open: Double) -> Path {
        let y0 = top + 38
        let w = 1 + 13 * open
        var p = Path()
        p.move(to: CGPoint(x: -60, y: y0 + 17))
        p.addCurve(to: CGPoint(x: 62, y: y0 - 2), control1: CGPoint(x: -30, y: y0 - 6 - w * 0.6), control2: CGPoint(x: 30, y: y0 - 14 - w * 0.6))
        return p
    }

    /// Three steam wisps rising from the crust; waves move with whole multiples of the loop.
    static func steam(t: Double, top: Double) -> Path {
        var p = Path()
        for w in 0..<3 {
            let fw = Double(w)
            let x0 = (fw - 1) * 44 + 6
            let ph = 2 * .pi * (3 + fw) * t / M.loop + fw * 1.7
            for j in 0...12 {
                let fj = Double(j)
                let y = top - 6 - fj * 11
                let x = x0 + (4 + fj * 0.9) * sin(fj * 0.55 - ph) + fj * (fw - 1) * 1.2
                if j == 0 { p.move(to: CGPoint(x: x, y: y)) } else { p.addLine(to: CGPoint(x: x, y: y)) }
            }
        }
        return p
    }

    /// A check mark for the ready badge, centred on (0, 0).
    static let check: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -6.5, y: 0.5))
        p.addLine(to: CGPoint(x: -2, y: 5))
        p.addLine(to: CGPoint(x: 7, y: -5))
        return p
    }()

    /// A linen cloth on the counter under the bowl, hanging over the edge with a soft wavy hem.
    static let cloth: Path = {
        let top = M.bowlRY - 12
        var p = Path()
        p.move(to: CGPoint(x: -158, y: top))
        p.addLine(to: CGPoint(x: 158, y: top))
        p.addCurve(to: CGPoint(x: 182, y: top + 64), control1: CGPoint(x: 166, y: top + 20), control2: CGPoint(x: 176, y: top + 40))
        let hem = top + 64
        for k in 0..<6 {
            let x0 = 182 - Double(k) * 60.7
            p.addQuadCurve(to: CGPoint(x: x0 - 60.7, y: hem), control: CGPoint(x: x0 - 30, y: hem + (k % 2 == 0 ? 7 : -3)))
        }
        p.addCurve(to: CGPoint(x: -158, y: top), control1: CGPoint(x: -176, y: top + 40), control2: CGPoint(x: -166, y: top + 20))
        p.closeSubpath()
        return p
    }()

    /// Soft folds running down the cloth's hanging part.
    static let folds: Path = {
        var p = Path()
        for x in [-120.0, -52, 30, 104] {
            p.move(to: CGPoint(x: x, y: M.bowlRY + 14))
            p.addQuadCurve(to: CGPoint(x: x + 2, y: M.bowlRY + 56), control: CGPoint(x: x + 6, y: M.bowlRY + 34))
        }
        return p
    }()

    private static func noise(_ x: Double, _ y: Double) -> Double {
        let s = sin(x * 12.9898 + y * 78.233) * 43758.5453
        return s - floor(s)
    }

    /// Grain for the backdrop, 100 pt square at 1.4 px per pt, mixed in with soft light.
    static let grain: Image = {
        let n = 140
        var bytes = [UInt8](repeating: 0, count: n * n)
        for j in 0..<n {
            for i in 0..<n {
                let v = noise(Double(i), Double(j)) * 0.6
                    + noise(floor(Double(i) / 3.5) + 19, floor(Double(j) / 3.5) + 19) * 0.4
                bytes[j * n + i] = UInt8(max(0, min(255, v * 255)))
            }
        }
        let provider = CGDataProvider(data: Data(bytes) as CFData)!
        let cg = CGImage(
            width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: n,
            space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent
        )!
        return Image(decorative: cg, scale: 1.4)
    }()
}
