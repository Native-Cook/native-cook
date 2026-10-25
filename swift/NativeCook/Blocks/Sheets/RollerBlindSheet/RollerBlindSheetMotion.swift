import Foundation

/*
 Roller Blind Sheet — geometry and the loop's script, as pure functions of the loop time (frame at
 t = loop equals frame at 0).

 The blind hangs from a roller across the top of the window. Its hem (a wooden bar) sits at hemY;
 the fabric between the roller and the hem is unrolled, so the roll's radius shrinks as hemY
 grows. A pull cord with a bead hangs from the middle of the hem and swings like a pendulum.

 Detents for the hem: up (rolled up, just under the roll), half (the window's sill), full (the
 sheet covers the room's controls).

 autoPlay script (loop = 9 s):
   0.0 – 1.2   rest: the blind half down at the sill, the scenes printed on it, the room a little dim.
   1.0 – 1.2   a finger lands on the bead.
   1.2 – 3.2   pull down to full (ease-in-out, a 3 % lift first), 14 pt past it.
   3.2 – 3.6   detent bounce (zero at both ends); the finger lets go, the cord swings 10°.
   3.6 – 5.6   hold.
   5.6 – 5.9   tug: the bead 20 pt down (ease-out), let go.
   5.9 – 7.0   the spring roller winds the blind up (ease-out); 7.0 – 7.2 the hem knocks the roll.
   7.2 – 7.5   a finger takes the bead again.
   7.5 – 8.3   pull down to the sill (ease-in-out), 8 pt past it; 8.3 – 8.6 settle (bounce).
   8.6 – 9.0   rest.
 */
struct RollerBlindSheetGeo {
    let w: Double
    let h: Double
    let unit: Double
    /// The blind's fabric spans x0 … x1; the roller's axis is at rollerY.
    let x0: Double
    let x1: Double
    let rollerY: Double
    /// The window opening.
    let winX: Double
    let winY: Double
    let winW: Double
    let winH: Double
    /// Hem detents.
    let up: Double
    let half: Double
    let full: Double
    /// Where the room's controls start (under the window).
    let controlsY: Double

    init(w: Double, h: Double, top: Double, bottom: Double) {
        self.w = w
        self.h = h
        unit = max(0.74, min(1.15, min(w / 393, h / 852)))
        rollerY = top + 116 * unit
        winX = 30
        winY = rollerY + 16 * unit
        winW = w - 60
        winH = min(330 * unit, h * 0.39)
        up = rollerY + 22 * unit
        half = winY + winH + 8 * unit
        full = h - bottom - 112 * unit
        x0 = 20
        x1 = w - 20
        controlsY = winY + winH + 22 * unit
    }
}

enum RollerBlindSheetMotion {
    typealias G = RollerBlindSheetGeo

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOut(_ x: Double) -> Double { let u = 1 - x; return 1 - u * u * u }
    static func easeInOut(_ x: Double) -> Double {
        if x < 0.5 { return 4 * x * x * x }
        let v = -2 * x + 2
        return 1 - v * v * v / 2
    }

    /// The hem's y at loop time t.
    static func hemAt(_ g: G, _ t: Double) -> Double {
        let over = 14 * g.unit
        if t < 1.2 { return g.half }
        if t < 3.2 {
            let u = seg(t, 1.2, 3.2)
            let span = g.full - g.half
            // a small lift first (the hand takes the weight), then the long pull
            let lift = -0.03 * (g.full - g.up) * sin(.pi * seg(u, 0, 0.16))
            return g.half + (span + over) * easeInOut(u) + lift
        }
        if t < 3.6 {
            let u = seg(t, 3.2, 3.6)
            return g.full + over * cos(.pi * 3 * u) * (1 - u) * (1 - u)
        }
        if t < 5.6 { return g.full }
        let tug = 20 * g.unit
        if t < 5.9 { return g.full + tug * easeOut(seg(t, 5.6, 5.9)) }
        if t < 7.0 { return g.up + (g.full + tug - g.up) * (1 - easeOut(seg(t, 5.9, 7.0))) }
        if t < 7.2 { return g.up + 4 * g.unit * sin(.pi * seg(t, 7.0, 7.2)) }
        if t < 7.5 { return g.up }
        let small = 8 * g.unit
        if t < 8.3 { return g.up + (g.half + small - g.up) * easeInOut(seg(t, 7.5, 8.3)) }
        if t < 8.6 {
            let u = seg(t, 8.3, 8.6)
            return g.half + small * cos(.pi * 3 * u) * (1 - u) * (1 - u)
        }
        return g.half
    }

    /// One swing of the cord (degrees): amplitude a, decay tau, starting at t0.
    private static func swingFrom(_ t: Double, _ t0: Double, _ a: Double, _ tau: Double) -> Double {
        if t < t0 { return 0 }
        let x = t - t0
        return a * exp(-x / tau) * sin(.pi * 2 * 1.4 * x)
    }

    /// The cord's angle from straight down (degrees) at loop time t (held straight while pulled).
    static func cordAt(_ t: Double) -> Double {
        if t >= 1.2 && t < 3.2 { return 0 }
        if t >= 5.6 && t < 5.9 { return 0 }
        if t >= 7.5 && t < 8.3 { return 0 }
        var a = swingFrom(t, 3.2, 10, 0.5) * (1 - seg(t, 5.3, 5.6))
        a += swingFrom(t, 5.9, -8, 0.45)
        a += swingFrom(t, 7.0, 12, 0.35) * (1 - easeInOut(seg(t, 7.2, 7.5)))
        a += swingFrom(t, 8.3, 6, 0.3) * (1 - easeInOut(seg(t, 8.6, 9.0)))
        return a
    }

    /// The finger on the bead at loop time t (alpha).
    static func fingerAt(_ t: Double) -> Double {
        if t >= 1.0 && t < 3.4 { return seg(t, 1.0, 1.2) * (1 - seg(t, 3.2, 3.4)) }
        if t >= 5.4 && t < 6.1 { return seg(t, 5.4, 5.6) * (1 - seg(t, 5.9, 6.1)) }
        if t >= 7.2 && t < 8.5 { return seg(t, 7.2, 7.4) * (1 - seg(t, 8.3, 8.5)) }
        return 0
    }

    /// How much the fabric blocks the window (0 … 1): the room's dimming.
    static func coverOf(_ g: G, _ hem: Double) -> Double { clamp01((hem - g.winY) / g.winH) }

    /// The roll's radius (pt) with the hem at hemY: thick when rolled up, thin when unrolled.
    static func rollRadius(_ g: G, _ hem: Double) -> Double {
        let core = 7.5 * g.unit
        let thick = 13.5 * g.unit
        let left = clamp01(1 - (hem - g.up) / (g.full + 40 * g.unit - g.up))
        return sqrt(core * core + (thick * thick - core * core) * left)
    }

    /// Where the hem may be dragged to (rubber band past up and full).
    static func rubberHem(_ g: G, _ y: Double) -> Double {
        if y > g.full {
            let o = y - g.full
            return g.full + o / (1 + o / (60 * g.unit))
        }
        if y < g.up {
            let o = g.up - y
            return g.up - o / (1 + o / (16 * g.unit))
        }
        return y
    }

    /// The detent a release lands on, from where the hem is and how fast it moves (projected 0.2 s).
    static func detentFor(_ g: G, _ hem: Double, _ v: Double) -> Double {
        let aim = hem + v * 0.2
        var best = g.up
        for s in [g.half, g.full] where abs(s - aim) < abs(best - aim) { best = s }
        return best
    }
}
