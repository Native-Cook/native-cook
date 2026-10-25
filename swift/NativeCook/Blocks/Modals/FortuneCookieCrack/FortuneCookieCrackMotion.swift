import SwiftUI

/// The fortune cookie's clock. Every value is a closed-form function of the loop time t, so the
/// frame at t = loop is the frame at 0, and a finger only moves t (press = play the squeeze, tap
/// when open = play the close). Times are seconds, lengths pt, angles radians.
enum FortuneCookieCrackMotion {
    static let loop = 9.0

    static let squeezeAt = 1.4
    static let crackAt = 2.0
    /// Where a quick tap starts playing (a shorter squeeze).
    static let tapFrom = 1.72
    /// The slip is fully unrolled here; the finger's playback pauses on it.
    static let openAt = 4.4
    static let closeAt = 6.4

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    /// Ease-out that passes the target by about 5 % and settles back.
    static func easeOutBack(_ x: Double) -> Double {
        let c = 1.1
        let m = x - 1
        return 1 + (c + 1) * m * m * m + c * m * m
    }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    struct Pose {
        /// Idle bob (pt) and tilt (rad), only while resting.
        var bob = 0.0
        var tilt = 0.0
        /// Squash of the squeeze.
        var sx = 1.0
        var sy = 1.0
        /// Shake before the snap (rad).
        var shake = 0.0
        /// Crack lines drawn 0…1, and how far the halves have opened 0…1 (overshoots a little).
        var crack = 0.0
        var open = 0.0
        /// Rolled slip: emerging, dropped to its place, unrolled (each 0…1).
        var emerge = 0.0
        var drop = 0.0
        var unroll = 0.0
        /// Seconds since the snap (< 0 before), and the crumbs' fade.
        var crumbT = -1.0
        var crumbA = 1.0
        /// 0 closed … 1 open: drives the roll of the sub label.
        var stage = 0.0
        var t = 0.0
    }

    /// The whole loop as a function of t in [0, loop).
    static func cookieAt(_ t: Double) -> Pose {
        let rest = 1 - easeInOut(seg(t, 1.0, squeezeAt)) + easeInOut(seg(t, 8.4, 8.9))
        let w = min(1, rest)
        let squeeze = easeInOut(seg(t, squeezeAt, crackAt)) * (1 - easeOut(seg(t, crackAt, 2.25)))
        let shakeEnv = sin(.pi * seg(t, 1.72, crackAt))
        let crack = easeOut(seg(t, crackAt, 2.45)) * (1 - easeInOut(seg(t, 8.0, 8.4)))
        // A hairline gap as the crack runs, then the snap: halves swing apart and settle.
        let pre = 0.06 * easeOut(seg(t, 2.1, 2.5))
        let open = (pre + (1 - pre) * easeOutBack(seg(t, 2.5, 3.2))) * (1 - easeInOut(seg(t, 7.4, 8.0)))
        var p = Pose()
        p.bob = 3 * sin(2 * .pi * 4 * t / loop) * w
        p.tilt = 0.014 * sin(2 * .pi * 2 * t / loop + 0.6) * w
        p.sx = 1 + 0.05 * squeeze
        p.sy = 1 - 0.065 * squeeze
        p.shake = 0.022 * shakeEnv * sin(2 * .pi * 81 * t / loop)
        p.crack = crack
        p.open = open
        p.emerge = easeOut(seg(t, 3.15, 3.5)) * (1 - easeInOut(seg(t, 7.25, 7.5)))
        p.drop = easeInOut(seg(t, 3.45, 3.85)) * (1 - easeInOut(seg(t, 6.95, 7.35)))
        p.unroll = easeInOut(seg(t, 3.8, openAt)) * (1 - easeInOut(seg(t, closeAt, 7.0)))
        p.crumbT = t >= 2.5 && t < 5 ? t - 2.5 : -1
        p.crumbA = 1 - seg(t, 4.2, 4.8)
        p.stage = easeInOut(seg(t, 3.3, 3.7)) * (1 - easeInOut(seg(t, 6.6, 7.0)))
        p.t = t
        return p
    }

