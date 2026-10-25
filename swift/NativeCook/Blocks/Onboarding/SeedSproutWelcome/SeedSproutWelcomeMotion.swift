import SwiftUI

/// Clock and plant geometry. Every value is a pure function of the loop time t (or of the growth G
/// the finger's watering has given the plant), so the frame at t = loop equals the frame at 0.
/// Coordinates are local to the pot: (0, 0) is the middle of the soil, y down, pt.
enum SeedSproutWelcomeMotion {
    static let loop = 10.0

    /// Stem height at full growth, and the share of growth G that is the sprout (the rest is bloom).
    static let stem = 205.0
    static let sprout = 0.55
    /// The can's resting place and where it pours from (its body's centre), and its pouring tilt.
    static let park = CGPoint(x: 120, y: -266)
    static let pourAt = CGPoint(x: 112, y: -196)
    static let tilt = -0.55
    /// The spout's rose, in the can's own coordinates.
    static let tip = CGPoint(x: -97, y: -24)

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeOutBack(_ x: Double) -> Double {
        let c = 1.4
        let m = x - 1
        return 1 + (c + 1) * m * m * m + c * m * m
    }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    struct Scene {
        /// Growth 0 (seed) … 1 (full bloom).
        var g = 0.0
        /// Can position, tilt, and how hard it pours 0…1.
        var cx = park.x
        var cy = park.y
        var tilt = 0.0
        var pour = 0.0
        /// Wet soil 0…1, petal fall 0…1 (each petal staggers inside it).
        var wet = 0.0
        var fall = 0.0
        /// Page 0 … 2 for the copy and the dots.
        var page = 0.0
        var t = 0.0
    }

    /// Page from growth: seed, sprout, bloom.
    static func pageOf(_ g: Double) -> Double {
        easeInOut(seg(g, 0.14, 0.32)) + easeInOut(seg(g, 0.64, 0.82))
    }

    /// The whole loop.
    static func sceneAt(_ t: Double) -> Scene {
        let go = easeInOut(seg(t, 1.4, 2.0))
        let tipK = easeInOut(seg(t, 1.8, 2.1)) * (1 - easeInOut(seg(t, 2.8, 3.05)))
        let home = easeInOut(seg(t, 3.0, 3.55))
        let k = go * (1 - home)
        let g = sprout * easeInOut(seg(t, 2.6, 4.6)) + (1 - sprout) * easeInOut(seg(t, 5.0, 6.8)) - easeInOut(seg(t, 8.2, 9.6))
        var s = Scene()
        s.g = clamp01(g)
        s.cx = park.x + (pourAt.x - park.x) * k
        s.cy = park.y + (pourAt.y - park.y) * k
        s.tilt = tilt * tipK
        s.pour = clamp01((tipK - 0.6) / 0.4)
        s.wet = easeOut(seg(t, 2.05, 2.7)) * (1 - easeInOut(seg(t, 4.2, 6.4)))
        s.fall = seg(t, 8.0, 9.4)
        s.page = pageOf(clamp01(g))
        s.t = t
        return s
    }

    /// The spout's rose in pot coordinates for a can at (cx, cy) tilted by `tilt`.
    static func spout(_ cx: Double, _ cy: Double, _ tilt: Double) -> CGPoint {
        let c = cos(tilt)
        let s = sin(tilt)
        return CGPoint(x: cx + tip.x * c - tip.y * s, y: cy + tip.x * s + tip.y * c)
    }

    static let drops = 64
    static let fallTime = 0.68
    static let gravity = 900.0

    /// Drop i of the shower at time `wt`: released from the rose `life` seconds ago with a seeded
    /// spread, it falls until it reaches the soil. Nil when not in the air.
    static func dropAt(_ i: Int, _ wt: Double, _ x0: Double, _ y0: Double, _ tilt: Double) -> (x: Double, y: Double, r: Double, life: Double)? {
        let fi = Double(i)
        let raw = (wt + hash(fi * 1.7) * fallTime).truncatingRemainder(dividingBy: fallTime)
        let life = (raw + fallTime).truncatingRemainder(dividingBy: fallTime)
        // Out along the spout (pointing left and a little up in the can's frame), with a seeded spread.
        let c = cos(tilt)
        let s = sin(tilt)
        let dir = atan2(-0.96 * s - 0.27 * c, -0.96 * c + 0.27 * s) + (hash(fi * 5.9) - 0.5) * 0.8
        let speed = 60 + 60 * hash(fi * 3.1)
        let x = x0 + cos(dir) * speed * life
        let y = y0 + sin(dir) * speed * life + 0.5 * gravity * life * life
        if y > -2 + 6 * hash(fi * 7.3) { return nil }
        return (x, y, 1.5 + 1.6 * hash(fi * 2.2), life)
    }

