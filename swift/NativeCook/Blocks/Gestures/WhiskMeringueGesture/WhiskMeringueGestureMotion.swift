import SwiftUI

/*
 Whisk Meringue Gesture — the loop's script, the bowl's geometry and the whisk's wires, as pure
 functions of the loop time (frame at t = loop equals frame at 0).

 A pose is [foam, peaks, swirl, agitation, headX, headY, lift, liftPeak, liftX, liftY]:
   foam      0 = clear egg whites … 1 = stiff glossy meringue
   peaks     how tall the whisked ridges stand
   swirl     how far the bowl's contents have turned (radians; only the foam shows it)
   agitation ripples in the liquid around the whisk
   head      the whisk's head in bowl units (bowl centre = 0, rim radius = 1)
   lift      0 = in the bowl … 1 = lifted towards the camera
   liftPeak  the stiff peak the lifted whisk pulls up, at (liftX, liftY)

 autoPlay script (loop = 9 s):
   0.0 – 1.2   rest: clear whites, the whisk resting against the right of the bowl.
   1.2 – 5.6   circles: the whisk speeds up to 2.3 rev/s, settles to a steady 1.7 rev/s, then slows
               to a stop (5.4 – 6.2); the whites go foamy, soft, then firm.
   5.6 – 6.4   peaks: the foam turns glossy and stiff, the swirl's ridges stand up.
   6.4 – 7.2   the whisk lifts (ease-out) and pulls a stiff peak up with it.
   7.2 – 8.4   reset: the whisk settles back while the bubbles pop back into clear whites.
   8.4 – 9.0   rest.
 */
enum WhiskMeringueGestureMotion {
    static let fFoam = 0, fPeak = 1, fSwirl = 2, fAgit = 3, fHX = 4, fHY = 5, fLift = 6, fLPeak = 7, fLX = 8, fLY = 9

    /// The camera's tilt squashes the round bowl vertically by this much.
    static let squash = 0.85
    /// The radius (bowl units) the whisk circles on, and where it rests.
    private static let circleR = 0.44
    private static let restHead = (0.5, 0.14)
    /// Where the hand holds the handle (bowl units), off the lower right of the bowl.
    static let grip = (1.32, 1.12)

    static let stages = ["Clear whites", "Foamy", "Soft peaks", "Firm peaks", "Stiff peaks"]

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOut(_ x: Double) -> Double { let u = 1 - x; return 1 - u * u * u }
    static func easeInOut(_ x: Double) -> Double {
        if x < 0.5 { return 4 * x * x * x }
        let v = -2 * x + 2
        return 1 - v * v * v / 2
    }

    static func mixPose(_ a: [Double], _ b: [Double], _ k: Double) -> [Double] {
        var out = a
        for i in 0..<a.count { out[i] = a[i] + (b[i] - a[i]) * k }
        return out
    }

    static func restPose() -> [Double] { [0, 0, 0, 0, restHead.0, restHead.1, 0, 0, 0, 0] }

    /// The meringue held still (reduced motion): stiff, glossy, a peak standing.
    static func stiffPose() -> [Double] { [1, 1, 2.2, 0, restHead.0, restHead.1, 0, 1, -0.12, -0.05] }

    private static func steadyTurns(_ x: Double) -> Double {
        0.92 + 1.7 * x + 0.6 * 0.5 * (1 - exp(-x / 0.5))
    }

    /// Whisk turns (revolutions) since it started circling at 1.2 s.
    static func turnsAt(_ t: Double) -> Double {
        if t < 1.2 { return 0 }
        if t < 2.0 {
            let u = (t - 1.2) / 0.8
            return 0.92 * u * u
        }
        if t < 5.4 { return steadyTurns(t - 2.0) }
        let s0 = 1.7 + 0.6 * exp(-3.4 / 0.5)
        let base = steadyTurns(3.4)
        let x = min(t, 6.2) - 5.4
        return base + s0 * x - s0 * x * x / (2 * 0.8)
    }

    /// The whisk's circling speed (rev/s) at t, for the ripples.
    static func whiskSpeedAt(_ t: Double) -> Double {
        if t < 1.2 || t >= 6.2 { return 0 }
        if t < 2.0 { return 2 * 0.92 * (t - 1.2) / 0.64 }
        if t < 5.4 { return 1.7 + 0.6 * exp(-(t - 2.0) / 0.5) }
        return (1.7 + 0.6 * exp(-3.4 / 0.5)) * (1 - (t - 5.4) / 0.8)
    }

