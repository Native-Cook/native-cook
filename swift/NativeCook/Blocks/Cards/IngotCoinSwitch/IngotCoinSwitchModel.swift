import CoreGraphics
import Foundation

/*
 Ingot Coin Switch — the timeline and the 3D ring.

 Ten pieces stand on a ring of radius 140 around a vertical axis and turn with it (piece i and i + 5
 share a style, so half a turn brings the ring back to itself). Each piece is a convex body made of
 four rings of K outline points: its outline goes from a rounded rectangle (a superellipse, the ingot)
 to a circle (the coin) with the morph m, its rings from a tapered ingot (small top face, big base) to
 a thin coin with a chamfer. The body is turned by its own yaw (a quarter turn with the morph, so a
 coin stands radially like a page of a book), leaned outward from its foot (the crown of the pin) and
 placed on the ring; then it is projected with perspective and shaded face by face.
*/

struct IngotCoinSwitchV3 {
    var x: Double
    var y: Double
    var z: Double

    static func + (a: Self, b: Self) -> Self { Self(x: a.x + b.x, y: a.y + b.y, z: a.z + b.z) }
    static func - (a: Self, b: Self) -> Self { Self(x: a.x - b.x, y: a.y - b.y, z: a.z - b.z) }
    static func * (a: Self, k: Double) -> Self { Self(x: a.x * k, y: a.y * k, z: a.z * k) }
    func dot(_ b: Self) -> Double { x * b.x + y * b.y + z * b.z }
    func cross(_ b: Self) -> Self {
        Self(x: y * b.z - z * b.y, y: z * b.x - x * b.z, z: x * b.y - y * b.x)
    }
    var length: Double { (x * x + y * y + z * z).squareRoot() }
    var normalized: Self { let l = length; return l > 1e-9 ? self * (1 / l) : self }
}

/// An RGB colour, 0…1 per channel.
struct IngotCoinSwitchRGB {
    var r: Double
    var g: Double
    var b: Double

    init(_ r: Double, _ g: Double, _ b: Double) { self.r = r; self.g = g; self.b = b }
    init(hex: UInt32) {
        self.init(Double((hex >> 16) & 0xFF) / 255, Double((hex >> 8) & 0xFF) / 255, Double(hex & 0xFF) / 255)
    }
    static func * (a: Self, k: Double) -> Self { Self(a.r * k, a.g * k, a.b * k) }
    static func + (a: Self, b: Self) -> Self { Self(a.r + b.r, a.g + b.g, a.b + b.b) }
    func mix(_ b: Self, _ t: Double) -> Self {
        Self(r + (b.r - r) * t, g + (b.g - g) * t, self.b + (b.b - self.b) * t)
    }
    var clamped: Self { Self(min(max(r, 0), 1), min(max(g, 0), 1), min(max(b, 0), 1)) }
}

/// One frame of the screen, whatever drives it (the loop, the finger or the way back to rest).
struct IngotCoinSwitchFrame {
    /// The ring's angle, radians.
    var phi = 0.0
    /// The morph of each piece: 0 ingot, 1 coin.
    var m = [Double](repeating: 0, count: IngotCoinSwitchModel.count)
    /// The switch's dark capsule: 0 on "Commodities", 1 on "Stocks" (a spring, it overshoots).
    var sel = 0.0
    /// The copy crossfades from one mode (0 commodities, 1 stocks) to the other, `copyU` s after the tap.
    var copyFrom = 0
    var copyTo = 0
    var copyU = 99.0
    var reduced = false
}

/// A quad of a piece's side, ready to fill.
struct IngotCoinSwitchQuad {
    var points: [CGPoint]
    var color: IngotCoinSwitchRGB
}

/// A flat face (the ingot's top or base, a coin's face) with its sliding highlight and its mark.
struct IngotCoinSwitchCap {
    var points: [CGPoint]
    /// Colours of the gradient across the face, at `IngotCoinSwitchModel.capStops`.
    var colors: [IngotCoinSwitchRGB]
    var from: CGPoint
    var to: CGPoint
    /// Top and bottom of the face, for the sheen.
    var top: CGPoint
    var bottom: CGPoint
    /// Maps the mark's image (centred on 0, y down, in the face's units) onto the screen.
    var transform: CGAffineTransform
    var ingotAlpha: Double
    var coinAlpha: Double
}

struct IngotCoinSwitchPiece {
    var index: Int
    var depth: Double
    var opacity: Double
    var silhouette: [CGPoint]
    var silhouetteColor: IngotCoinSwitchRGB
    var quads: [IngotCoinSwitchQuad]
    var caps: [IngotCoinSwitchCap]
}

