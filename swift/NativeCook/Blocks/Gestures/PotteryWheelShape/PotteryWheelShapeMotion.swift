import Foundation

/*
 Pottery Wheel Shape — the loop's script and the clay's geometry, as pure functions of the loop
 time (every frame at t = loop equals the frame at t = 0). Shared by the view and the shader's
 uniforms.

 The clay is a surface of revolution. Its profile is a height H and eight radii r0…r7 spaced
 evenly from the foot (r0) to the top (r7), blended with a cubic B-spline (the shader uses the
 same formula), plus `open` (0 = a closed, domed lump; 1 = a vessel with an open top).

 Units: points at the 393 × 852 design size (multiplied by `unit` on other screens), seconds,
 revolutions per second.

 autoPlay script (loop = 9 s):
   0.0 – 1.4   rest: a squat lump, a little off centre, turning slowly (0.6 rev/s).
   1.4 – 2.4   centring: both hands press in, the wheel speeds up to 2.2 rev/s, the wobble dies,
               the lump becomes a cone; 2.3 – 2.9 the thumb opens the top.
   2.4 – 4.4   pull up: one finger rides the wall from the foot to the lip, the walls rise.
   4.4 – 5.6   neck in: both hands squeeze the upper third, the belly swells.
   5.6 – 6.2   lip flare: a finger inside the lip turns it out.
   6.2 – 7.2   hold: the finished vase spins.
   7.2 – 8.6   collapse: the walls sag (overshoot down, then back), the top closes, the lump
               re-centres off-axis while the wheel slows to 0.6 rev/s.
   8.6 – 9.0   rest (the same frame as 0).
 */
enum PotteryWheelShapeMotion {
    static let pH = 0
    static let pR = 1 // r0 … r7 at 1 … 8
    static let pOpen = 9
    static let pWob = 10

    /// Wheel speeds (rev/s) at rest and while throwing.
    static let slow = 0.6
    static let fast = 2.2
    /// pt per cm for the readout.
    static let ptPerCm = 13.3
    /// The camera's elevation (16°). The shader uses the same numbers.
    static let sinE = 0.2756
    static let cosE = 0.9613

    static let lump: [Double] = [104, 80, 82, 81, 78, 73, 66, 58, 52, 0, 3.5]
    static let cone: [Double] = [130, 64, 64, 62, 58, 53, 47, 41, 37, 0, 0]
    static let cyl: [Double] = [226, 56, 55, 52, 49, 46, 44, 42, 41, 1, 0]
    static let neck: [Double] = [236, 58, 66, 71, 64, 47, 31, 27, 30, 1, 0]
    static let flare: [Double] = [240, 58, 66, 71, 64, 47, 31, 31, 44, 1, 0]

    static let phases = ["Wedged and ready", "Centring the clay", "Pulling the walls up", "Collaring the neck",
                         "Flaring the lip", "Looking good", "Back to a ball"]

    /// Slip drops: sprayN drops on a sprayPeriod cycle (whole cycles per loop: 9 / 0.6 = 15).
    static let sprayN = 38
    static let sprayPeriod = 0.6

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOut(_ x: Double) -> Double { let u = 1 - x; return 1 - u * u * u }
    static func easeInOut(_ x: Double) -> Double {
        if x < 0.5 { return 4 * x * x * x }
        let v = -2 * x + 2
        return 1 - v * v * v / 2
    }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    static func mixPose(_ a: [Double], _ b: [Double], _ k: Double) -> [Double] {
        var out = a
        for i in 0..<a.count { out[i] = a[i] + (b[i] - a[i]) * k }
        return out
    }

    private static func bspline(_ d: Double) -> Double {
        let x = abs(d)
        if x >= 2 { return 0 }
        if x >= 1 { return (2 - x) * (2 - x) * (2 - x) / 6 }
        return (4 - 6 * x * x + 3 * x * x * x) / 6
    }

    /// The clay's radius (pt, before `unit`) at height y above the wheel head.
    static func radiusAt(_ p: [Double], _ y: Double) -> Double {
        let H = p[pH]
        let u = clamp01(y / H) * 7
        var sum = 0.0, wsum = 0.0
        for i in 0..<8 {
            let w = bspline(u - Double(i))
            sum += w * p[pR + i]
            wsum += w
        }
        var r = sum / max(wsum, 1e-3)
        let cap = sqrt(clamp01((H - y) / (0.42 * H)))
        r *= cap + (1 - cap) * p[pOpen]
        return r + 5 * exp(-max(y, 0) / 5)
    }

