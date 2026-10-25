import SwiftUI

/// The coupon's clock and geometry. Every value on screen is a pure function of the loop time (or of
/// the finger's state), so the frame at t = loop is the frame at 0. Lengths are pt, angles radians,
/// times seconds. Coordinates are card-local: (0, 0) is the coupon's top-left corner at rest.
enum PerforatedCouponTearMotion {
    static let loop = 9.0

    // Script beats (s).
    static let grabAt = 1.4
    static let tearEnd = 3.6
    static let swingEnd = 4.6
    static let flyStart = swingEnd
    static let flyEnd = 5.4
    static let stampAt = 5.0
    static let stampLen = 0.38
    static let sinkStart = 6.8
    static let sinkEnd = 7.4
    static let exitEnd = 7.6
    /// The fresh coupon starts sliding in while the redeemed one is still leaving.
    static let enterStart = 7.1
    static let enterEnd = 8.2

    /// Card height; the stub is stubW wide on the right, joined by the perforation.
    static let cardH = 200.0
    static let stubW = 116.0
    static let corner = 20.0
    static let notchR = 11.0
    static let holeR = 2.7
    static let holeGap = 10.0
    static let holeY0 = notchR + 4
    /// The stub leans at most this far (rad) about the tear front while it rips.
    static let tearLean = 0.22
    /// Where the stub hangs from the finger once it is free (rad).
    static let hang = -0.09
    static let swingDecay = 3.2
    static let swingHz = 1.45
    static let pocketTilt = 0.12
    static let pocketScale = 0.6
    static let sinkDepth = 96.0

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    /// Ease-out that passes the target by about 4 % and settles.
    static func easeOutBack(_ x: Double) -> Double {
        let c = 0.9
        let m = x - 1
        return 1 + (c + 1) * m * m * m + c * m * m
    }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    static var holeCount: Int { Int(floor((cardH - 2 * notchR - 8) / holeGap)) + 1 }

    /// The tear front travels hole by hole: each gap rips quickly, then the next hole holds a moment.
    static func frontAt(_ tear: Double) -> Double {
        let n = Double(holeCount)
        let x = clamp01(tear) * n
        let k = floor(x)
        let f = x - k
        let q = min(n, k + (f < 0.3 ? 0 : (f - 0.3) / 0.7)) / n
        return notchR + (cardH - 2 * notchR) * q
    }

    /// The stub while still joined: it turns about the tear front by up to tearLean.
    static func attachedPose(_ g: PerforatedCouponTearGeom, _ tear: Double, _ nudge: Double)
        -> (front: Double, sx: Double, sy: Double, phi: Double)
    {
        let front = frontAt(tear)
        let theta = tearLean * tear + nudge
        let dx = g.gx - g.px
        let dy = g.gy - front
        let c = cos(theta)
        let s = sin(theta)
        return (front, g.px + dx * c - dy * s, front + dx * s + dy * c, theta)
    }

    static func swingAngle(_ tau: Double) -> Double {
        hang + (tearLean - hang) * exp(-swingDecay * tau) * cos(2 * .pi * swingHz * tau)
    }

    static func swingPose(_ g: PerforatedCouponTearGeom, _ tau: Double) -> (sx: Double, sy: Double, phi: Double) {
        let end = attachedPose(g, 1, 0)
        let drift = easeOut(clamp01(tau))
        return (end.sx - 46 * drift, end.sy + 34 * drift, swingAngle(tau))
    }

    static func defaultFly(_ g: PerforatedCouponTearGeom) -> PerforatedCouponTearFly {
        let p = swingPose(g, swingEnd - tearEnd)
        return PerforatedCouponTearFly(x: p.sx, y: p.sy, phi: p.phi, on: false)
    }

    static func arriving(_ g: PerforatedCouponTearGeom, _ t: Double) -> Double {
        g.travel * (1 - easeOutBack(seg(t, enterStart, enterEnd)))
    }

