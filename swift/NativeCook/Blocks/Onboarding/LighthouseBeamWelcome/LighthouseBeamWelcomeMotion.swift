import Foundation

/*
 Lighthouse Beam Welcome — timing and the beam's geometry. Every frame of the loop is a pure
 function of the loop time t, so t = loop draws the same frame as t = 0. A port of the React
 Native lighthouseMotion.ts.

 The lantern turns about a vertical axis: azimuth φ = 0 points right (across the screen), φ = π/2
 towards the viewer, π left, 3π/2 away. Seen from a little above, the beam's direction on the
 screen is (cos φ, E·sin φ + TILT): it is longest pointing sideways, short and wide (a flare)
 pointing at the viewer, short and faint pointing away.

 autoPlay timeline (loop = 10 s):
   0.0 – 1.8   rest: the beam lies across page 1's text (φ = 0), fog drifting, sea moving.
   1.8 – 10.0  two whole turns (φ 0 → 4π). The lantern speeds up over 0.7 s, turns at a constant
               rate (3.75 s a turn, linear: a real rotation), and slows over 0.7 s into the rest.
               Text is revealed only where the cone passes and glows on for 0.8 s after it.
   3.73 – 4.33 page 1 → 2 (ease-in-out cubic), centred on φ = π, while the beam is behind the tower.
   7.48 – 8.08 page 2 → 1, centred on φ = 3π; the last sweep reveals page 1 and comes to rest on it.
 */
enum LighthouseBeamWelcomeMotion {
    static let loop = 10.0
    /// Viewer elevation (how much "towards the viewer" drops on screen) and the beam's downward tilt.
    static let e = 0.3
    static let tilt = 0.12
    static let turnStart = 1.8
    static let turnEnd = 10.0
    static let ramp = 0.7
    static let turns = 2.0
    /// Times (s) the trail looks back, for the 0.8 s afterglow.
    static let trail: [Double] = [0.1, 0.22, 0.36, 0.55, 0.8]
    static let pages = 3
    static let slide = 0.6

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    /// 0 … 1 over [a, b] with a constant speed in the middle and smooth ramps of `ramp` s at both ends.
    static func trapezoid(_ t: Double, _ a: Double, _ b: Double, _ ramp: Double) -> Double {
        let span = b - a
        let vmax = 1 / (span - ramp)
        let x = t - a
        if x <= 0 { return 0 }
        if x >= span { return 1 }
        if x < ramp {
            let u = x / ramp
            return vmax * ramp * (u * u * u - (u * u * u * u) / 2)
        }
        if x < span - ramp { return vmax * (ramp * 0.5 + (x - ramp)) }
        let u = (span - x) / ramp
        return 1 - vmax * ramp * (u * u * u - (u * u * u * u) / 2)
    }

    /// The lantern's azimuth at loop time t, unwrapped (0 … 4π).
    static func phiAt(_ t: Double) -> Double {
        2 * Double.pi * turns * trapezoid(t, turnStart, turnEnd, ramp)
    }

    /// φ at t − back, continuous across the loop's seam (one loop earlier is 4π less).
    static func phiBefore(_ t: Double, _ back: Double) -> Double {
        let s = t - back
        return s >= 0 ? phiAt(s) : phiAt(s + loop) - 2 * Double.pi * turns
    }

    // The page changes sit where the beam points away from the text (φ = π and 3π), found once.
    private static func timeOfPhi(_ target: Double) -> Double {
        var lo = turnStart
        var hi = turnEnd
        for _ in 0..<40 {
            let mid = (lo + hi) / 2
            if phiAt(mid) < target { lo = mid } else { hi = mid }
        }
        return (lo + hi) / 2
    }

    static let pageOut = timeOfPhi(Double.pi)
    static let pageBack = timeOfPhi(3 * Double.pi)

    /// The page shown, as a float (0 = page 1, 1 = page 2).
    static func pageAt(_ t: Double) -> Double {
        let out = easeInOutCubic(seg(t, pageOut - slide / 2, pageOut + slide / 2))
        let back = easeInOutCubic(seg(t, pageBack - slide / 2, pageBack + slide / 2))
        return out - back
    }

    /// The lantern azimuth that points the beam at a screen point (relative to the lamp), nearest `near`.
    static func aimAt(_ dx: Double, _ dy: Double, near: Double) -> Double {
        // Solve (dx, dy)·m = (cos φ, E sin φ + TILT) for m > 0, then read φ.
        let a = dx * dx + (dy * dy) / (e * e)
        let b = (-2 * dy * tilt) / (e * e)
        let c = (tilt * tilt) / (e * e) - 1
        let disc = max(0, b * b - 4 * a * c)
        let m = a > 1e-6 ? (-b + sqrt(disc)) / (2 * a) : 0
        let cosP = dx * m
        let sinP = max(-1, min(1, (dy * m - tilt) / e))
        let phi = atan2(sinP, cosP)
        let k = ((near - phi) / (2 * Double.pi)).rounded()
        return phi + k * 2 * Double.pi
    }

    /// How much the beam faces the viewer (0 … 1): drives the lamp's flare.
    static func facing(_ phi: Double) -> Double {
        let s = sin(phi)
        return s > 0 ? pow(s, 4) : 0
    }

    /// CSS-style cubic bezier easing (x1, y1, x2, y2) at x in 0…1.
    static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        func coord(_ t: Double, _ a: Double, _ b: Double) -> Double {
            let u = 1 - t
            return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t
        }
        var lo = 0.0
        var hi = 1.0
        var t = x
        for _ in 0..<40 {
            if coord(t, x1, x2) < x { lo = t } else { hi = t }
            t = (lo + hi) / 2
        }
        return coord(t, y1, y2)
    }

    static func easeInOut(_ x: Double) -> Double { bezier(0.65, 0, 0.35, 1, x) }
}

/// Screen layout, in points.
struct LighthouseBeamWelcomeGeo: Equatable {
    let w: Double
    let h: Double
    let u: Double
    let horizon: Double
    let lampX: Double
    let lampY: Double
    /// The tower: base y and gallery y.
    let baseY: Double
    let galleryY: Double
    /// Text block: left, top, width (the content image covers [contentY, contentY + contentH]).
    let textX: Double
    let textY: Double
    let textW: Double
    let contentY: Double
    let contentH: Double

    init(w: Double, h: Double, top: Double, bottom: Double) {
        let m = LighthouseBeamWelcomeMotion.self
        let u = min(w / 393, (h - top - bottom) / 759)
        let horizon = (top + (h - top - bottom) * 0.6).rounded()
        let lampX = (w * 0.16).rounded()
        let lampY = (top + (h - top - bottom) * 0.27).rounded()
        let textX = max(lampX + 96 * u, w * 0.4).rounded()
        let textW = w - textX - 24
        // The text block's middle sits on the beam's axis at rest, a little right of its left edge.
        let mid = lampY + m.tilt * (textX + textW * 0.3 - lampX)
        let textY = (mid - 80 * u).rounded()
        self.w = w
        self.h = h
        self.u = u
        self.horizon = horizon
        self.lampX = lampX
        self.lampY = lampY
        self.baseY = horizon + 30 * u
        self.galleryY = lampY + 16 * u
        self.textX = textX
        self.textY = textY
        self.textW = textW
        self.contentY = textY - 40
        self.contentH = (180 * u).rounded() + 80
    }
}