    static func poseAt(_ t: Double) -> [Double] {
        var p = restPose()
        let ang = turnsAt(t) * .pi * 2
        let reset = easeInOut(seg(t, 7.3, 8.4))
        let u = seg(t, 1.4, 5.6)
        let foam = 0.86 * u * (2 - u) + 0.14 * easeInOut(seg(t, 5.6, 6.4))
        p[fFoam] = foam * (1 - reset)
        p[fPeak] = (0.35 * easeInOut(seg(t, 3.4, 5.6)) + 0.65 * easeInOut(seg(t, 5.6, 6.4))) * (1 - reset)
        p[fSwirl] = t < 8.4 ? 0.62 * ang : 0
        p[fAgit] = min(1, whiskSpeedAt(t) / 2.3)
        let cx = circleR * cos(ang)
        let cy = circleR * sin(ang)
        let enter = easeInOut(seg(t, 1.2, 1.55))
        var hx = restHead.0 + (cx - restHead.0) * enter
        var hy = restHead.1 + (cy - restHead.1) * enter
        let endAng = turnsAt(6.2) * .pi * 2
        let ex = circleR * cos(endAng)
        let ey = circleR * sin(endAng)
        let lift = easeOut(seg(t, 6.4, 7.2))
        let back = easeInOut(seg(t, 7.2, 8.4))
        if t >= 6.2 {
            hx = ex + (restHead.0 - ex) * back
            hy = ey - 0.08 * lift + (restHead.1 - ey + 0.08) * back
        }
        p[fHX] = hx
        p[fHY] = hy
        p[fLift] = lift * (1 - back)
        p[fLPeak] = easeOut(seg(t, 6.45, 7.05)) * (1 - reset)
        p[fLX] = ex
        p[fLY] = ey
        return p
    }

    static func stageOf(_ foam: Double) -> Int {
        if foam < 0.06 { return 0 }
        if foam < 0.36 { return 1 }
        if foam < 0.66 { return 2 }
        if foam < 0.92 { return 3 }
        return 4
    }

    /// The whisk seen from above: six wire loops that share the tip and gather into the ferrule,
    /// then the handle up to the hand. `lift` brings it towards the camera (bigger).
    static func whiskPaths(_ g: WhiskMeringueGestureGeo, _ hx: Double, _ hy: Double, _ lift: Double, _ dx: Double, _ dy: Double) -> (wires: Path, handle: Path) {
        let (x0, y0) = g.toScreen(hx, hy)
        let (gx, gy) = g.toScreen(grip.0, grip.1)
        var ax = gx - x0
        var ay = gy - y0
        let len = max(1, hypot(ax, ay))
        ax /= len
        ay /= len
        let s = g.unit * (1 + 0.2 * lift)
        let L = 124 * s
        let W = 40 * s
        let tx = x0 - ax * L * 0.32 + dx
        let ty = y0 - ay * L * 0.32 + dy
        let nx = -ay
        let ny = ax
        var wires = Path()
        for i in 0..<6 {
            let phi = Double(i) / 6 * .pi + 0.26
            let lateral = W * cos(phi)
            for k in 0...24 {
                let sArc = Double(k) / 24 * .pi * 2
                // teardrop loop: wide near the tip, gathered at the ferrule
                let along = L * (1 - cos(sArc / 2)) / 2
                let swell = sin(sArc / 2) * pow(max(0, 1 - along / L), 0.35)
                let side: Double = sin(sArc) >= 0 ? 1 : -1
                let off = lateral * swell * side
                let pt = CGPoint(x: tx + ax * along + nx * off, y: ty + ay * along + ny * off)
                if k == 0 { wires.move(to: pt) } else { wires.addLine(to: pt) }
            }
        }
        let fx = tx + ax * L
        let fy = ty + ay * L
        var handle = Path()
        handle.move(to: CGPoint(x: fx, y: fy))
        handle.addLine(to: CGPoint(x: fx + ax * 190 * s, y: fy + ay * 190 * s))
        return (wires, handle)
    }
}

/// The screen's geometry.
struct WhiskMeringueGestureGeo {
    let w: Double
    let h: Double
    let unit: Double
    let cx: Double
    let cy: Double
    let R: Double

    init(w: Double, h: Double, top: Double, bottom: Double) {
        self.w = w
        self.h = h
        unit = max(0.74, min(1.15, min(w / 393, h / 852)))
        R = min(w * 0.43, 172 * unit)
        // the bowl sits between the header and the progress card, a little above their middle
        cy = top + (bottom - top) * 0.47
        cx = w / 2
    }

    /// A point in bowl units → screen.
    func toScreen(_ x: Double, _ y: Double) -> (Double, Double) {
        (cx + x * R, cy + y * R * WhiskMeringueGestureMotion.squash)
    }
}