    /// The whole loop, as a function of t in [0, loop). `fly` is where the flight to the wallet starts.
    static func scriptPose(_ g: PerforatedCouponTearGeom, _ t: Double, _ fly: PerforatedCouponTearFly) -> PerforatedCouponTearPose {
        var p = PerforatedCouponTearPose()
        if t < tearEnd {
            let grab = seg(t, grabAt, grabAt + 0.25)
            // Anticipation: the finger presses the stub back a hair before it pulls.
            let nudge = -0.035 * sin(.pi * seg(t, grabAt, grabAt + 0.32))
            let u = seg(t, grabAt + 0.2, tearEnd)
            // Slow first holes, then the rip runs away.
            let tear = 0.22 * u + 0.78 * u * u * u
            let a = attachedPose(g, tear, nudge)
            p.tear = tear
            p.front = a.front
            p.sx = a.sx
            p.sy = a.sy
            p.phi = a.phi
            p.lift = t < grabAt ? 0 : easeOut(grab)
        } else if t < flyStart {
            let s = swingPose(g, t - tearEnd)
            p.tear = 1
            p.front = cardH
            p.sx = s.sx
            p.sy = s.sy
            p.phi = s.phi
            p.lift = 1
        } else if t < exitEnd {
            let from = fly.on ? fly : defaultFly(g)
            let u = easeInOut(seg(t, flyStart, flyEnd))
            // A quadratic arc that swings out to the right and drops into the wallet's slot.
            let cx = (from.x + g.pocketX) / 2 + 28
            let cy = from.y + (g.pocketY - from.y) * 0.3 - 24
            let a = (1 - u) * (1 - u)
            let b = 2 * (1 - u) * u
            let c = u * u
            p.tear = 1
            p.front = cardH
            p.sx = a * from.x + b * cx + c * g.pocketX
            p.sy = a * from.y + b * cy + c * g.pocketY + sinkDepth * easeInOut(seg(t, sinkStart, sinkEnd))
            p.phi = from.phi + (pocketTilt - from.phi) * u
            p.scale = 1 + (pocketScale - 1) * u
            p.lift = 1 - seg(t, flyEnd - 0.2, flyEnd)
            // The stub moves behind the pocket's front the moment its lower edge reaches it.
            let bottom = p.sy + p.scale * (cardH - g.gy) * cos(p.phi)
            p.filed = u > 0.3 && bottom > g.front - 1
            let st = seg(t, stampAt, stampAt + stampLen)
            p.stamp = t < stampAt ? 0 : 1 + 0.35 * (1 - easeOutBack(st))
            // The redeemed coupon leaves to the left.
            p.slide = -g.travel * easeInOut(seg(t, sinkStart, exitEnd))
            p.fresh = arriving(g, t)
            p.freshOn = t >= enterStart
        } else {
            // The fresh coupon (stub joined again) is now the live one; it settles with a small overshoot.
            p.slide = arriving(g, t)
        }
        if p.tear == 0 && p.sx == 0 {
            let a = attachedPose(g, 0, 0)
            p.sx = a.sx
            p.sy = a.sy
        }
        return p
    }

    /// The finger's version: joined with tear progress, or free and held at (x, y, phi).
    static func userPose(_ g: PerforatedCouponTearGeom, tear: Double, free: Bool, x: Double, y: Double, phi: Double) -> PerforatedCouponTearPose {
        var p = PerforatedCouponTearPose()
        if free {
            p.tear = 1
            p.front = cardH
            p.sx = x
            p.sy = y
            p.phi = phi
            p.lift = 1
            return p
        }
        let a = attachedPose(g, max(0, tear), 0)
        p.tear = max(0, tear)
        p.front = a.front
        p.sx = a.sx
        p.sy = a.sy
        p.phi = a.phi
        p.lift = clamp01(tear * 6)
        return p
    }
}

struct PerforatedCouponTearGeom: Equatable {
    var cw: Double
    var px: Double
    /// The stub's grab point, in card coordinates.
    var gx: Double
    var gy: Double
    /// Where the grab point lands in the wallet, in card coordinates.
    var pocketX: Double
    var pocketY: Double
    /// Width of the screen: how far the coupon slides out and in.
    var travel: Double
    /// Top of the wallet's front panel, in card coordinates.
    var front: Double
}

struct PerforatedCouponTearFly {
    var x = 0.0
    var y = 0.0
    var phi = 0.0
    var on = false
}

struct PerforatedCouponTearPose {
    var tear = 0.0
    var front = PerforatedCouponTearMotion.notchR
    var sx = 0.0
    var sy = 0.0
    var phi = 0.0
    var scale = 1.0
    var lift = 0.0
    var stamp = 0.0
    var slide = 0.0
    var filed = false
    var fresh = 0.0
    var freshOn = false
}

/// Paths of the coupon. The tear line is one seeded polyline down the perforation: the body's
/// right edge and the stub's left edge both follow it above the tear front (so the two halves
/// fit exactly) and run straight below it, where the paper is still whole.
enum PerforatedCouponTearArt {
    typealias M = PerforatedCouponTearMotion

