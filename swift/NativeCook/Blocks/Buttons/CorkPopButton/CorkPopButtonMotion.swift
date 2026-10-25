import Foundation

/*
 Cork Pop Button — the motion. A frame is a pure function of a few clocks: the cork's state before
 the pop (inch, rock, how loose the cage is), the time since the pop, and the time since the
 bottle swap began. The loop fills them from the timeline; a finger fills them from the
 component's simulation, so both share one drawing.

 autoPlay (loop = 9 s):
   0.0 – 1.2   rest: the corked bottle, "Twist the cork up".
   1.2 – 3.2   three twists (rock ±5°, cage key spins 3 turns), each inching the cork up 4.5 pt (ease-out,
               0.25 s); strain shake 2.9 – 3.2.
   3.2         pop: cork and cage fly up with a spin (1700 pt/s, g 1400), a CO2 puff (1.1 s).
   3.2 – 4.6   foam gushes from the lip (metaball shader, 16 balls) and runs down the neck; the order
               card lifts (ease-out back, 0.4 s) with a gold sheen; the line rolls to "Cheers".
   4.4 – 8.7   gold bubbles rise across the screen and pop (each 1.6 – 2.2 s).
   4.6 – 6.2   the foam settles to a small crown.
   6.4 – 8.0   the swap: the open bottle sinks (0.7 s), a corked one rises (0.8 s, ease-out back); the card
               settles, the line rolls back. 8.0 – 9.0 rest; 9.0 is the same frame as 0.0.
 */
enum CorkPopButtonMotion {
    static let loop = 9.0
    static let popAt = 3.2
    static let swapAt = 6.4
    static let swapFor = 1.6
    static let bubbleCount = 56
    static let ballCount = 16
    /// Bubbles are gone this long after the pop.
    static let bubblesDone = 5.7

    struct Geo: Equatable {
        var w: Double
        var h: Double
        var cx: Double
        /// Top of the glass lip, the neck's half width there, the neck's length, the shoulder's end.
        var lipY: Double
        var neck: Double
        var neckLen: Double
        var body: Double
        /// The cork's mushroom: half width, height (above the lip).
        var corkW: Double
        var corkH: Double
        /// The order card.
        var cardX: Double
        var cardY: Double
        var cardW: Double
        var cardH: Double
        var shoulder: Double
        var labelY: Double
        var hintY: Double
    }

    /// What a frame needs, before and after the pop. -1 for a clock that has not started.
    struct Clocks {
        var inch = 0.0
        var rock = 0.0
        var loose = 0.0
        var shake = 0.0
        var finger = 0.0
        var popE = -1.0
        var swapE = -1.0
        var swapFor = CorkPopButtonMotion.swapFor
    }

    struct Fog { var x, y, r, alpha: Double }
    struct Bubble { var x, y, r, alpha, ring: Double }

    struct Pose {
        /// The bottle on screen (0 = the open one, 1 = the fresh one) and its drop (pt).
        var fresh = 0.0
        var drop = 0.0
        var corked = true
        var inch = 0.0
        var rock = 0.0
        var loose = 0.0
        var finger = 0.0
        /// Flying cork: x, y, rotation.
        var cork: (Double, Double, Double)?
        /// Foam balls x, y, r (16) and the foam's bounds.
        var balls: [Float] = []
        var bounds = CGRect.zero
        var fog: [Fog] = []
        var bubbles: [Bubble] = []
        var lift = 0.0
        var sheen = 0.0
        var label = 0.0
    }

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double {
        let k = 1 - clamp01(x)
        return 1 - k * k * k
    }
    static func easeInOutCubic(_ x: Double) -> Double {
        let k = clamp01(x)
        if k < 0.5 { return 4 * k * k * k }
        let m = -2 * k + 2
        return 1 - m * m * m / 2
    }
    static func easeOutBack(_ x: Double, _ c: Double = 1.7) -> Double {
        let k = clamp01(x) - 1
        return 1 + (c + 1) * k * k * k + c * k * k
    }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// The twist phase of the loop (1.2 – 3.2).
    static func autoClocks(_ t: Double) -> Clocks {
        if t >= popAt {
            return Clocks(popE: t - popAt, swapE: t >= swapAt ? t - swapAt : -1, swapFor: swapFor)
        }
        var inch = 0.0
        var rock = 0.0
        for start in [1.35, 1.95, 2.55] {
            inch += 4.5 * easeOutCubic(seg(t, start + 0.3, start + 0.55))
            // Each twist: a turn one way (anticipation), then the other, while the cork rises.
            let u = seg(t, start, start + 0.55)
            rock += 5 * sin(.pi * 2 * u) * (u > 0 && u < 1 ? 1 : 0)
        }
        let loose = easeInOutCubic(seg(t, 1.3, 2.6))
        let shake = seg(t, 2.9, 3.05) * 0.8 * sin(2 * .pi * 41 * t)
        let finger = easeOutCubic(seg(t, 1.1, 1.3)) * (1 - seg(t, 3.12, 3.2))
        return Clocks(inch: inch, rock: rock, loose: loose, shake: shake, finger: finger)
    }

