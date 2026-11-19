import Foundation

// One loop drives the whole film. Every film value is a pure function of the loop time t (0 <= t < loop), so t = loop
// gives the same frame as t = 0.
//
//  0.0 –  0.9  poster: the whole city, the blue route from the hub to Home, "Arriving in 12 min"
//  0.9 – 12.6  the drive. The camera frames the van and the door together, so it zooms in by itself as the van gets
//              closer: whole city → district → street; the map turns 37° until north is up and tilts into 3D.
//  5.25 – 7.0  a finger drags the map aside to look round (the camera stops following; the locate button empties)
//  7.5  – 8.3  a tap on the locate button: the camera glides back onto the van
//  ~8.8 – 12.6 the last 100 m: right in to the door, the van parks; 12.45 "Arrived", the door pulses
// 13.1  – 14.45 Call is tapped: a "Calling Marco…" pill shows, then the call ends
// 14.45 – 17.0 Message is tapped: the quick reply "Coming down now" is sent (the toast from 14.6)
// 15.45 – 18.35 the camera pulls back out to the whole city; the van leaves the door and the next round starts at the
//              hub. 18.35 – 19.0 poster. Last frame = first frame.

/// The screen camera: centre (m), zoom (pt per m), rotation, focus point, ground squash and tilt.
struct CourierZoomTrackerCam {
    var cx: Double, cy: Double, s: Double, cosB: Double, sinB: Double, fx: Double, fy: Double, squash: Double, tilt: Double

    func toScreen(_ wx: Double, _ wy: Double) -> CGPoint {
        let dx = (wx - cx) * s, dy = (wy - cy) * s
        return CGPoint(x: fx + dx * cosB - dy * sinB, y: fy + (dx * sinB + dy * cosB) * squash)
    }

    func toWorld(_ sx: Double, _ sy: Double) -> (Double, Double) {
        let x = sx - fx, y = (sy - fy) / squash
        return (cx + (x * cosB + y * sinB) / s, cy + (-x * sinB + y * cosB) / s)
    }

    var heightScale: Double { s * tilt * 0.9 }
}

/// [width, height, focus x, focus y, framing width, framing height, panel top].
struct CourierZoomTrackerLay {
    var w: Double, h: Double, fx: Double, fy: Double, aw: Double, ah: Double, panel: Double
}

/// The camera along the film or under a finger: centre, log2 zoom, bearing.
struct CourierZoomTrackerPose: Equatable {
    var x: Double, y: Double, z: Double, b: Double

    static func lerp(_ a: Self, _ b: Self, _ k: Double) -> Self {
        .init(x: a.x + (b.x - a.x) * k, y: a.y + (b.y - a.y) * k, z: a.z + (b.z - a.z) * k, b: a.b + (b.b - a.b) * k)
    }
}

enum CourierZoomTrackerMotion {
    typealias City = CourierZoomTrackerCity
    typealias Lay = CourierZoomTrackerLay
    typealias Pose = CourierZoomTrackerPose

    static let loop: Double = 19

    static let driveA = 0.9, driveB = 12.6
    static let grabIn = 5.25, grab = 5.45, dragA = 5.5, dragB = 6.7, release = 6.8
    static let recenter = 7.5, glideB = 8.3
    static let arriveA = 12.45, arriveB = 12.85
    static let call = 13.1, callOpen = 13.2, callClose = 14.2
    static let msg = 14.45, msgSend = 14.6
    static let backA = 15.45, backB = 18.35
    static let vanOutA = 15.7, vanOutB = 16.2, vanInA = 16.9, vanInB = 17.6

    static let etaMin = 12.0
    static let sMax = 8.5
    static let sMin = 0.07
    private static let sFloor = 0.12
    private static let soft = 40.0
    private static let rampIn = 1.6
    private static let rampOut = 2.2
    static let bearing0 = 36.8 * Double.pi / 180
    static let dragX = -120.0
    static let dragY = 36.0
    static let idle = 2.0
    static let returnDur = 1.5
    static let extrudeX = 0.2