    /// y of every edge sample, three per hole gap, hole centres included; jitter is 0 on a hole.
    static let edge: (ys: [Double], js: [Double]) = {
        var ys: [Double] = [0]
        var js: [Double] = [0]
        let step = M.holeGap / 3
        let first = Int(ceil(-M.holeY0 / step))
        let last = Int(floor((M.cardH - M.holeY0) / step))
        for j in first...last {
            let y = M.holeY0 + Double(j) * step
            if y <= 0.5 || y >= M.cardH - 0.5 { continue }
            ys.append(y)
            js.append(j % 3 == 0 ? 0 : (M.hash(Double(j) * 3.7 + 1) - 0.5) * 3.6)
        }
        ys.append(M.cardH)
        js.append(0)
        return (ys, js)
    }()

    /// The body: rounded on the left, its right edge on the tear line.
    static func body(px: Double, front: Double) -> Path {
        let (ys, js) = edge
        let h = M.cardH
        let c = M.corner
        var p = Path()
        p.move(to: CGPoint(x: c, y: 0))
        p.addLine(to: CGPoint(x: px, y: 0))
        for i in 1..<(ys.count - 1) { p.addLine(to: CGPoint(x: px + (ys[i] < front ? js[i] : 0), y: ys[i])) }
        p.addLine(to: CGPoint(x: px, y: h))
        p.addLine(to: CGPoint(x: c, y: h))
        p.addQuadCurve(to: CGPoint(x: 0, y: h - c), control: CGPoint(x: 0, y: h))
        p.addLine(to: CGPoint(x: 0, y: c))
        p.addQuadCurve(to: CGPoint(x: c, y: 0), control: CGPoint(x: 0, y: 0))
        p.closeSubpath()
        return p
    }

    /// The stub: rounded on the right, its left edge on the same tear line.
    static func stub(px: Double, cw: Double, front: Double) -> Path {
        let (ys, js) = edge
        let h = M.cardH
        let c = M.corner
        var p = Path()
        p.move(to: CGPoint(x: px, y: 0))
        p.addLine(to: CGPoint(x: cw - c, y: 0))
        p.addQuadCurve(to: CGPoint(x: cw, y: c), control: CGPoint(x: cw, y: 0))
        p.addLine(to: CGPoint(x: cw, y: h - c))
        p.addQuadCurve(to: CGPoint(x: cw - c, y: h), control: CGPoint(x: cw, y: h))
        p.addLine(to: CGPoint(x: px, y: h))
        for i in stride(from: ys.count - 2, through: 1, by: -1) {
            p.addLine(to: CGPoint(x: px + (ys[i] < front ? js[i] : 0), y: ys[i]))
        }
        p.closeSubpath()
        return p
    }

    /// The torn edge's paper core and its fibres: short hairs that stick out of the torn part,
    /// `dir` = +1 for the body (into the gap) or -1 for the stub.
    static func fibres(px: Double, front: Double, dir: Double) -> Path {
        let (ys, js) = edge
        var p = Path()
        var started = false
        for i in 0..<ys.count {
            if ys[i] > front { break }
            let pt = CGPoint(x: px + js[i], y: ys[i])
            if !started {
                p.move(to: pt)
                started = true
            } else {
                p.addLine(to: pt)
            }
        }
        for i in 1..<(ys.count - 1) {
            if ys[i] > front - 1 { break }
            let fi = Double(i)
            let h = M.hash(fi * 9.1 + (dir > 0 ? 3 : 17))
            if h < 0.35 { continue }
            let len = 1.4 + 2.6 * M.hash(fi * 5.3 + dir)
            let ang = (M.hash(fi * 2.9 + dir * 7) - 0.5) * 1.3
            let x = px + js[i]
            p.move(to: CGPoint(x: x, y: ys[i]))
            p.addLine(to: CGPoint(x: x + dir * len * cos(ang), y: ys[i] + len * sin(ang)))
        }
        return p
    }

    /// Perforation holes and the two notches, as one path.
    static func holes(px: Double) -> Path {
        var p = Path()
        for k in 0..<M.holeCount {
            let y = M.holeY0 + Double(k) * M.holeGap
            p.addEllipse(in: CGRect(x: px - M.holeR, y: y - M.holeR, width: 2 * M.holeR, height: 2 * M.holeR))
        }
        p.addEllipse(in: CGRect(x: px - M.notchR, y: -M.notchR, width: 2 * M.notchR, height: 2 * M.notchR))
        p.addEllipse(in: CGRect(x: px - M.notchR, y: M.cardH - M.notchR, width: 2 * M.notchR, height: 2 * M.notchR))
        return p
    }

    /// A barcode of horizontal bars across the stub, seeded.
    static func barcode(x: Double, y: Double, w: Double, h: Double) -> Path {
        var p = Path()
        var yy = y
        var i = 0
        while yy < y + h {
            let t = 1 + floor(M.hash(Double(i) * 1.71 + 4) * 3)
            if i % 2 == 0 { p.addRect(CGRect(x: x, y: yy, width: w, height: t)) }
            yy += t + (i % 2 == 0 ? 0 : 1)
            i += 1
        }
        return p
    }