    /// Where foam ball j is e seconds after the pop, relative to the lip's centre (dx, dy, r).
    static func foamBall(_ g: Geo, _ j: Int, _ e: Double) -> (Double, Double, Double) {
        let dj = Double(j)
        let settle = 1 - 0.82 * easeInOutCubic(seg(e, 1.4, 3.0))
        if j == 0 {
            // The crown sitting on the lip.
            return (0, -g.neck * 0.25, g.neck * (0.2 + 0.9 * easeOutBack(seg(e, 0, 0.22))) * (0.55 + 0.45 * settle) * (e < 0 ? 0 : 1))
        }
        if j <= 9 {
            // Gushers: thrown up at different speeds so they stack into one column, then slow, fall
            // back and run down the neck's sides.
            let start = 0.025 * dj
            let tau = max(0, e - start)
            let v = 230 + 52 * dj + 40 * hash(dj + 3)
            let grav = 900.0
            let tPeak = v / grav
            let side = j % 2 == 0 ? 1.0 : -1.0
            let fall = tPeak * 1.75
            var dx: Double
            var dy: Double
            if tau < fall {
                dy = -(v * tau - 0.5 * grav * tau * tau)
                dx = side * (4 + 16 * hash(dj + 9)) * tau * 2
            } else {
                // Down the neck, slowing like cream.
                let k = 1 - exp(-(tau - fall) / 0.7)
                let y0 = -(v * fall - 0.5 * grav * fall * fall)
                dy = y0 + k * (g.neckLen * (0.2 + 0.4 * hash(dj + 5)) - y0)
                dx = side * (4 + 16 * hash(dj + 9)) * fall * 2 * (1 - k) + side * (g.neck + 3) * k
            }
            let grow = easeOutCubic(tau / 0.2)
            let late = j > 6 ? 0.8 : 1.0
            let r = (e < start ? 0 : 1) * g.neck * (0.52 + 0.3 * hash(dj + 1)) * late * (0.45 + 0.55 * grow) * settle
            return (dx, dy, r)
        }
        // Drips over the lip, running down both sides.
        let start = 0.3 + 0.1 * (dj - 10)
        let tau = e - start
        let side = j % 2 == 0 ? 1.0 : -1.0
        let k = 1 - exp(-max(0, tau) / 0.9)
        let r = tau < 0 ? 0 : g.neck * (0.3 + 0.12 * hash(dj + 2)) * easeOutCubic(tau / 0.3) * settle
        return (side * (g.neck + 2), -2 + k * g.neckLen * (0.3 + 0.35 * hash(dj + 21)), r)
    }

