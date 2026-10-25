import Foundation

/*
 Boba Pearl Progress — layout, the pearls' packing and the order's timeline. A cup's content is a
 pure function of its content time `tc` (pearls dropped, tea level, foam, seal, straw); the loop
 adds the shake and the cup swap. Every frame is a function of the loop time t. A port of the
 React Native bobaMotion.ts.

 autoPlay timeline (loop = 10 s):
   0.0 – 1.4   rest: an empty clear cup, "Brewing" at 0 %.
   1.4 – 2.8   40 tapioca pearls drop in, bottom row first, three scoops; each falls under gravity
               and bounces once (damped |sin|, 90 ms decay) into its packed place.
   2.6 – 5.0   milk tea pours in (ease-in-out cubic), surface waving; brown-sugar streaks climb.
   5.0 – 6.4   "Shaking": the cup rocks ±4° about its base (4 swings, sine envelope), the tea
               sloshes 70 ms behind and overshoots, pearls jiggle; foam rises on top.
   6.4 – 7.0   "Sealing": the film drops onto the rim (ease-out) and is pressed (squash 8 %).
   7.05 – 7.45 the straw drops in and punches the film.
   7.0 – 8.2   "Ready" at 100 %.
   8.2 – 9.0   the cup slides out to the left (ease-in-out cubic); 8.85 – 9.6 an empty cup slides in
               from the right with a little overshoot; the label returns to "Brewing", 0 %.
   9.6 – 10.0  rest.
 */
enum BobaPearlProgressMotion {
    static let loop = 10.0
    static let gravity = 2200.0

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double {
        let u = 1 - x
        return 1 - u * u * u
    }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    /// Ease-out with a small overshoot (about 4 %).
    static func easeOutBack(_ x: Double) -> Double {
        let c1 = 1.1
        let c3 = c1 + 1
        let u = x - 1
        return 1 + c3 * u * u * u + c1 * u * u
    }

    private static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 4.1) * 43758.5453
        return s - floor(s)
    }

    /// Half width of the cup's inside at height y above the base.
    static func halfWidth(_ g: BobaPearlProgressGeo, _ y: Double) -> Double {
        g.baseW + ((g.rimW - g.baseW) * y) / g.cupH - 3 * g.u
    }

    /// A pearl's resting place (cup frame: x from the axis, y up from the base), size and layer.
    struct Pearl {
        var x: Double
        var y: Double
        var r: Double
        var back: Bool
        var drop: Double
        var fromX: Double
    }

    /// Packs the pearls in rows from the base up: a back layer (smaller, higher, darker) and a front
    /// layer, with seeded jitter. Then orders the drops bottom first, in three scoops.
    static func packPearls(_ g: BobaPearlProgressGeo) -> [Pearl] {
        var out: [Pearl] = []
        for back in [true, false] {
            let r = g.pearlR * (back ? 0.86 : 1)
            let want = back ? 18 : 22
            var row = 0
            var placed = 0
            while placed < want && row < 8 {
                let y = r + Double(row) * r * 1.72 + (back ? 3 * g.u : 0)
                let half = halfWidth(g, y) - r
                let count = max(1, Int(floor((half * 2) / (r * 2.02))) + 1)
                let step = count > 1 ? (half * 2) / Double(count - 1) : 0
                let shift = (row % 2 == 1 ? step * 0.5 : 0) + (back ? step * 0.25 : 0)
                var i = 0
                while i < count && placed < want {
                    let k = Double(out.count)
                    var x = -half + Double(i) * step + shift
                    if x > half { x -= step }
                    x += (hash(k) - 0.5) * r * 0.35
                    out.append(Pearl(
                        x: x,
                        y: y + (hash(k + 50) - 0.5) * r * 0.25,
                        r: r * (0.94 + hash(k + 90) * 0.12),
                        back: back,
                        drop: 0,
                        fromX: 0
                    ))
                    placed += 1
                    i += 1
                }
                row += 1
            }
        }
        // A stable sort by height, as JavaScript's sort is.
        let order = out.indices.sorted { a, b in
            out[a].y != out[b].y ? out[a].y < out[b].y : a < b
        }
        let third = Int(ceil(Double(out.count) / 3))
        for (rank, idx) in order.enumerated() {
            let scoop = (rank * 3) / out.count
            out[idx].drop = 1.4 + Double(scoop) * 0.42 + Double(rank % third) * 0.022
            out[idx].fromX = (hash(Double(idx) + 7) - 0.5) * 34 * g.u
        }
        return out
    }

    /// Height above the base and x of pearl p at content time tc (−1 height: not dropped yet).
    static func pearlAt(_ g: BobaPearlProgressGeo, _ p: Pearl, _ tc: Double) -> (Double, Double) {
        if tc < p.drop { return (p.fromX, -1) }
        let startY = g.cupH + 46 * g.u
        let fall = startY - p.y
        let tFall = sqrt((2 * fall) / gravity)
        let tau = tc - p.drop
        if tau < tFall {
            let k = tau / tFall
            return (p.fromX + (p.x - p.fromX) * easeOutCubic(k), startY - 0.5 * gravity * tau * tau)
        }
        let after = tau - tFall
        let bounce = 9 * g.u * exp(-after / 0.09) * abs(sin(after * 24))
        return (p.x, p.y + bounce)
    }

    static func level(_ g: BobaPearlProgressGeo, _ tc: Double) -> Double {
        g.maxLevel * easeInOutCubic(seg(tc, 2.6, 5.0))
    }

    /// The pour: top of the stream reached (0 … 1), tail fallen (0 … 1).
    static func pour(_ tc: Double) -> (Double, Double) { (seg(tc, 2.5, 2.68), seg(tc, 4.82, 5.02)) }

    static func foam(_ tc: Double) -> Double { easeOutCubic(seg(tc, 5.3, 6.3)) }

    /// The seal: drop (0 above … 1 on the rim), squash (0 … 1 … 0).
    static func seal(_ tc: Double) -> (Double, Double) {
        (easeOutCubic(seg(tc, 6.4, 6.75)), sin(Double.pi * seg(tc, 6.72, 7.0)))
    }

    /// The straw: how far it has come down (0 … 1), the film's pop ring (0 … 1).
    static func straw(_ tc: Double) -> (Double, Double) {
        (easeOutBack(seg(tc, 7.05, 7.45)), seg(tc, 7.38, 7.75))
    }

    static func percent(_ tc: Double) -> Double {
        18 * seg(tc, 1.4, 2.8) + 42 * seg(tc, 2.8, 5.0) + 22 * seg(tc, 5.0, 6.4) + 13 * seg(tc, 6.4, 7.0) + 5 * seg(tc, 7.0, 7.2)
    }

    static let steps = ["Brewing", "Shaking", "Sealing", "Ready"]
    static let status = ["Brewing your tea", "Shaking it up", "Sealing your cup", "Ready for pickup"]
    static let eta = ["About 2 min", "About 1 min", "Almost there", "Counter 2"]

    static func stepOf(_ tc: Double) -> Int {
        tc < 5.0 ? 0 : (tc < 6.4 ? 1 : (tc < 7.0 ? 2 : 3))
    }

    /// Cup rotation (radians) of a shake that started `s` seconds ago (1.4 s long).
    static func shakeRot(_ s: Double) -> Double {
        if s <= 0 || s >= 1.4 { return 0 }
        let env = pow(sin((Double.pi * s) / 1.4), 0.6)
        return (4 * Double.pi / 180) * sin((2 * Double.pi * 4 * s) / 1.4) * env
    }

    static func shakeEnv(_ s: Double) -> Double {
        if s <= 0 || s >= 1.4 { return 0 }
        return sin((Double.pi * s) / 1.4)
    }

    static func loopScene(_ g: BobaPearlProgressGeo, _ t: Double, ambient: Double) -> BobaPearlProgressScene {
        var cups: [BobaPearlProgressCup] = []
        let s = t - 5.0
        let rot = shakeRot(s)
        let lagRot = shakeRot(s - 0.07)
        if t < 9.0 {
            cups.append(BobaPearlProgressCup(
                dx: -g.w * 0.8 * easeInOutCubic(seg(t, 8.2, 9.0)),
                rot: rot,
                tc: min(t, 8.2),
                tilt: -rot + 1.5 * lagRot,
                roll: 0,
                shake: shakeEnv(s),
                phase: s
            ))
        }
        if t > 8.85 {
            cups.append(BobaPearlProgressCup(dx: g.w * 0.8 * (1 - easeOutBack(seg(t, 8.85, 9.6))), rot: 0, tc: 0, tilt: 0, roll: 0, shake: 0, phase: 0))
        }
        let swapped = t >= 8.9
        let tcLabel = swapped ? 0 : min(t, 8.2)
        var last = -10.0
        for c in [5.0, 6.4, 7.0, 8.9] where t >= c { last = c }
        let prevStep = last == 8.9 ? 3 : (last == 5.0 ? 0 : (last == 6.4 ? 1 : (last == 7.0 ? 2 : 0)))
        return BobaPearlProgressScene(
            cups: cups,
            percent: percent(tcLabel),
            step: stepOf(tcLabel),
            prevStep: prevStep,
            stepFade: last < 0 ? 1 : clamp01((t - last) / 0.3),
            pct: (100, 0, easeInOutCubic(seg(t, 8.8, 9.1))),
            clock: ambient
        )
    }
}