    /// The widest diameter of the profile (pt).
    static func widest(_ p: [Double]) -> Double {
        var m = 0.0
        for k in 0...16 { m = max(m, radiusAt(p, p[pH] * Double(k) / 16)) }
        return 2 * m
    }

    /// The clay's pose at loop time t.
    static func poseAt(_ t: Double) -> [Double] {
        if t < 1.4 { return lump }
        if t < 2.4 { return mixPose(lump, cone, easeInOut(seg(t, 1.4, 2.4))) }
        if t < 4.4 {
            var p = mixPose(cone, cyl, easeInOut(seg(t, 2.4, 4.4)))
            p[pOpen] = easeInOut(seg(t, 2.3, 2.9))
            return p
        }
        if t < 5.6 { return mixPose(cyl, neck, easeInOut(seg(t, 4.4, 5.6))) }
        if t < 7.2 { return mixPose(neck, flare, easeInOut(seg(t, 5.6, 6.2))) }
        if t < 8.6 {
            let u = seg(t, 7.2, 8.6)
            // The walls sag past the lump and come back: an overshoot that is zero at both ends.
            let k = easeInOut(u) + 0.22 * sin(Double.pi * 2 * u) * (1 - u) * (u < 0.5 ? 1 : 0.6)
            var p = mixPose(flare, lump, k)
            p[pOpen] = 1 - easeInOut(seg(t, 7.3, 8.1))
            p[pWob] = lump[pWob] * easeOut(seg(t, 7.8, 8.6))
            return p
        }
        return lump
    }

    /// Integral of the throwing ramp (0 at rest speed, 1 at full speed), in seconds.
    private static func rampArea(_ t: Double) -> Double {
        var a = 0.0
        let up = seg(t, 1.4, 2.4)
        a += 1.0 * (up * up * up - up * up * up * up / 2)
        a += max(0, min(t, 7.2) - 2.4)
        let dn = seg(t, 7.2, 8.6)
        a += 1.4 * (dn - (dn * dn * dn - dn * dn * dn * dn / 2))
        return a
    }

    /// The wheel's speed (rev/s) at loop time t.
    static func speedAt(_ t: Double) -> Double {
        let up = seg(t, 1.4, 2.4)
        let dn = seg(t, 7.2, 8.6)
        let s = up * up * (3 - 2 * up) - dn * dn * (3 - 2 * dn)
        return slow + (fast - slow) * s
    }

    /// The wheel's angle (radians) at loop time t: exactly 15 whole turns per loop.
    static func spinAt(_ t: Double) -> Double {
        let revs = slow * t + (fast - slow) * rampArea(t)
        return revs * Double.pi * 2
    }

    /// Where the hands touch the clay at loop time t, as [height, side, alpha, press] for each of
    /// two fingers (press = how far the pad sits inside the wall's outline, pt).
    static func fingersAt(_ t: Double, _ p: [Double]) -> [Double] {
        let H = p[pH]
        var out: [Double] = [0, 1, 0, 0, 0, -1, 0, 0]
        if t >= 1.3 && t < 2.55 {
            let a = seg(t, 1.3, 1.5) * (1 - seg(t, 2.35, 2.55))
            let press = 4 + 3 * sin(seg(t, 1.4, 2.4) * Double.pi)
            out[0] = H * 0.42; out[2] = a; out[3] = press
            out[4] = H * 0.42; out[6] = a; out[7] = press
        } else if t >= 2.45 && t < 4.6 {
            out[0] = H * (0.08 + 0.86 * easeInOut(seg(t, 2.5, 4.4)))
            out[2] = seg(t, 2.45, 2.65) * (1 - seg(t, 4.4, 4.6))
            out[3] = 5
        } else if t >= 4.4 && t < 5.75 {
            let a = seg(t, 4.4, 4.6) * (1 - seg(t, 5.5, 5.75))
            out[0] = H * 0.74; out[2] = a; out[3] = 6
            out[4] = H * 0.74; out[6] = a; out[7] = 6
        } else if t >= 5.5 && t < 6.45 {
            out[0] = H - 10
            out[2] = seg(t, 5.5, 5.7) * (1 - seg(t, 6.2, 6.45))
            out[3] = 30
        }
        return out
    }