    /// The stem, as a cubic from the soil up to `stem`; `sway` bends the top sideways (pt).
    static func stemPoint(_ u: Double, _ sway: Double) -> (x: Double, y: Double, angle: Double) {
        let p1x = -20.0, p1y = -stem * 0.36
        let p2x = 24 + sway * 0.4, p2y = -stem * 0.7
        let p3x = sway, p3y = -stem
        // The cubic starts at the origin (the soil), so its first point drops out.
        let b = 3 * (1 - u) * (1 - u) * u
        let c = 3 * (1 - u) * u * u
        let d = u * u * u
        let x = b * p1x + c * p2x + d * p3x
        let y = b * p1y + c * p2y + d * p3y
        let tx = 3 * (1 - u) * (1 - u) * p1x + 6 * (1 - u) * u * (p2x - p1x) + 3 * u * u * (p3x - p2x)
        let ty = 3 * (1 - u) * (1 - u) * p1y + 6 * (1 - u) * u * (p2y - p1y) + 3 * u * u * (p3y - p2y)
        return (x, y, atan2(ty, tx))
    }

    /// How far up the stem has grown and how open the bloom is (each 0…1), from growth g.
    static func plantOf(_ g: Double) -> (stem: Double, bloom: Double) {
        (clamp01(g / sprout), clamp01((g - sprout - 0.04) / (1 - sprout - 0.04)))
    }

    static let leaves: [(u: Double, side: Double, len: Double)] = [
        (0.24, -1, 50), (0.38, 1, 56), (0.55, -1, 48), (0.7, 1, 40),
    ]

    /// Leaf k: 0 folded … 1 open, with a springy overshoot as it unfurls.
    static func leafOpen(_ stemUp: Double, _ k: Int) -> (len: Double, wide: Double) {
        let g = clamp01((stemUp - leaves[k].u - 0.04) / 0.26)
        return (g <= 0 ? 0 : easeOutBack(g), easeOut(clamp01((g - 0.2) / 0.8)))
    }

    static let petals = 7
}

