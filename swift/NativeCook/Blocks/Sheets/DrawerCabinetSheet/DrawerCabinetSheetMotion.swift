import Foundation

/*
 Drawer Cabinet Sheet — the drawer's travel and the things rattling in it.

 The drawer's opening (pt pulled out of the cabinet) is a pure function of the loop time. Every
 item rides in its compartment on a spring along the drawer's travel (and a little turn): when the
 drawer accelerates the item lags, hits the divider's slack, bounces and settles. Under autoPlay
 the items' response to the scripted travel is integrated once at layout into a table (240 samples
 a second over one loop, starting and ending at rest), so each frame is a lookup — deterministic,
 and the frame at t = loop is the frame at 0. Under touch the same spring is stepped live with the
 real drawer's acceleration.

 loop = 8 s: rest 0–1.2 (closed) · pulled to half 1.2–2.2 · to full 2.8–3.6 · the blue pen picked
 4.0–5.0 (lifts 0.25 s, flies 0.6 s, lands) · soft close 5.6–7.0 (fast, then the damper eases it
 home over the last few centimetres) · the desk clears 7.0–7.5 · rest.
*/

struct DrawerCabinetSheetSpring {
    /// Room to move along the drawer's travel either way (pt) and how hard it bounces off a divider.
    let slack: Double
    let bounce: Double
    /// Spring of the felt / friction holding it near its place (ω rad/s, ζ), and how much it turns.
    let w: Double
    let z: Double
    let turn: Double
}

