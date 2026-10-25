import Foundation

/*
 Slide Projector Gallery — the timeline of one slide change and of the autoPlay loop. Pure
 functions of time; the finger's changes use the same `change`, timed from the swipe.

 One change (u = 0 … 1 over `change` s, or `fast` s for the quick ones):
   0.00 – 0.18  the lamp dims to dark (ease-in-out) while the lamp flickers.
   0.12 – 0.42  the tray turns one slot (ease-out-back, 6 % overshoot: the clunk).
   0.30         the next slide is in the gate.
   0.33 – 0.62  it drops in: the picture slides up from 34 pt below its place (ease-out-back),
                the lamp comes back (ease-out).
   0.33 – 0.80  focus pull, blurry to sharp (ease-out), then 0.72 – 0.95 a small breath past
                focus and back (the operator's hand); the picture is 3 % larger while soft.

 autoPlay (loop = 9 s): rest on slide 1 0 – 1.4; 1 → 2 at 1.4 – 2.6; hold; 2 → 3 at 4.2 – 5.4;
 hold; back to the first in two quick changes, 3 → 2 at 6.6 – 7.5 and 2 → 1 at 7.5 – 8.4; rest.
 Dust motes drift up the beam once per loop.
 */
enum SlideProjectorGalleryMotion {
    static let loop: Double = 9
    static let change: Double = 1.2
    static let fast: Double = 0.9
    static let drop: Double = 34
    static let traySlots = 24

    static let auto: [(start: Double, length: Double, from: Int, to: Int)] = [
        (1.4, change, 0, 1),
        (4.2, change, 1, 2),
        (6.6, fast, 2, 1),
        (7.5, fast, 1, 0),
    ]

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let u = clamp01((x - a) / (b - a))
        return u * u * (3 - 2 * u)
    }
    static func easeOutCubic(_ u: Double) -> Double { let v = 1 - u; return 1 - v * v * v }
    static func easeInOutCubic(_ u: Double) -> Double {
        if u < 0.5 { return 4 * u * u * u }
        let v = -2 * u + 2
        return 1 - v * v * v / 2
    }
    static func easeOutBack(_ u: Double, _ c1: Double) -> Double {
        let c3 = c1 + 1, v = u - 1
        return 1 + c3 * v * v * v + c1 * v * v
    }
    /// Reanimated's default withTiming curve.
    static func easeInOutQuad(_ u: Double) -> Double { u < 0.5 ? 2 * u * u : 1 - (-2 * u + 2) * (-2 * u + 2) / 2 }

    struct Pose {
        /// Slide in the gate (0…4).
        var slide: Int
        /// Tray position in slots (continuous).
        var tray: Double
        var bright: Double
        var drop: Double
        var focus: Double
        var flicker: Double
        var scale: Double
    }

    /// One change from slide `from` to `to` at progress u (0…1).
    static func change(_ u: Double, _ from: Int, _ to: Int) -> Pose {
        let dim = easeInOutCubic(seg(u, 0, 0.18))
        let back = easeOutCubic(seg(u, 0.33, 0.62))
        let bright = 1 - dim + dim * back
        let tray = Double(from) + Double(to - from) * easeOutBack(seg(u, 0.12, 0.42), 1.3)
        let dropped = u < 0.33 ? drop : drop * (1 - easeOutBack(seg(u, 0.33, 0.62), 1.1))
        let pull = 1 - easeOutCubic(seg(u, 0.33, 0.8))
        let breath = 0.22 * sin(.pi * seg(u, 0.72, 0.95))
        let focus = u < 0.33 ? 0.6 * easeInOutCubic(seg(u, 0.02, 0.2)) : pull + breath
        // A lamp that stutters while the slide is pulled and pushed (a fixed pattern, not random).
        let w = seg(u, 0.04, 0.5)
        let flick = w > 0 && w < 1 ? 0.18 * max(0, sin(37 * w) * sin(23 * w + 1.3)) : 0
        return Pose(slide: u < 0.3 ? from : to, tray: tray, bright: bright, drop: dropped, focus: focus,
                    flicker: 1 - flick, scale: 1 + 0.03 * min(1, focus))
    }

    static func rest(_ slide: Int) -> Pose {
        Pose(slide: slide, tray: Double(slide), bright: 1, drop: 0, focus: 0, flicker: 1, scale: 1)
    }

    static func autoPose(_ t: Double) -> Pose {
        for a in auto where t >= a.start && t < a.start + a.length {
            return change((t - a.start) / a.length, a.from, a.to)
        }
        var slide = 0
        for a in auto where t >= a.start + a.length { slide = a.to }
        return rest(slide)
    }

    private static func fractHash(_ x: Double) -> Double {
        let v = sin(x * 12.9898) * 43758.5453
        return v - floor(v)
    }

    /// Dust mote i at loop time t: (x, y, radius, brightness) given the beam (lens, slide bottom).
    static func mote(_ i: Int, _ t: Double, lensX: Double, lensY: Double, topY: Double, halfTop: Double) -> (Double, Double, Double, Double) {
        let fi = Double(i)
        let h1 = fractHash(fi * 1.37 + 0.11)
        let h2 = fractHash(fi * 2.91 + 0.53)
        let h3 = fractHash(fi * 4.17 + 0.29)
        let k = 1 + floor(h3 * 2)
        let s = (h1 + k * t / loop).truncatingRemainder(dividingBy: 1)
        let sway = 0.1 * sin(2 * .pi * (2 * t / loop + h2))
        let lateral = (h2 * 2 - 1) * 0.92 + sway
        let y = lensY - s * (lensY - topY)
        let bw = 10 + (halfTop - 10) * s
        let x = lensX + lateral * bw
        let fade = smoothstep(0, 0.12, s) * (1 - smoothstep(0.85, 1, s))
        let tw = 0.6 + 0.4 * sin(2 * .pi * (3 * t / loop + h1 * 7))
        let r = 0.6 + h3 * 1.5
        return (x, y, r, fade * tw * (1 - 0.7 * abs(lateral)))
    }
}