/// Static art for the pot, the can and the plant's parts, in pot coordinates ((0, 0) = middle of
/// the soil) or in each part's own frame.
enum SeedSproutWelcomeArt {
    typealias M = SeedSproutWelcomeMotion
    private static func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x, y: y) }

    /// Terracotta pot: tapered body below a rolled rim.
    static let potBody: Path = {
        var b = Path()
        b.move(to: pt(-70, 12))
        b.addLine(to: pt(70, 12))
        b.addCurve(to: pt(54, 104), control1: pt(66, 50), control2: pt(60, 86))
        b.addQuadCurve(to: pt(44, 112), control: pt(52, 112))
        b.addLine(to: pt(-44, 112))
        b.addQuadCurve(to: pt(-54, 104), control: pt(-52, 112))
        b.addCurve(to: pt(-70, 12), control1: pt(-60, 86), control2: pt(-66, 50))
        b.closeSubpath()
        return b
    }()

    static let potRim = Path(roundedRect: CGRect(x: -80, y: -8, width: 160, height: 26), cornerRadius: 9)
    static let soil = CGRect(x: -68, y: -9, width: 136, height: 18)

    /// Crumbs of soil on its surface.
    static let soilCrumbs: Path = {
        var b = Path()
        for i in 0..<34 {
            let fi = Double(i)
            let a = M.hash(fi * 1.9) * .pi * 2
            let r = M.hash(fi * 3.7).squareRoot()
            let rr = 0.8 + 1.4 * M.hash(fi * 5.3)
            let x = cos(a) * r * 60
            let y = sin(a) * r * 7
            b.addEllipse(in: CGRect(x: x - rr, y: y - rr, width: 2 * rr, height: 2 * rr))
        }
        return b
    }()

    /// A seed half: an almond, about 16 × 9 pt, split along its length.
    static func seedHalf(_ dir: Double) -> Path {
        var b = Path()
        b.move(to: pt(-8, 0))
        b.addQuadCurve(to: pt(8, 0), control: pt(0, dir * 10))
        b.closeSubpath()
        return b
    }

    static let seedTop = seedHalf(-1)
    static let seedBottom = seedHalf(1)

    /// Leaf pointing along +x from its base, 50 pt long, 22 pt wide.
    static let leaf: Path = {
        var b = Path()
        b.move(to: .zero)
        b.addCurve(to: pt(50, 0), control1: pt(12, -14), control2: pt(34, -13))
        b.addCurve(to: .zero, control1: pt(34, 12), control2: pt(12, 13))
        b.closeSubpath()
        return b
    }()

    static let veins: Path = {
        var b = Path()
        b.move(to: pt(2, 0))
        b.addQuadCurve(to: pt(47, 0), control: pt(26, -1.5))
        for x in [12.0, 22, 32] {
            b.move(to: pt(x, -0.6))
            b.addLine(to: pt(x + 7, -6.5))
            b.move(to: pt(x + 2, 0.4))
            b.addLine(to: pt(x + 8, 6))
        }
        return b
    }()

    /// Petal pointing up (−y) from the flower's centre, 36 pt long, notched at the tip.
    static let petal: Path = {
        var b = Path()
        b.move(to: .zero)
        b.addCurve(to: pt(-4, -36), control1: pt(-12, -8), control2: pt(-11, -30))
        b.addQuadCurve(to: pt(4, -36), control: pt(0, -32))
        b.addCurve(to: .zero, control1: pt(11, -30), control2: pt(12, -8))
        b.closeSubpath()
        return b
    }()

    /// The bud: a teardrop on the stem's tip, pointing up.
    static let bud: Path = {
        var b = Path()
        b.move(to: pt(0, 2))
        b.addCurve(to: pt(0, -26), control1: pt(-12, -2), control2: pt(-9, -18))
        b.addCurve(to: pt(0, 2), control1: pt(9, -18), control2: pt(12, -2))
        b.closeSubpath()
        return b
    }()

    /// Stamens around the flower's centre.
    static let stamens: Path = {
        var b = Path()
        for i in 0..<14 {
            let fi = Double(i)
            let a = fi / 14 * .pi * 2 + M.hash(fi) * 0.3
            let r = 6 + 3 * M.hash(fi * 2.3)
            b.addEllipse(in: CGRect(x: cos(a) * r - 1.1, y: sin(a) * r - 1.1, width: 2.2, height: 2.2))
        }
        return b
    }()

    /// The stem from the soil up to `upTo` (0…1 of its length), sampled, so it really grows.
    static func stem(upTo: Double, sway: Double) -> Path {
        var b = Path()
        let n = 26
        b.move(to: .zero)
        for k in 1...n {
            let p = M.stemPoint(upTo * Double(k) / Double(n), sway)
            b.addLine(to: pt(p.x, p.y))
        }
        return b
    }

    // The watering can, in its own frame: (0, 0) is the body's centre.

    static let canBody: Path = {
        var b = Path()
        b.move(to: pt(-40, -30))
        b.addLine(to: pt(40, -30))
        b.addCurve(to: pt(46, -22), control1: pt(44, -30), control2: pt(46, -27))
        b.addLine(to: pt(50, 30))
        b.addQuadCurve(to: pt(44, 36), control: pt(50, 36))
        b.addLine(to: pt(-44, 36))
        b.addQuadCurve(to: pt(-50, 30), control: pt(-50, 36))
        b.addLine(to: pt(-46, -22))
        b.addCurve(to: pt(-40, -30), control1: pt(-46, -27), control2: pt(-44, -30))
        b.closeSubpath()
        return b
    }()

    static let canSpout: Path = {
        var b = Path()
        b.move(to: pt(-44, 8))
        b.addLine(to: pt(-80, -20))
        b.addLine(to: pt(-84, -16))
        b.addLine(to: pt(-46, 22))
        b.closeSubpath()
        return b
    }()

    /// The rose (sprinkler head) at the spout's end, a short flared cap.
    static let canRose: Path = {
        var b = Path()
        b.move(to: pt(-78, -24))
        b.addLine(to: pt(-92, -34))
        b.addQuadCurve(to: pt(-94, -14), control: pt(-100, -26))
        b.addLine(to: pt(-84, -12))
        b.closeSubpath()
        return b
    }()

    static let canHandle: Path = {
        var b = Path()
        b.move(to: pt(-18, -30))
        b.addCurve(to: pt(36, -30), control1: pt(-14, -66), control2: pt(30, -66))
        b.move(to: pt(42, -16))
        b.addCurve(to: pt(48, 22), control1: pt(66, -14), control2: pt(66, 18))
        return b
    }()

    /// Where the can's art lives in its own frame (with room for its shadow).
    static let canBounds = CGRect(x: -104, y: -72, width: 180, height: 160)

    /// Soft daylight from a window, falling across the wall in two slanted panes.
    static func windowLight(_ w: Double, _ h: Double) -> Path {
        var b = Path()
        for (x, y, pw) in [(w * 0.08, h * 0.08, w * 0.32), (w * 0.46, h * 0.04, w * 0.3)] {
            b.move(to: pt(x, y))
            b.addLine(to: pt(x + pw, y))
            b.addLine(to: pt(x + pw - h * 0.22, y + h * 0.5))
            b.addLine(to: pt(x - h * 0.22, y + h * 0.5))
            b.closeSubpath()
        }
        return b
    }

    private static func noise(_ x: Double, _ y: Double) -> Double {
        let s = sin(x * 12.9898 + y * 78.233) * 43758.5453
        return s - floor(s)
    }

    /// Grain over the wall and the pot, 100 pt square at 1.4 px per pt, mixed in with soft light.
    static let grain: Image = {
        let n = 140
        var bytes = [UInt8](repeating: 0, count: n * n)
        for j in 0..<n {
            for i in 0..<n {
                let v = noise(Double(i), Double(j)) * 0.6
                    + noise(floor(Double(i) / 3.5) + 23, floor(Double(j) / 3.5) + 23) * 0.4
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