enum IngotCoinSwitchModel {
    typealias V3 = IngotCoinSwitchV3
    typealias RGB = IngotCoinSwitchRGB

    /// One loop of the self-playing demo: rest, switch to coins, hold, switch back, rest.
    static let loop = 10.0
    static let count = 10
    static let styles = 5
    static let ringR = 120.0
    /// The ring's size on the screen.
    static let viewScale = 0.9
    static let camD = 900.0
    static let focal = 900.0
    /// Outline points per ring.
    static let k = 48

    /// The loop's taps on the switch, and the morph that follows each.
    static let switchOn = 2.4
    static let switchOff = 6.4
    static let morphLag = 0.1
    static let morphDur = 1.1
    /// The morph runs as a wave from the front piece to the sides.
    static let waveDelay = 0.32
    static let idleBeforeBack = 2.0
    /// The ring's slow turn: half a turn per loop.
    static let omega = Double.pi / loop
    /// Rotating the ring by this angle brings it back to itself.
    static let home = Double.pi

    // MARK: Pieces

    static let ingotHex: [UInt32] = [0xD8B25A, 0xD9967F, 0xB8BCC7, 0x50525C, 0xC7A47E]
    static let coinHex: [UInt32] = [0xA9A3B2, 0xE4B9AE, 0xECDCD6, 0x86A1C9, 0xC78E86]

    static let metals = ["Gold", "Copper", "Silver", "Platinum", "Palladium"]
    static let metalPrices = ["$78.42 /g", "$9.36 /kg", "$1.04 /g", "$33.90 /g", "$35.12 /g"]
    static let metalMoves = ["+1.2%", "+0.8%", "+2.1%", "+0.4%", "+1.7%"]
    static let tickers = ["HLDN", "NWG", "ORF", "VLA", "QRY"]
    static let marks = ["H", "N", "O", "V", "Q"]
    static let stockPrices = ["$42.18", "$118.60", "$27.35", "$64.02", "$211.40"]
    static let stockMoves = ["+2.4%", "+0.9%", "+1.6%", "+3.1%", "+0.6%"]

    /// Ingot: half width and height of the outline; coin: radius.
    static let ingotA = 40.0
    static let ingotB = 108.0
    static let coinR = 78.0
    /// The four rings of a body, (z, scale x, scale y), for the ingot and the coin.
    static let ingotRings: [(Double, Double, Double)] = [(22, 0.80, 0.91), (-17, 1, 1), (-21, 1, 1), (-22, 0.97, 0.98)]
    static let coinRings: [(Double, Double, Double)] = [(7.5, 0.94, 0.94), (5, 1, 1), (-5, 1, 1), (-7.5, 0.94, 0.94)]

    /// Locations of the colours across a face.
    static let capStops: [Double] = [0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1]

    // MARK: Curves

    static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func easeOut(_ x: Double) -> Double { let u = 1 - x; return 1 - u * u * u }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    /// An angle wrapped into −π…π.
    static func wrap(_ a: Double) -> Double {
        var x = a.truncatingRemainder(dividingBy: 2 * Double.pi)
        if x > Double.pi { x -= 2 * Double.pi }
        if x < -Double.pi { x += 2 * Double.pi }
        return x
    }

    /// A damped spring from x0 (velocity v0) to `target`, tau seconds in.
    static func spring(_ tau: Double, x0: Double, v0: Double, to target: Double,
                       response: Double, damping: Double) -> Double {
        if tau <= 0 { return x0 }
        let z = min(damping, 0.999)
        let w0 = 2 * Double.pi / response
        let wd = w0 * (1 - z * z).squareRoot()
        let a = x0 - target
        let b = (v0 + z * w0 * a) / wd
        return target + exp(-z * w0 * tau) * (a * cos(wd * tau) + b * sin(wd * tau))
    }

    static let selResponse = 0.42
    static let selDamping = 0.78

    static func theta(_ i: Int, _ phi: Double) -> Double { phi + 2 * Double.pi * Double(i) / Double(count) }

    /// The delay of piece i in the morph wave, from where it stands on the ring.
    static func delay(_ i: Int, _ phi: Double) -> Double {
        waveDelay * abs(wrap(theta(i, phi))) / Double.pi
    }

    /// The morph of a piece from m0 to `target`, `u` s after its wave started.
    static func morph(_ u: Double, delay: Double, from m0: Double, to target: Double, duration: Double = morphDur) -> Double {
        m0 + (target - m0) * easeInOut(seg(u, delay, delay + duration))
    }