    /// How hard slip flies off the wall (0 … 1) at loop time t, and from which height (share of H).
    static func sprayAt(_ t: Double) -> (Double, Double) {
        if t >= 1.5 && t < 2.4 { return (0.35, 0.42) }
        if t >= 2.6 && t < 4.4 { return (1, 0.08 + 0.86 * easeInOut(seg(t, 2.5, 4.4))) }
        if t >= 4.6 && t < 5.5 { return (0.55, 0.74) }
        if t >= 5.7 && t < 6.2 { return (0.5, 0.97) }
        return (0, 0.5)
    }

    static func phaseAt(_ t: Double) -> Int {
        if t < 1.4 { return 0 }
        if t < 2.4 { return 1 }
        if t < 4.4 { return 2 }
        if t < 5.6 { return 3 }
        if t < 6.2 { return 4 }
        if t < 7.2 { return 5 }
        if t < 8.6 { return 6 }
        return 0
    }

    /// The wobble's x offset (pt) for a pose at a wheel angle.
    static func wobbleX(_ p: [Double], _ spin: Double, _ unit: Double) -> Double {
        p[pWob] * unit * sin(spin + 0.6)
    }

    // Limits of a shape made by hand (pt): past them the clay resists (rubber band).
    static let rMin = 16.0, rMax = 104.0, hMin = 70.0, hMax = 268.0

    private static func rubber(_ v: Double, _ lo: Double, _ hi: Double) -> Double {
        if v > hi { return hi + (v - hi) / (1 + (v - hi) / 30) }
        if v < lo { return lo - (lo - v) / (1 + (lo - v) / 30) }
        return v
    }

    /// A finger working the wall: a drag of (dx, dy) pt at screen (x, y). Returns a new pose.
    static func shapeByFinger(_ g: PotteryWheelShapeGeo, _ p: [Double], x: Double, y: Double, dx: Double, dy: Double) -> [Double] {
        var out = p
        let u = g.unit
        let H = p[pH]
        let h = min(H, max(0, (g.baseY - y) / (u * cosE)))
        let side: Double = x >= g.cx ? 1 : -1
        let reach = radiusAt(p, h) * u
        let nearWall = abs(abs(x - g.cx) - reach) < 70 * u
        if !nearWall { return out }
        let inward = -side * dx / u
        let k = h / H * 7
        var squeezed = 0.0
        for i in 0..<8 {
            let w = exp(-((Double(i) - k) * (Double(i) - k)) / (2 * 1.25 * 1.25))
            let r = out[pR + i] - inward * w * 0.85
            out[pR + i] = rubber(r, rMin, rMax)
            squeezed += inward * w
        }
        // Squeezing pushes clay up, widening lets it settle; dragging up pulls it taller.
        let up = -dy / u
        let Hn = rubber(H + up * 0.9 + squeezed * 0.35, hMin, hMax)
        let keep = sqrt(H / Hn)
        for i in 0..<8 { out[pR + i] = rubber(out[pR + i] * keep, rMin, rMax) }
        out[pH] = Hn
        out[pOpen] = min(1, max(out[pOpen], (Hn - 120) / 40))
        out[pWob] = out[pWob] * 0.96
        return out
    }

    /// Brings a hand-made pose back inside the limits (after the finger lifts).
    static func settlePose(_ p: [Double], _ k: Double) -> [Double] {
        var out = p
        out[pH] += (min(hMax, max(hMin, p[pH])) - p[pH]) * k
        for i in 0..<8 {
            let r = p[pR + i]
            out[pR + i] += (min(rMax, max(rMin, r)) - r) * k
        }
        return out
    }
}

/// The screen's geometry: the wheel head's centre and the scale of the hero.
struct PotteryWheelShapeGeo {
    let w: Double
    let h: Double
    let unit: Double
    let cx: Double
    let baseY: Double

    init(w: Double, h: Double) {
        self.w = w
        self.h = h
        unit = max(0.74, min(1.15, min(w / 393, h / 852)))
        cx = w / 2
        baseY = h * 0.69
    }

    /// A finger (height, side, alpha, press) from fingersAt → screen (x, y, alpha, radius).
    func fingerOnScreen(_ p: [Double], _ f: [Double], _ o: Int, _ wob: Double) -> [Double] {
        let r = PotteryWheelShapeMotion.radiusAt(p, f[o]) * unit
        let pad = 15 * unit
        return [cx + wob + f[o + 1] * (r + pad - f[o + 3] * unit), baseY - f[o] * unit * PotteryWheelShapeMotion.cosE, f[o + 2], pad]
    }
}
