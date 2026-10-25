import Foundation

/*
 Paper Cut Diorama Splash — the timeline (loop = 9 s), a pure function of the scene time t.
 The rest pose (and the poster) is the finished diorama with the logo lit, so t = 0 and t = loop
 are the same frame and the screen is never blank.

   0.0 – 0.4   rest: the lit diorama, the lamp breathing ±6 % (three whole sines a loop).
   0.4 – 1.8   the scene sways once as if the phone were tilted (one whole sine, parallax by depth).
   1.8 – 3.0   the camera flies through the logo's door: every layer scales about the door, the
               front frame fastest (×40) and the sky slowest (×17), ease-in-out on a log scale so
               the speed feels even; the lamp behind the door fills the screen and the home screen
               fades in over it (2.55 – 3.0, scale 1.04 → 1).
   3.0 – 5.0   home.
   5.0 – 5.6   home recedes (opacity 1 → 0, scale 1 → 0.96, ease-in-out) while the diorama
               rebuilds: the six layers rise from below as one piece (5.0 – 6.2, ease-out-back,
               4 % overshoot), each layer behind the frame trailing it by up to 40 pt × (1 − depth)
               mid-rise (a sine bump, 0 at both ends), so the arch carries the scene up inside it
               and nothing shows outside the window.
   6.4 – 7.1   the lamp comes back on behind the door and the wordmark (ease-out).
   7.1 – 9.0   rest.
 */
enum PaperCutDioramaSplashMotion {
    static let loop: Double = 9
    static let layers = 6
    /// Depth of each layer for parallax and zoom: sky … frame.
    static let depth: [Double] = [0.42, 0.5, 0.58, 0.68, 0.8, 1]

    static let sway = (0.4, 1.8)
    static let zoom = (1.8, 3.0)
    static let zoomMax: Double = 40
    static let homeIn = (2.55, 3.0)
    static let homeOut = (5.0, 5.6)
    static let riseAt: Double = 5.0
    static let riseLen: Double = 1.2
    static let riseLag: Double = 40
    static let lightOn = (6.4, 7.1)
    /// Where a finger takes the scene: the rest pose (t = 0, the same frame as t = loop).
    static let restScene: Double = 0
    /// After this, a finger plays the rebuild forward to the loop's end rather than back through home.
    static let rebuildAt = riseAt

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
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

    /// A cubic bézier easing (CSS style).
    static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> (Double) -> Double {
        func coord(_ s: Double, _ p1: Double, _ p2: Double) -> Double {
            ((((1 - 3 * p2 + 3 * p1) * s) + (3 * p2 - 6 * p1)) * s + 3 * p1) * s
        }
        return { x in
            if x <= 0 { return 0 }
            if x >= 1 { return 1 }
            var lo = 0.0, hi = 1.0, s = x
            for _ in 0..<40 {
                let v = coord(s, x1, x2)
                if abs(v - x) < 1e-6 { break }
                if v < x { lo = s } else { hi = s }
                s = (lo + hi) / 2
            }
            return coord(s, y1, y2)
        }
    }
    static let backEase = bezier(0.65, 0, 0.35, 1)

    /// A finger's rubber band: follows 1:1 near zero, never past ±1.6.
    static func rubber(_ v: Double) -> Double { v / (1 + abs(v) / 1.6) }

    struct Layer { var rise: Double; var swayX: Double; var swayY: Double; var scale: Double }

    struct Pose {
        var layers: [Layer]
        var light: Double
        /// Layers drawn at all (0 while the home screen covers them).
        var visible: Double
        var home: Double
        var homeScale: Double
    }

    static func pose(_ t: Double, riseDist: Double) -> Pose {
        let rebuilding = t >= riseAt
        let zu = rebuilding ? 0 : easeInOutCubic(seg(t, zoom.0, zoom.1))
        let S = exp(log(zoomMax) * zu)
        let sw = sin(2 * .pi * seg(t, sway.0, sway.1))
        var out: [Layer] = []
        for d in depth {
            // The whole diorama rises on one curve; each layer behind the frame trails it a little
            // and catches up as it settles, so the window is never empty.
            let u = seg(t, riseAt, riseAt + riseLen)
            let rise = rebuilding ? (1 - easeOutBack(u, 0.9)) * riseDist + (1 - d) * riseLag * sin(.pi * u) : 0
            out.append(Layer(rise: rise, swayX: 16 * sw * (d - 0.66) * 2.2, swayY: -3 * sw * sw * (d - 0.4), scale: 1 + (S - 1) * d))
        }
        let breath = 1 + 0.06 * sin(2 * .pi * 3 * t / loop)
        let on = rebuilding ? easeOutCubic(seg(t, lightOn.0, lightOn.1)) : 1
        let light = on * breath * (rebuilding ? 1 : 1 + 1.5 * seg(t, 2.0, 2.8))
        let hIn = easeOutCubic(seg(t, homeIn.0, homeIn.1))
        let hOut = easeInOutCubic(seg(t, homeOut.0, homeOut.1))
        return Pose(layers: out, light: light, visible: t < 3.05 || rebuilding ? 1 : 0,
                    home: hIn * (1 - hOut), homeScale: rebuilding ? 1 - 0.04 * hOut : 1.04 - 0.04 * hIn)
    }
}
