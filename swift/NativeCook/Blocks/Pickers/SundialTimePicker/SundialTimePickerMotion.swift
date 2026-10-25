import Foundation

/*
 Sundial Time Picker — the sun, the dial's geometry and the loop's script, as pure functions of
 the loop time (frame at t = loop equals frame at 0).

 Time τ is in hours (7 … 17). The sun follows an equinox day at latitude 40° N: hour angle
 h = (τ − 12) · 15°, direction (east, north, up) = (−sin h, −sin φ cos h, cos φ cos h). The dial
 is a horizontal sundial: north is up on screen, the gnomon's style rises from the centre O along
 the noon line at the latitude's angle, so its shadow edge falls on the hour line
 atan(sin φ · tan h).

 autoPlay script (loop = 9 s):
   0.0 – 1.4   rest at 10:00.
   1.2 – 1.4   a finger lands on the sun.
   1.4 – 4.4   drag to 15:30 (ease-in-out); the readout rolls through every quarter.
   4.4         snap: 15:30 exactly with a small overshoot that settles by 4.7.
   4.4 – 5.6   hold.
   5.6 – 7.6   back to 10:00 (ease-in-out), the finger lifts at 7.6.
   7.6 – 9.0   rest.
 */
struct SundialTimePickerGeo {
    let w: Double
    let h: Double
    let unit: Double
    let cx: Double
    let cy: Double
    let R: Double
    let arcX: Double
    let arcY: Double
    let gnomon: Double

    init(w: Double, h: Double, top: Double) {
        self.w = w
        self.h = h
        unit = max(0.74, min(1.15, min(w / 393, h / 852)))
        R = min(150 * unit, w * 0.38)
        cy = top + 380 * unit
        cx = w / 2
        arcX = min(w / 2 - 22, R + 30 * unit)
        arcY = R + 50 * unit
        gnomon = R * 0.45
    }
}

enum SundialTimePickerMotion {
    typealias G = SundialTimePickerGeo

    static let tMin = 7.0
    static let tMax = 17.0
    static let restTime = 10.0
    private static let lat = 40 * Double.pi / 180
    static let sinLat = sin(lat)
    static let cosLat = cos(lat)

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOut(_ x: Double) -> Double {
        if x < 0.5 { return 4 * x * x * x }
        let v = -2 * x + 2
        return 1 - v * v * v / 2
    }

    /// The chosen time (hours) at loop time t.
    static func timeAt(_ t: Double) -> Double {
        if t < 1.4 { return restTime }
        if t < 4.4 { return restTime + (15.5 - restTime) * easeInOut(seg(t, 1.4, 4.4)) }
        if t < 5.6 {
            // the snap: a 6-minute overshoot that dies away
            let u = seg(t, 4.4, 4.7)
            return 15.5 + 0.1 * sin(.pi * u) * (1 - u)
        }
        if t < 7.6 { return 15.5 + (restTime - 15.5) * easeInOut(seg(t, 5.6, 7.6)) }
        return restTime
    }

    /// The finger on the sun at loop time t (alpha).
    static func fingerAt(_ t: Double) -> Double {
        if t >= 1.2 && t < 4.65 { return seg(t, 1.2, 1.4) * (1 - seg(t, 4.45, 4.65)) }
        if t >= 5.4 && t < 7.8 { return seg(t, 5.4, 5.6) * (1 - seg(t, 7.6, 7.8)) }
        return 0
    }

    /// The sun's direction (east, north, up) at time τ.
    static func sunDir(_ tau: Double) -> (Double, Double, Double) {
        let h = (tau - 12) * 15 * .pi / 180
        return (-sin(h), -sinLat * cos(h), cosLat * cos(h))
    }

    /// The sun handle on the sky arc (screen point) at time τ.
    static func sunOnArc(_ g: G, _ tau: Double) -> (Double, Double) {
        let b = Double.pi * (1 - (tau - 6) / 12)
        return (g.cx + g.arcX * cos(b), g.cy - g.arcY * sin(b))
    }

    /// The time a finger at (x, y) asks for: the sun's place on the arc above the dial, or the
    /// direction of the shadow on the dial itself.
    static func timeFromFinger(_ g: G, _ x: Double, _ y: Double, onDial: Bool) -> Double {
        if onDial {
            let a = atan2(x - g.cx, g.cy - y)
            let clamped = max(-1.45, min(1.45, a))
            return 12 + atan(tan(clamped) / sinLat) * 12 / .pi
        }
        let b = atan2((g.cy - y) / g.arcY, (x - g.cx) / g.arcX)
        let bb = b < -Double.pi / 2 ? Double.pi : max(0, b)
        return 6 + 12 * (1 - bb / .pi)
    }

    /// Keeps a dragged time inside 7:00 … 17:00 with a rubber band.
    static func rubberTime(_ tau: Double) -> Double {
        if tau > tMax { return tMax + (tau - tMax) / (1 + (tau - tMax) / 0.4) }
        if tau < tMin { return tMin - (tMin - tau) / (1 + (tMin - tau) / 0.4) }
        return tau
    }

    /// The quarter hour a time shows as (and snaps to).
    static func quarterOf(_ tau: Double) -> Double {
        min(tMax, max(tMin, (tau * 4).rounded() / 4))
    }
}