    /// Lucky number chip i: 0 hidden … 1 shown (with a pop).
    static func chipAt(_ t: Double, _ i: Int) -> Double {
        let a = 4.05 + 0.06 * Double(i)
        return easeOutBack(seg(t, a, a + 0.36)) * (1 - easeInOut(seg(t, closeAt, closeAt + 0.3)))
    }

    static let crumbs = 26
    static let gravity = 1100.0

    /// Crumb i, tau seconds after the snap, in cookie-local coordinates, with `floor` the plate's
    /// surface. Crumbs leave from the crack, fly out and land on the plate.
    static func crumbAt(_ i: Int, _ tau: Double, _ floorY: Double) -> (x: Double, y: Double, r: Double, on: Bool) {
        let fi = Double(i)
        let sx = (hash(fi * 3.1) - 0.5) * 14
        let sy = -30 + hash(fi * 5.7) * 90
        let side: Double = hash(fi * 1.3) < 0.5 ? -1 : 1
        let vx = side * (40 + 160 * hash(fi * 7.9))
        let vy = -120 - 260 * hash(fi * 2.3)
        let delay = 0.08 * hash(fi * 4.4)
        let tt = max(0, tau - delay)
        var x = sx + vx * tt
        var y = sy + vy * tt + 0.5 * gravity * tt * tt
        let land = floorY - 4 + 12 * hash(fi * 6.6)
        if y > land {
            // Time it reached the plate: from there it slides a little and stops.
            let hit = (-vy + (vy * vy + 2 * gravity * (land - sy)).squareRoot()) / gravity
            x = sx + vx * hit + vx * 0.06 * (1 - exp(-(tt - hit) * 8))
            y = land
        }
        let r = 1.1 + 2.1 * hash(fi * 8.8)
        return (x, y, r, tau >= delay)
    }
}

/// The cookie, drawn in its own coordinates: centre (0, 0), about 230 pt wide, front view of the
/// folded crescent. It is split once into two halves along a seeded jagged crack.
struct FortuneCookieCrackShapes {
    let left: Path
    let right: Path
    let leftLip: Path
    let rightLip: Path
    let whole: Path
    let wholeLip: Path
    let inside: Path
    let leftInside: Path
    let rightInside: Path
    let rim: Path
    let crease: Path
    let crack: Path
    let branches: Path
    let pivotL = CGPoint(x: -102, y: 36)
    let pivotR = CGPoint(x: 102, y: 36)

    /// Where the cookie's art lives in its own coordinates (with room for the lip's shadow).
    static let bounds = CGRect(x: -126, y: -70, width: 252, height: 136)