/// One cup to draw.
struct BobaPearlProgressCup {
    var dx: Double
    var rot: Double
    var tc: Double
    /// Surface tilt in the cup's frame, pearl roll (pt), shake envelope and phase.
    var tilt: Double
    var roll: Double
    var shake: Double
    var phase: Double
}

struct BobaPearlProgressScene {
    var cups: [BobaPearlProgressCup]
    var percent: Double
    /// Step shown, the previous one and the cross-fade between them.
    var step: Int
    var prevStep: Int
    var stepFade: Double
    /// Progress value cross-fade during the swap: value before, value after, fade.
    var pct: (Int, Int, Double)
    /// The ambient time for waves and the stream.
    var clock: Double
}

/// Screen layout, in points.
struct BobaPearlProgressGeo: Equatable {
    let w: Double
    let h: Double
    let u: Double
    let cx: Double
    /// Cup: rim y, base y, half widths at rim and base, rim and base ellipse radii.
    let top: Double
    let base: Double
    let rimW: Double
    let baseW: Double
    let rimRy: Double
    let baseRy: Double
    let cupH: Double
    let maxLevel: Double
    let pearlR: Double
    let percentY: Double
    let stepsY: Double
    let buttonY: Double

    init(w: Double, h: Double, top: Double, bottom: Double) {
        let u = min(w / 393, (h - top - bottom) / 759)
        let cupTop = top + 104 + 86 * u
        let cupH = 300 * u
        self.w = w
        self.h = h
        self.u = u
        self.cx = w / 2
        self.top = cupTop
        self.base = cupTop + cupH
        self.rimW = 96 * u
        self.baseW = 70 * u
        self.rimRy = 13 * u
        self.baseRy = 9 * u
        self.cupH = cupH
        self.maxLevel = cupH * 0.8
        self.pearlR = 8.4 * u
        self.percentY = cupTop + cupH + 36 * u
        self.stepsY = cupTop + cupH + 104 * u
        self.buttonY = h - bottom - 16 - 52
    }
}