    static func makePose(_ c: Clocks, _ g: Geo) -> Pose {
        let H = g.h - g.lipY + g.corkH + 40
        var p = Pose()
        var fresh = 0.0
        var drop = 0.0
        var corked = true
        if c.popE >= 0 { corked = false }
        if c.swapE >= 0 {
            let sink = c.swapFor * 0.44
            if c.swapE < sink {
                drop = H * easeInOutCubic(c.swapE / sink)
            } else {
                fresh = 1
                corked = true
                drop = H * (1 - easeOutBack((c.swapE - sink) / (c.swapFor * 0.5), 1.2))
            }
        }
        let lipX = g.cx
        let lipY = g.lipY + drop
        let e = c.popE
        if e >= 0 && e < 0.8 {
            let y = g.lipY - g.corkH * 0.5 - 18 - 1700 * e + 0.5 * 1400 * e * e
            if y > -80 { p.cork = (lipX + 90 * e, y, 820 * e) }
        }
        if e >= 0 && fresh == 0 {
            var x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9
            p.balls.reserveCapacity(ballCount * 3)
            for j in 0..<ballCount {
                let b = foamBall(g, j, e)
                let x = lipX + b.0
                let y = lipY + b.1
                p.balls.append(contentsOf: [Float(x), Float(y), Float(b.2)])
                if b.2 > 0.5 {
                    x0 = min(x0, x - b.2 * 2.2)
                    y0 = min(y0, y - b.2 * 2.2)
                    x1 = max(x1, x + b.2 * 2.2)
                    y1 = max(y1, y + b.2 * 2.2)
                }
            }
            if x1 > x0 { p.bounds = CGRect(x: x0, y: y0, width: x1 - x0, height: y1 - y0) }
            if e < 1.1 {
                for k in 0..<7 {
                    let dk = Double(k)
                    let u = seg(e, 0.02 * dk, 1.1)
                    let a = -Double.pi / 2 + (hash(dk + 40) - 0.5) * 2.2
                    let d = g.neck * (0.6 + 2.4 * easeOutCubic(u)) * (0.6 + 0.6 * hash(dk + 44))
                    p.fog.append(Fog(x: lipX + cos(a) * d * 1.3, y: lipY - 10 + sin(a) * d - 40 * u,
                                     r: g.neck * (0.5 + 1.5 * easeOutCubic(u)),
                                     alpha: 0.55 * (1 - u) * seg(e, 0.02 * dk, 0.02 * dk + 0.06)))
                }
            }
        }
        if e >= 0 {
            for b in 0..<bubbleCount {
                let db = Double(b)
                let start = 1.0 + 2.6 * hash(db + 70)
                let life = 1.5 + 0.55 * hash(db + 73)
                let tau = e - start
                if tau < 0 || tau > life { continue }
                let u = tau / life
                let r = 4 + 11 * pow(hash(db + 77), 1.5)
                let spread = (hash(db + 79) - 0.5) * g.w * 1.05
                let x = lipX + spread * easeOutCubic(u * 1.4) + 9 * sin(2 * .pi * (1.2 * tau + hash(db + 81)))
                let y = g.lipY - 30 - (220 + 220 * hash(db + 83)) * tau - 60 * tau * tau
                let ring = seg(tau, life - 0.14, life)
                p.bubbles.append(Bubble(x: x, y: y, r: r * (0.55 + 0.45 * easeOutCubic(tau / 0.25)) * (1 + 0.6 * ring), alpha: 1 - ring, ring: ring))
            }
        }
        let sw = c.swapE
        p.lift = (e >= 0 ? easeOutBack(seg(e, 0, 0.4)) : 0) * (1 - (sw >= 0 ? easeInOutCubic(seg(sw, c.swapFor * 0.12, c.swapFor * 0.5)) : 0))
        p.label = (e >= 0 ? easeInOutCubic(seg(e, 0.1, 0.45)) : 0) * (1 - (sw >= 0 ? easeInOutCubic(seg(sw, c.swapFor * 0.62, c.swapFor * 0.84)) : 0))
        p.fresh = fresh
        p.drop = drop
        p.corked = corked
        p.inch = fresh > 0 ? 0 : c.inch + c.shake
        p.rock = fresh > 0 ? 0 : c.rock
        p.loose = fresh > 0 ? 0 : c.loose
        p.finger = fresh > 0 ? 0 : c.finger
        p.sheen = e >= 0 ? seg(e, 0.1, 1.0) : 0
        return p
    }
}