    // MARK: autoPlay

    static func autoFrame(_ t: Double) -> IngotCoinSwitchFrame {
        var f = IngotCoinSwitchFrame()
        f.phi = omega * t
        let on = switchOn + morphLag
        let off = switchOff + morphLag
        for i in 0..<count {
            let up = morph(t - on, delay: delay(i, omega * on), from: 0, to: 1)
            let down = morph(t - off, delay: delay(i, omega * off), from: 0, to: 1)
            f.m[i] = up - down
        }
        if t < switchOn {
            f.sel = 0
        } else if t < switchOff {
            f.sel = spring(t - switchOn, x0: 0, v0: 0, to: 1, response: selResponse, damping: selDamping)
            f.copyFrom = 0; f.copyTo = 1; f.copyU = t - switchOn
        } else {
            let x0 = spring(switchOff - switchOn, x0: 0, v0: 0, to: 1, response: selResponse, damping: selDamping)
            f.sel = spring(t - switchOff, x0: x0, v0: 0, to: 0, response: selResponse, damping: selDamping)
            f.copyFrom = 1; f.copyTo = 0; f.copyU = t - switchOff
        }
        return f
    }

    /// Opacity of the outgoing and incoming copy, and the rise of the incoming one.
    static func copyFade(_ u: Double) -> (out: Double, inn: Double, rise: Double) {
        let o = 1 - easeOut(seg(u, 0, 0.15))
        let e = easeOut(seg(u, 0.12, 0.35))
        return (o, e, 6 * (1 - e))
    }

    /// The piece nearest the front, and how much its ticker label shows (it fades at each change).
    static func front(_ f: IngotCoinSwitchFrame) -> (index: Int, alpha: Double) {
        let slot = 2 * Double.pi / Double(count)
        let x = -f.phi / slot
        var i = Int((x).rounded()) % count
        if i < 0 { i += count }
        let frac = (x + 0.5) - (x + 0.5).rounded(.down)
        let edge = clamp01(min(frac, 1 - frac) / 0.07)
        let mm = f.m[i]
        let swap = clamp01(abs(mm - 0.5) * 5)
        return (i, f.reduced ? swap : min(edge, swap))
    }

    // MARK: Material

    static let keyL = V3(x: -0.45, y: 0.6, z: 0.66).normalized
    static let rimL = V3(x: 0.85, y: 0.15, z: 0.5).normalized
    static let keyH = (keyL + V3(x: 0, y: 0, z: 1)).normalized
    static let rimH = (rimL + V3(x: 0, y: 0, z: 1)).normalized

    /// Metal (or frosted coin metal, `frost` 1) under a key light and a rim light.
    static func shade(_ base: RGB, _ n: V3, frost: Double) -> RGB {
        let nv = max(0, n.z)
        let deep = RGB(base.r * base.r, base.g * base.g, base.b * base.b) * 0.62
        var c = deep.mix(base, smooth(0, 0.95, nv))
        c = c * (0.6 + 0.42 * max(0, n.dot(keyL)))
        let spec = 0.95 * pow(max(0, n.dot(keyH)), lerp(36, 22, frost)) + 0.4 * pow(max(0, n.dot(rimH)), 10)
        let hi = base.mix(RGB(1, 1, 1), lerp(0.62, 0.5, frost))
        c = c + hi * (spec * lerp(1, 0.75, frost))
        return c.clamped
    }

    /// The colours across a face: its flat shade with a highlight band at `p` (0…1 across the face).
    static func capColors(_ base: RGB, _ n: V3, p: Double, frost: Double) -> [RGB] {
        let flat = shade(base, n, frost: frost)
        let hi = flat.mix(RGB(1, 1, 1), lerp(0.6, 0.4, frost))
        return capStops.map { x in
            let edge = 0.86 + 0.14 * (1 - pow(2 * x - 1, 2)) - 0.06 * x
            let band = exp(-pow((x - p) / 0.17, 2))
            // Polished metal also mirrors the dark room: a darker band on each side of the highlight.
            let dark = exp(-pow((x - p - 0.42) / 0.16, 2)) + exp(-pow((x - p + 0.42) / 0.16, 2))
            return (flat * (edge * (1 - 0.3 * dark))).mix(hi, band * 0.9).clamped
        }
    }

    // MARK: Geometry