    /// A small cup on a saucer, centred on the origin, about 14 pt wide (the stamp-card mark).
    static let cup: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -6, y: -4))
        p.addLine(to: CGPoint(x: 4, y: -4))
        p.addLine(to: CGPoint(x: 3.2, y: 2.5))
        p.addQuadCurve(to: CGPoint(x: 0.5, y: 4.5), control: CGPoint(x: 2.8, y: 4.5))
        p.addLine(to: CGPoint(x: -3.5, y: 4.5))
        p.addQuadCurve(to: CGPoint(x: -5.4, y: 2.5), control: CGPoint(x: -5.8, y: 4.5))
        p.closeSubpath()
        p.addRoundedRect(in: CGRect(x: -8, y: 5.6, width: 14, height: 1.8), cornerSize: CGSize(width: 0.9, height: 0.9))
        return p
    }()

    static let cupHandle = Path(ellipseIn: CGRect(x: 4.6 - 2.2, y: -0.6 - 2.2, width: 4.4, height: 4.4))

    /// Coffee-bean outlines tiled faintly over the body.
    static func beans(w: Double, h: Double) -> Path {
        var p = Path()
        for row in 0..<7 {
            for col in 0..<10 {
                let r = Double(row)
                let c = Double(col)
                let x = c * 30 + Double(row % 2) * 15 + (M.hash(r * 11 + c) - 0.5) * 6
                let y = r * 30 + 8 + (M.hash(r * 7 + c * 3) - 0.5) * 6
                if x > w - 6 || y > h - 6 { continue }
                p.addEllipse(in: CGRect(x: x - 5, y: y - 7, width: 10, height: 14))
                p.move(to: CGPoint(x: x, y: y - 6))
                p.addQuadCurve(to: CGPoint(x: x, y: y + 6), control: CGPoint(x: x - 2.5, y: y))
            }
        }
        return p
    }

    private static func noise(_ x: Double, _ y: Double) -> Double {
        let s = sin(x * 12.9898 + y * 78.233) * 43758.5453
        return s - floor(s)
    }

    /// Paper grain, 128 pt square, tiled: grey noise mixed in with soft light.
    static let grain: Image = {
        let n = 192 // 1.5 px per pt
        var bytes = [UInt8](repeating: 0, count: n * n)
        for j in 0..<n {
            for i in 0..<n {
                let v = noise(Double(i), Double(j)) * 0.65
                    + noise(Double(i / 3) + 31, Double(j / 3) + 31) * 0.35
                bytes[j * n + i] = UInt8(max(0, min(255, v * 255)))
            }
        }
        return grayImage(bytes, n, n, scale: 1.5, alpha: false)
    }()

    /// Stamp ink: an alpha mask where the paper's tooth did not take the ink, 100 pt square, tiled.
    static let inkMask: Image = {
        let n = 120 // 1.2 px per pt
        var bytes = [UInt8](repeating: 0, count: n * n)
        for j in 0..<n {
            for i in 0..<n {
                let v = noise(Double(i), Double(j)) * 0.6
                    + noise(floor(Double(i) / 4.8) + 7, floor(Double(j) / 4.8) + 7) * 0.6
                let x = clamp01((v - 0.08) / (0.5 - 0.08))
                bytes[j * n + i] = UInt8(x * x * (3 - 2 * x) * 255)
            }
        }
        return grayImage(bytes, n, n, scale: 1.2, alpha: true)
    }()

    private static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }

    private static func grayImage(_ bytes: [UInt8], _ w: Int, _ h: Int, scale: Double, alpha: Bool) -> Image {
        let data = Data(bytes) as CFData
        let provider = CGDataProvider(data: data)!
        let cg: CGImage
        if alpha {
            // White everywhere, with the noise as its alpha (premultiplied: the grey is the alpha).
            var rgba = [UInt8](repeating: 0, count: w * h * 4)
            for k in 0..<(w * h) {
                let a = bytes[k]
                rgba[k * 4] = a
                rgba[k * 4 + 1] = a
                rgba[k * 4 + 2] = a
                rgba[k * 4 + 3] = a
            }
            let p = CGDataProvider(data: Data(rgba) as CFData)!
            cg = CGImage(
                width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4,
                space: CGColorSpace(name: CGColorSpace.sRGB)!,
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                provider: p, decode: nil, shouldInterpolate: false, intent: .defaultIntent
            )!
        } else {
            cg = CGImage(
                width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: w,
                space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
                provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent
            )!
        }
        return Image(decorative: cg, scale: scale)
    }
}