    init() {
        let H = FortuneCookieCrackMotion.self
        func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x, y: y) }
        // One smooth dome on top, two wings below pinched into the fold's notch in the middle.
        var o = Path()
        o.move(to: pt(-116, 32))
        o.addCurve(to: pt(0, -66), control1: pt(-124, -26), control2: pt(-66, -66))
        o.addCurve(to: pt(116, 32), control1: pt(66, -66), control2: pt(124, -26))
        o.addCurve(to: pt(26, 54), control1: pt(102, 54), control2: pt(58, 64))
        o.addQuadCurve(to: pt(0, 4), control: pt(10, 42))
        o.addQuadCurve(to: pt(-26, 54), control: pt(-10, 42))
        o.addCurve(to: pt(-116, 32), control1: pt(-58, 64), control2: pt(-102, 54))
        o.closeSubpath()
        // The folded-over flaps either side of the notch.
        var l = Path()
        var r = Path()
        for d in [-1.0, 1.0] {
            l.move(to: pt(0, 4))
            l.addCurve(to: pt(d * 113, 31), control1: pt(d * 30, 12), control2: pt(d * 82, 8))
            l.addCurve(to: pt(d * 26, 54), control1: pt(d * 102, 54), control2: pt(d * 58, 64))
            l.addQuadCurve(to: pt(0, 4), control: pt(d * 10, 42))
            l.closeSubpath()
            r.move(to: pt(d * 2, 6))
            r.addCurve(to: pt(d * 112, 31), control1: pt(d * 30, 12), control2: pt(d * 82, 8))
        }
        // The crack: from the crease down to the bottom edge, x jitter of ±5 pt every 8.5 pt.
        var pts: [CGPoint] = []
        for k in 0...14 {
            let y = -74 + Double(k) * 8.5
            let jx = k == 0 ? 0 : (H.hash(Double(k) * 2.7 + 3) - 0.5) * 10
            pts.append(pt(jx, y))
        }
        func side(_ dir: Double) -> Path {
            var p = Path()
            p.move(to: pt(pts[0].x, -200))
            for q in pts { p.addLine(to: q) }
            p.addLine(to: pt(pts[pts.count - 1].x, 200))
            p.addLine(to: pt(dir * 300, 200))
            p.addLine(to: pt(dir * 300, -200))
            p.closeSubpath()
            return p
        }
        let L = side(-1)
        let R = side(1)
        let pocket = Path(ellipseIn: CGRect(x: -15, y: 8, width: 30, height: 33))
        var crease = Path()
        crease.move(to: pt(0, 4))
        crease.addQuadCurve(to: pt(0, -36), control: pt(-2, -14))
        var c = Path()
        c.move(to: pts[0])
        for q in pts { c.addLine(to: q) }
        var br = Path()
        br.move(to: pts[3])
        br.addLine(to: pt(pts[3].x - 12, pts[3].y + 6))
        br.addLine(to: pt(pts[3].x - 24, pts[3].y + 4))
        br.move(to: pts[6])
        br.addLine(to: pt(pts[6].x + 10, pts[6].y + 4))
        br.addLine(to: pt(pts[6].x + 20, pts[6].y + 1))

        left = o.intersection(L)
        right = o.intersection(R)
        leftLip = l.intersection(L)
        rightLip = l.intersection(R)
        whole = o
        wholeLip = l
        inside = pocket
        leftInside = pocket.intersection(L)
        rightInside = pocket.intersection(R)
        rim = r
        self.crease = crease
        crack = c
        branches = br
    }
}

/// Baked cookie texture and paper grain, computed once off the main thread.
enum FortuneCookieCrackTexture {
    private static func h(_ x: Double, _ y: Double) -> Double {
        let s = sin(x * 12.9898 + y * 78.233) * 43758.5453
        return s - floor(s)
    }

    private static func vn(_ x: Double, _ y: Double) -> Double {
        let ix = floor(x)
        let iy = floor(y)
        var fx = x - ix
        var fy = y - iy
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        let a = h(ix, iy)
        let b = h(ix + 1, iy)
        let c = h(ix, iy + 1)
        let d = h(ix + 1, iy + 1)
        let top = a + (b - a) * fx
        let bot = c + (d - c) * fx
        return top + (bot - top) * fy
    }

    private static func fbm(_ x: Double, _ y: Double) -> Double {
        vn(x, y) * 0.55 + vn(x * 2.03 + 7.1, y * 2.03 + 7.1) * 0.3 + vn(x * 4.11 + 3.7, y * 4.11 + 3.7) * 0.15
    }