    /// The convex hull of points (monotone chain).
    static func hull(_ input: [CGPoint]) -> [CGPoint] {
        let p = input.sorted { $0.x == $1.x ? $0.y < $1.y : $0.x < $1.x }
        if p.count < 3 { return p }
        func cross(_ o: CGPoint, _ a: CGPoint, _ b: CGPoint) -> Double {
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        }
        var lower: [CGPoint] = []
        for q in p {
            while lower.count >= 2 && cross(lower[lower.count - 2], lower[lower.count - 1], q) <= 0 { lower.removeLast() }
            lower.append(q)
        }
        var upper: [CGPoint] = []
        for q in p.reversed() {
            while upper.count >= 2 && cross(upper[upper.count - 2], upper[upper.count - 1], q) <= 0 { upper.removeLast() }
            upper.append(q)
        }
        lower.removeLast()
        upper.removeLast()
        return lower + upper
    }

    static func outline(_ j: Int, _ m: Double) -> (Double, Double) {
        let a = 2 * Double.pi * Double(j) / Double(k)
        let c = cos(a)
        let s = sin(a)
        let e = 2.0 / 6.0
        let ix = ingotA * (c < 0 ? -1 : 1) * pow(abs(c), e)
        let iy = ingotB * (s < 0 ? -1 : 1) * pow(abs(s), e)
        return (lerp(ix, coinR * c, m), lerp(iy, coinR * s, m))
    }

    /// The pieces of the ring, sorted far to near, projected around (cx, cy).
    static func pieces(_ f: IngotCoinSwitchFrame, cx: Double, cy: Double) -> [IngotCoinSwitchPiece] {
        var out: [IngotCoinSwitchPiece] = []
        out.reserveCapacity(count)
        let outlines0 = (0..<k).map { outline($0, 0) }
        let outlines1 = (0..<k).map { outline($0, 1) }
        for i in 0..<count {
            out.append(piece(i, f, cx: cx, cy: cy, o0: outlines0, o1: outlines1))
        }
        return out.sorted { $0.depth < $1.depth }
    }