    // MARK: helpers

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeInOutSine(_ x: Double) -> Double { 0.5 - 0.5 * cos(.pi * x) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInCubic(_ x: Double) -> Double { x * x * x }
    static func smooth01(_ x: Double) -> Double { let k = clamp01(x); return k * k * (3 - 2 * k) }
    static func bump(_ t: Double, _ a: Double, _ b: Double) -> Double { let k = sin(.pi * seg(t, a, b)); return k * k }

    /// Cubic bezier (0.65, 0, 0.35, 1), the RN `EASE_IN_OUT`.
    static func easeBezier(_ x: Double) -> Double {
        func bx(_ u: Double) -> Double { 3 * (1 - u) * (1 - u) * u * 0.65 + 3 * (1 - u) * u * u * 0.35 + u * u * u }
        func by(_ u: Double) -> Double { 3 * (1 - u) * u * u + u * u * u }
        let xx = clamp01(x)
        var lo = 0.0, hi = 1.0
        for _ in 0..<30 { let m = (lo + hi) / 2; if bx(m) < xx { lo = m } else { hi = m } }
        return by((lo + hi) / 2)
    }

    static func remainingAt(_ u: Double, _ len: Double) -> Double { (len + soft) * pow(soft / (len + soft), u) - soft }
    static func logParam(_ dLeft: Double, _ len: Double) -> Double { log((len + soft) / (max(dLeft, 0) + soft)) / log((len + soft) / soft) }

    /// A point on the route d metres from the hub: x, y, heading.
    static func routeAt(_ d: Double) -> (x: Double, y: Double, hx: Double, hy: Double) {
        let rx = City.route.x, ry = City.route.y
        let n = rx.count - 1
        let f = min(max(d / City.routeStep, 0), Double(n))
        let i = min(Int(floor(f)), n - 1)
        let k = f - Double(i)
        let a = max(i - 3, 0), b = min(i + 4, n)
        let hx = rx[b] - rx[a], hy = ry[b] - ry[a]
        let hl = hypot(hx, hy) == 0 ? 1 : hypot(hx, hy)
        return (rx[i] + (rx[i + 1] - rx[i]) * k, ry[i] + (ry[i + 1] - ry[i]) * k, hx / hl, hy / hl)
    }

    static func bearingOf(_ u: Double) -> Double { bearing0 * (1 - smooth01((u - 0.5) / 0.36)) }

    static func tiltOf(_ s: Double) -> Double {
        let k = (log2(s) - log2(0.55)) / (log2(3.6) - log2(0.55))
        return 0.8 * smooth01(k)
    }

    /// The camera that frames the van (d metres along the route) and the door together.
    static func framing(_ d: Double, _ lay: Lay) -> Pose {
        let len = City.routeLen
        let p = routeAt(d)
        let n = City.route.x.count - 1
        let hx = City.route.x[n], hy = City.route.y[n]
        let left = len - d
        let b = bearingOf(logParam(left, len))
        let vx = hx - p.x, vy = hy - p.y
        let c = cos(-b), s = sin(-b)
        let sx = vx * c - vy * s, sy = vx * s + vy * c
        let a = lay.ah * 0.8, w = lay.aw * 0.66
        let vl = hypot(sx, sy)
        let grow = vl > 1 ? sqrt(max(left, vl) / vl) : 1
        let q = ((sy / a) * (sy / a) + (sx / w) * (sx / w)) * grow * grow
        let inv = 1 / sMax
        let zc = 1 / pow(q * q + inv * inv * inv * inv, 0.25)
        let z = pow(zc * zc * zc * zc + sFloor * sFloor * sFloor * sFloor, 0.25)
        let near = 1 - clamp01(left / 150)
        return Pose(x: (p.x + hx) / 2, y: (p.y + hy) / 2 - 5 * near, z: log2(z), b: b)
    }

    // MARK: the film

    static func driveU(_ t: Double) -> Double {
        let T = driveB - driveA
        let tau = min(max(t - driveA, 0), T)
        let v = 1 / (T - rampIn / 2 - rampOut / 2)
        if tau < rampIn {
            let x = tau / rampIn
            return v * rampIn * (x * x * x - x * x * x * x / 2)
        }
        if tau < T - rampOut { return v * (rampIn / 2 + tau - rampIn) }
        let y = (tau - (T - rampOut)) / rampOut
        return min(1, v * (rampIn / 2 + (T - rampOut - rampIn) + rampOut * (y - (y * y * y - y * y * y * y / 2))))
    }

    static func filmDrive(_ t: Double) -> Double { City.routeLen - remainingAt(driveU(t), City.routeLen) }

    /// The film's camera and whether it follows the van.
    static func filmCamera(_ t: Double, _ lay: Lay) -> (Pose, Bool) {
        let len = City.routeLen
        var live: Pose
        if t < backA {
            live = framing(filmDrive(t), lay)
        } else {
            let a = framing(len, lay), b = framing(0, lay)
            let k = easeInOutSine(seg(t, backA, backB))
            let lz = lerp(a.z, b.z, k)
            let ia = pow(2, -a.z), ib = pow(2, -b.z)
            let w = (pow(2, -lz) - ia) / (ib - ia)
            live = Pose(x: lerp(a.x, b.x, w), y: lerp(a.y, b.y, w), z: lz, b: lerp(a.b, b.b, k))
        }
        let f = easeOutCubic(seg(t, grab - 0.05, grab + 0.3)) * (1 - easeInOutCubic(seg(t, recenter, glideB)))
        if f > 0 {
            let fr = framing(filmDrive(grab - 0.05), lay)
            let k = easeInOutCubic(seg(t, dragA, dragB))
            let ox = dragX * k, oy = dragY * k
            let z = pow(2, fr.z)
            let cb = cos(fr.b), sb = sin(fr.b)
            let wx = (ox * cb - oy * sb) / z, wy = (ox * sb + oy * cb) / z
            live = Pose(x: lerp(live.x, fr.x - wx, f), y: lerp(live.y, fr.y - wy, f), z: lerp(live.z, fr.z, f), b: lerp(live.b, fr.b, f))
        }
        let drift = bump(t, 11.8, 16.4)
        live.z += 0.1 * drift
        live.b -= 0.09 * drift
        return (live, !(t >= grab && t < recenter))
    }

    static func filmVan(_ t: Double) -> (d: Double, a: Double) {
        if t < 16.5 { return (filmDrive(t), 1 - easeInCubic(seg(t, vanOutA, vanOutB))) }
        return (0, easeOutCubic(seg(t, vanInA, vanInB)))
    }

    static func filmTravelled(_ t: Double) -> (end: Double, a: Double) {
        t < 17.3 ? (filmDrive(t) / City.routeLen, 1 - easeInOutCubic(seg(t, 16.5, 17.3))) : (0, 1)
    }

    static func filmArrived(_ t: Double) -> Double {
        easeInOutCubic(seg(t, arriveA, arriveB)) * (1 - easeInOutCubic(seg(t, vanInA, vanInB)))
    }

    static func filmToast(_ t: Double) -> Double {
        easeOutCubic(seg(t, msgSend, msgSend + 0.3)) * (1 - easeInCubic(seg(t, 16.6, 17.0)))
    }

    static func filmCall(_ t: Double) -> Double {
        easeOutCubic(seg(t, callOpen, callOpen + 0.3)) * (1 - easeInCubic(seg(t, callClose, callClose + 0.25)))
    }

    static func filmDoorPulse(_ t: Double) -> Double {
        let k = seg(t, driveB - 0.05, driveB + 1.0)
        return k > 0 && k < 1 ? k : 0
    }

    /// Ghost finger: kind (0 none, 1 drag, 2 locate, 3 Call, 4 Message), visibility, press, x, y.
    static func filmFinger(_ t: Double, _ lay: Lay, _ buttons: [CGPoint]) -> (kind: Int, vis: Double, press: Double, x: Double, y: Double) {
        if t >= grabIn && t < release + 0.3 {
            let k = easeInOutCubic(seg(t, dragA, dragB))
            let vis = easeOutCubic(seg(t, grabIn, grab)) * (1 - easeOutCubic(seg(t, release, release + 0.3)))
            let press = seg(t, grabIn + 0.08, grab) * (1 - seg(t, dragB + 0.02, release))
            return (1, vis, press, lay.w * 0.7 + dragX * k, lay.fy - 110 + dragY * k)
        }
        let taps = [recenter, call, msg]
        for i in 0..<3 {
            let tap = taps[i]
            if t >= tap - 0.32 && t < tap + 0.42 {
                let vis = easeOutCubic(seg(t, tap - 0.32, tap - 0.12)) * (1 - easeOutCubic(seg(t, tap + 0.14, tap + 0.42)))
                return (2 + i, vis, bump(t, tap - 0.12, tap + 0.14), buttons[i].x, buttons[i].y + 3)
            }
        }
        return (0, 0, 0, 0, 0)
    }

    // MARK: copy

    static func etaText(_ dLeft: Double) -> String {
        let m = max(1, Int(ceil(etaMin * dLeft / City.routeLen - 0.02)))
        return "\(m) min"
    }

    static func distanceText(_ dLeft: Double) -> String {
        let d = max(dLeft, 0)
        if d < 15 { return "Pulling up outside" }
        let dist = d >= 1000 ? String(format: "%.1f km", d / 1000) : "\(max(10, Int((d / 10).rounded(.toNearestOrAwayFromZero)) * 10)) m"
        return "\(dist) away · \(d > 400 ? "On the way" : "Almost there")"
    }

    // MARK: projection

    static func screenCam(_ v: Pose, _ lay: Lay) -> CourierZoomTrackerCam {
        let s = pow(2, v.z)
        let tilt = tiltOf(s)
        return CourierZoomTrackerCam(cx: v.x, cy: v.y, s: s, cosB: cos(-v.b), sinB: sin(-v.b), fx: lay.fx, fy: lay.fy, squash: 1 - 0.28 * tilt, tilt: tilt)
    }
}