    private static func sstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = min(1, max(0, (x - a) / (b - a)))
        return t * t * (3 - 2 * t)
    }

    /// Golden in the middle, toasted toward the rim and tips, sheen on the two lobes, a fine baked
    /// texture (value-noise fbm, three octaves), pits and a few sugar sparkles. Same maths as the
    /// RN version's shader, at `shade` brightness.
    static func cookieColor(_ px: Double, _ py: Double) -> (Double, Double, Double) {
        let qx = px / 114
        let qy = py / 64
        let v = min(1, max(0, qy * 0.5 + 0.5))
        let top = (0.97, 0.80, 0.47)
        let mid = (0.91, 0.66, 0.31)
        let bot = (0.76, 0.48, 0.19)
        var c: (Double, Double, Double)
        if v < 0.5 {
            let k = v * 2
            c = (top.0 + (mid.0 - top.0) * k, top.1 + (mid.1 - top.1) * k, top.2 + (mid.2 - top.2) * k)
        } else {
            let k = (v - 0.5) * 2
            c = (mid.0 + (bot.0 - mid.0) * k, mid.1 + (bot.1 - mid.1) * k, mid.2 + (bot.2 - mid.2) * k)
        }
        // A broad sheen on the dome and a tighter glint: the baked sugar is a little glossy.
        let ax = (px + 34) / 62
        let ay = (py + 36) / 26
        let sheen = exp(-(ax * ax + ay * ay))
        c = (c.0 + 0.15 * sheen, c.1 + 0.12 * sheen, c.2 + 0.07 * sheen)
        let gx = (px + 48) / 22
        let gy = (py + 46) / 7
        let glint = exp(-(gx * gx + gy * gy))
        c = (c.0 + 0.2 * glint, c.1 + 0.18 * glint, c.2 + 0.13 * glint)
        // Toasted toward the wing tips and the folded bottom.
        let e = (qx * qx + qy * 1.1 * qy * 1.1).squareRoot()
        var k = (1 - 0.36 * sstep(0.74, 1.1, e)) * (1 - 0.16 * sstep(0.15, 0.8, qy))
        c = (c.0 * k, c.1 * k, c.2 * k)
        let blot = sstep(0.55, 0.82, fbm(px * 0.05 + 11, py * 0.05 + 11)) * 0.4
        c = (c.0 + (c.0 * 0.86 - c.0) * blot, c.1 + (c.1 * 0.74 - c.1) * blot, c.2 + (c.2 * 0.62 - c.2) * blot)
        k = (0.93 + 0.12 * fbm(px * 0.12, py * 0.12)) * (0.97 + 0.05 * h(floor(px * 1.3), floor(py * 1.3)))
        c = (c.0 * k, c.1 * k, c.2 * k)
        let spark = h(floor(px * 0.9), floor(py * 0.9)) >= 0.994 ? 1.0 : 0.0
        c = (c.0 + 0.12 * spark, c.1 + 0.1 * spark, c.2 + 0.07 * spark)
        return c
    }

    /// The cookie's texture over `FortuneCookieCrackShapes.bounds`, at `scale` px per pt, for two shades.
    static func cookie(scale: Double, shades: [Double]) -> [CGImage] {
        let b = FortuneCookieCrackShapes.bounds
        let w = Int(b.width * scale)
        let hgt = Int(b.height * scale)
        var buffers = shades.map { _ in [UInt8](repeating: 255, count: w * hgt * 4) }
        for j in 0..<hgt {
            let py = b.minY + (Double(j) + 0.5) / scale
            for i in 0..<w {
                let px = b.minX + (Double(i) + 0.5) / scale
                let c = cookieColor(px, py)
                let o = (j * w + i) * 4
                for (n, s) in shades.enumerated() {
                    buffers[n][o] = UInt8(max(0, min(255, c.0 * s * 255)))
                    buffers[n][o + 1] = UInt8(max(0, min(255, c.1 * s * 255)))
                    buffers[n][o + 2] = UInt8(max(0, min(255, c.2 * s * 255)))
                }
            }
        }
        return buffers.map { image($0, w, hgt) }
    }

    /// Paper grain, 100 pt square at 1.4 px per pt, tiled with soft light.
    static func grain() -> CGImage {
        let n = 140
        var bytes = [UInt8](repeating: 255, count: n * n * 4)
        for j in 0..<n {
            for i in 0..<n {
                let v = h(Double(i), Double(j)) * 0.6 + h(Double(i / 4) + 13, Double(j / 4) + 13) * 0.4
                let g = UInt8(max(0, min(255, v * 255)))
                let o = (j * n + i) * 4
                bytes[o] = g
                bytes[o + 1] = g
                bytes[o + 2] = g
            }
        }
        return image(bytes, n, n)
    }

    private static func image(_ rgba: [UInt8], _ w: Int, _ h: Int) -> CGImage {
        let provider = CGDataProvider(data: Data(rgba) as CFData)!
        return CGImage(
            width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4,
            space: CGColorSpace(name: CGColorSpace.sRGB)!,
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
            provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent
        )!
    }
}