enum DrawerCabinetSheetMotion {
    static let pickAt = 4.0
    static let liftFor = 0.25
    static let flyFor = 0.6
    static let closeAt = 5.6
    static let closeFor = 1.4
    static let clearAt = 7.0
    /// Samples per second of the autoPlay table.
    static let rate = 240

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : (v > 1 ? 1 : v) }

    static func easeInOutCubic(_ v: Double) -> Double {
        let x = clamp01(v)
        if x < 0.5 { return 4 * x * x * x }
        let q = -2 * x + 2
        return 1 - q * q * q / 2
    }

    static func easeOutCubic(_ v: Double) -> Double {
        let q = 1 - clamp01(v)
        return 1 - q * q * q
    }

    /// A finger's pull settling into a detent: a spring response (ω rad/s, ζ) from 0 to 1.
    static func settle(_ tau: Double, _ w: Double, _ z: Double) -> Double {
        if tau <= 0 { return 0 }
        let wd = w * (1 - z * z).squareRoot()
        return 1 - exp(-z * w * tau) * (cos(wd * tau) + z * w / wd * sin(wd * tau))
    }

    /// Soft close: pushed at speed, then the damper takes the last stretch slowly (ease-out quint).
    static func softClose(_ u: Double) -> Double {
        let q = 1 - clamp01(u)
        return 1 - q * q * q * q * q
    }

    /// The drawer's opening in the loop, for detents `half` and `full`.
    static func autoOpen(_ t: Double, _ half: Double, _ full: Double) -> Double {
        if t < 1.2 { return 0 }
        if t < 2.8 { return half * settle(t - 1.2, 8, 0.72) }
        if t < closeAt { return half + (full - half) * settle(t - 2.8, 9, 0.68) }
        return full * (1 - softClose((t - closeAt) / closeFor))
    }

    /// One step of an item's spring along the travel: `a` is the drawer's acceleration (pt/s²,
    /// + = moving out faster, toward the top of the screen); the item lags behind it (+y in the
    /// drawer = toward the back). state = (y, vy).
    static func stepItem(_ s: DrawerCabinetSheetSpring, _ state: inout (Double, Double), _ a: Double, _ dt: Double) {
        let acc = -s.w * s.w * state.0 - 2 * s.z * s.w * state.1 + a * 0.9
        state.1 += acc * dt
        state.0 += state.1 * dt
        if state.0 > s.slack {
            state.0 = s.slack
            if state.1 > 0 { state.1 = -state.1 * s.bounce }
        } else if state.0 < -s.slack {
            state.0 = -s.slack
            if state.1 < 0 { state.1 = -state.1 * s.bounce }
        }
    }

    /// The autoPlay table: per item, its lag at `rate` samples a second over one loop.
    static func buildTable(_ springs: [DrawerCabinetSheetSpring], _ half: Double, _ full: Double) -> [[Double]] {
        let loop = DrawerCabinetSheet.loop
        let n = Int((loop * Double(rate)).rounded())
        let dt = 1 / Double(rate)
        var ys = springs.map { _ in [Double](repeating: 0, count: n + 1) }
        var states = springs.map { _ in (0.0, 0.0) }
        // Drawer acceleration by central differences of the scripted opening (+ = outward).
        func open(_ t: Double) -> Double { autoOpen(max(0, min(loop, t)), half, full) }
        for i in 0...n {
            let t = Double(i) * dt
            let a = (open(t + dt) - 2 * open(t) + open(t - dt)) / (dt * dt)
            for k in springs.indices {
                stepItem(springs[k], &states[k], a, dt)
                ys[k][i] = states[k].0
            }
        }
        // The table must close the loop exactly: ease any last residue to 0 over the final second.
        for k in springs.indices {
            for i in (n - rate)...n {
                ys[k][i] *= 1 - easeInOutCubic(Double(i - (n - rate)) / Double(rate))
            }
        }
        return ys
    }

    /// An item's lag at loop time t from the table (linear between samples).
    static func tableAt(_ ys: [Double], _ t: Double) -> Double {
        let f = clamp01(t / DrawerCabinetSheet.loop) * Double(ys.count - 1)
        let i = Int(floor(f))
        let j = min(ys.count - 1, i + 1)
        return ys[i] + (ys[j] - ys[i]) * (f - Double(i))
    }

    struct PickPose {
        var x = 0.0
        var y = 0.0
        var rot = 0.0
        var lift = 0.0
        var scale = 1.0
    }

    /// A pick: from its place in the drawer the item lifts (0.25 s), flies in an arc to its place
    /// on the desk (0.6 s, ease-in-out) and lands with a small bounce. `u` = seconds since the pick.
    static func pickPose(_ fx: Double, _ fy: Double, _ frot: Double, _ tx: Double, _ ty: Double, _ trot: Double, _ u: Double) -> PickPose {
        if u <= 0 { return PickPose(x: fx, y: fy, rot: frot, lift: 0, scale: 1) }
        if u < liftFor {
            let k = easeOutCubic(u / liftFor)
            return PickPose(x: fx, y: fy - 6 * k, rot: frot * (1 - 0.4 * k), lift: k, scale: 1 + 0.1 * k)
        }
        let v = (u - liftFor) / flyFor
        if v < 1 {
            let s = easeInOutCubic(v)
            let arc = 60 * sin(Double.pi * s)
            let lift = 1 - 0.5 * max(0, s - 0.6) / 0.4
            return PickPose(
                x: fx + (tx - fx) * s,
                y: fy - 6 + (ty - fy + 6) * s - arc,
                rot: frot * 0.6 * (1 - s) + trot * s + 0.12 * sin(Double.pi * s),
                lift: lift,
                scale: 1 + 0.1 * lift
            )
        }
        let b = u - liftFor - flyFor
        let bounce = b < 0.5 ? exp(-b * 10) * sin(b * 34) : 0
        let settleLift = max(0, 0.5 * (1 - b / 0.12))
        return PickPose(x: tx, y: ty, rot: trot, lift: settleLift, scale: 1 + 0.05 * settleLift - 0.03 * bounce)
    }

    /// cubic-bezier(x1, y1, x2, y2) at progress x (as CSS / Reanimated's Easing.bezier).
    static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
        let p = clamp01(x)
        if p == 0 || p == 1 { return p }
        func bx(_ t: Double) -> Double { let u = 1 - t; return 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t }
        func by(_ t: Double) -> Double { let u = 1 - t; return 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t }
        var lo = 0.0
        var hi = 1.0
        var t = p
        for _ in 0..<24 {
            let v = bx(t)
            if abs(v - p) < 1e-6 { break }
            if v < p { lo = t } else { hi = t }
            t = (lo + hi) / 2
        }
        return by(t)
    }

    /// The soft-close damper's curve.
    static func soft(_ x: Double) -> Double { bezier(0.12, 0.8, 0.2, 1, x) }
}