    static func piece(_ i: Int, _ f: IngotCoinSwitchFrame, cx: Double, cy: Double,
                      o0: [(Double, Double)], o1: [(Double, Double)]) -> IngotCoinSwitchPiece {
        let m = f.m[i]
        let th = theta(i, f.phi)
        let style = i % styles
        let yaw = Double.pi / 2 * m
        let lean = 0.2 * (1 - 0.75 * m)
        let bump = f.reduced ? 0 : sin(Double.pi * m)
        let pop = 1 + 0.05 * bump
        let half = lerp(ingotB, coinR, m)
        let cyaw = cos(yaw), syaw = sin(yaw)
        let cl = cos(lean), sl = sin(lean)
        let ct = cos(th), st = sin(th)
        let cam = V3(x: 0, y: 0, z: camD)

        func world(_ x0: Double, _ y0: Double, _ z0: Double) -> V3 {
            var x = x0 * pop, y = y0 * pop, z = z0 * pop
            let x1 = x * cyaw + z * syaw
            z = -x * syaw + z * cyaw
            x = x1
            let yy = y + half
            y = -half + yy * cl - z * sl
            z = yy * sl + z * cl
            let x2 = x * ct + z * st
            z = -x * st + z * ct
            return V3(x: x2 + ringR * st, y: y, z: z + ringR * ct)
        }
        func project(_ p: V3) -> CGPoint {
            let s = viewScale * focal / (camD - p.z)
            return CGPoint(x: cx + p.x * s, y: cy - p.y * s)
        }

        let rings = (0..<4).map { r -> (Double, Double, Double) in
            let a = ingotRings[r], b = coinRings[r]
            return (lerp(a.0, b.0, m), lerp(a.1, b.1, m), lerp(a.2, b.2, m))
        }
        var pts3 = [[V3]]()
        var pts2 = [[CGPoint]]()
        for r in 0..<4 {
            var row3 = [V3]()
            var row2 = [CGPoint]()
            row3.reserveCapacity(k)
            row2.reserveCapacity(k)
            for j in 0..<k {
                let ox = lerp(o0[j].0, o1[j].0, m)
                let oy = lerp(o0[j].1, o1[j].1, m)
                let p = world(ox * rings[r].1, oy * rings[r].2, rings[r].0)
                row3.append(p)
                row2.append(project(p))
            }
            pts3.append(row3)
            pts2.append(row2)
        }

        let center = world(0, 0, 0)
        let base = RGB(hex: ingotHex[style]).mix(RGB(hex: coinHex[style]), smooth(0.25, 0.75, m))
        let frost = smooth(0.25, 0.75, m)
        // A coin's edge is brighter, plain metal, whatever its face's tint.
        let rim = base.mix(RGB(hex: 0xD9D8DC), 0.4 * frost)
        // Pieces at the back of the ring sit in the ring's shade.
        let shadeK = lerp(0.72, 1, smooth(-ringR, ringR * 0.4, center.z))

        // Side quads facing the camera; neighbours of (almost) the same colour merge into one strip.
        var quads: [IngotCoinSwitchQuad] = []
        var sum = RGB(0, 0, 0)
        var seen = 0.0
        for r in 0..<3 {
            var run: [Int] = []
            var runColor = RGB(0, 0, 0)
            func flush() {
                guard let j0 = run.first, let j1 = run.last else { return }
                var pts: [CGPoint] = []
                for j in j0...(j1 + 1) { pts.append(pts2[r][j % k]) }
                for j in stride(from: j1 + 1, through: j0, by: -1) { pts.append(pts2[r + 1][j % k]) }
                quads.append(IngotCoinSwitchQuad(points: pts, color: runColor))
                run.removeAll()
            }
            for j in 0..<k {
                let jn = (j + 1) % k
                let a = pts3[r][j], b = pts3[r][jn], c = pts3[r + 1][jn], d = pts3[r + 1][j]
                var n = (b - a).cross(d - a)
                if n.length < 1e-6 { n = (c - b).cross(d - b) }
                let mid = (a + b + c + d) * 0.25
                if n.length < 1e-6 { flush(); continue }
                if n.dot(mid - center) < 0 { n = n * -1 }
                n = n.normalized
                if n.dot(cam - mid) <= 0 { flush(); continue }
                let col = shade(rim, n, frost: frost) * shadeK
                sum = sum + col
                seen += 1
                if !run.isEmpty {
                    let dc = max(abs(col.r - runColor.r), abs(col.g - runColor.g), abs(col.b - runColor.b))
                    if dc > 0.012 { flush() }
                }
                if run.isEmpty { runColor = col }
                run.append(j)
            }
            flush()
        }

        // The silhouette, filled first so no background shows through the seams between faces.
        var all: [CGPoint] = []
        all.reserveCapacity(4 * k)
        for row in pts2 { all.append(contentsOf: row) }
        let silhouette = hull(all)
        let silhouetteColor = seen > 0 ? sum * (1 / seen) : base * shadeK

        var caps: [IngotCoinSwitchCap] = []
        let axisZ = (world(0, 0, 1) - world(0, 0, 0)).normalized
        for (r, sign) in [(0, 1.0), (3, -1.0)] {
            let zc = rings[r].0
            let cc = world(0, 0, zc)
            let n = axisZ * sign
            let toCam = (cam - cc).normalized
            let facing = n.dot(toCam)
            if facing <= 0 { continue }
            let hw = lerp(ingotA, coinR, m) * rings[r].1
            let hh = half * rings[r].2
            // Across the face, left to right as seen from its front.
            let left = project(world(-hw * sign, 0, zc))
            let right = project(world(hw * sign, 0, zc))
            let viewYaw = atan2(n.x, n.z)
            let p = 0.5 - 0.85 * sin(viewYaw)
            let o = project(cc)
            let px = project(world(40 * sign * rings[r].1, 0, zc))
            let py = project(world(0, 40 * rings[r].2, zc))
            let ux = (px.x - o.x) / 40, uy = (px.y - o.y) / 40
            let vx = (py.x - o.x) / 40, vy = (py.y - o.y) / 40
            let show = smooth(0.08, 0.4, facing)
            caps.append(IngotCoinSwitchCap(
                points: pts2[r],
                colors: capColors(base, n, p: p, frost: frost).map { $0 * shadeK },
                from: left, to: right,
                top: project(world(0, hh, zc)), bottom: project(world(0, -hh, zc)),
                transform: CGAffineTransform(a: ux, b: uy, c: -vx, d: -vy, tx: o.x, ty: o.y),
                ingotAlpha: r == 0 ? show * smooth(0.5, 0.2, m) : 0,
                coinAlpha: show * smooth(0.5, 0.8, m)))
        }

        let opacity = f.reduced ? 1 : 1 - 0.45 * bump
        return IngotCoinSwitchPiece(index: i, depth: center.z, opacity: opacity, silhouette: silhouette,
                                    silhouetteColor: silhouetteColor, quads: quads, caps: caps)
    }
}
