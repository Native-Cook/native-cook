import SwiftUI

/*
 The tear, as geometry. Everything is in the card's own coordinates (origin at its top-left corner).

 A tear starts at S on the card's edge and runs along the unit vector u for `length` points; n is u turned
 a quarter turn (u = down gives n = left). The cut is a jagged line: a point every 6 pt along u, pushed
 sideways along n by a seeded noise (±3 pt grain over a ±5 pt wander), tapering to nothing at the tip T.

 The card is cut into three regions:
   side +1   the +n side of the cut, behind the line through T across u (the left flap when u points down);
   side −1   the −n side;
   rest      everything past that line (the part not yet torn), drawn unchanged.
 A flap is sheared away from the cut: a point p moves by side · open · n · (length − u·(p − S)), so it moves
 most at the card's edge and not at all on the line through T, and the two flaps open a wedge without a seam.
 Once the cut has crossed the card (commit), each flap also falls: a small pop, gravity, a drift outwards
 and a spin the same way.
 */

struct TornPhotoDeleteTear {
    /// Start on the card's edge and direction (angle in degrees, 90 = down), in card points.
    var start: CGPoint
    var angle: Double
    var length: Double
    /// How far the flaps are sheared apart (0 = shut).
    var open: Double
    var seed: Int
    /// Seconds since the tear crossed the card, while its pieces fall.
    var fall: Double? = nil

    var u: CGVector { CGVector(dx: cos(angle * .pi / 180), dy: sin(angle * .pi / 180)) }
    var n: CGVector { CGVector(dx: -u.dy, dy: u.dx) }
    var tip: CGPoint { CGPoint(x: start.x + u.dx * length, y: start.y + u.dy * length) }

    static let step = 6.0
    static let lead = 24.0

    /// The cut's points from just outside the edge to the tip, each with the width of the white paper core
    /// shown on the +n side (the −n side shows 0.55 of it).
    func cut() -> [(p: CGPoint, core: Double)] {
        var pts: [(CGPoint, Double)] = []
        let u = self.u, n = self.n
        func at(_ d: Double, _ off: Double) -> CGPoint {
            CGPoint(x: start.x + u.dx * d + n.dx * off, y: start.y + u.dy * d + n.dy * off)
        }
        pts.append((at(-Self.lead, 0), 0))
        var i = 0
        while Double(i) * Self.step < length {
            let d = Double(i) * Self.step
            let taper = min(1, (length - d) / 14)
            let off = TornPhotoDeleteNoise.offset(i, seed: seed) * taper
            let core = (1.6 + 3.4 * TornPhotoDeleteNoise.hash(i + 500, seed)) * min(1, (length - d) / 30)
            pts.append((at(d, off), core))
            i += 1
        }
        pts.append((at(length, 0), 0))
        return pts
    }

    /// The region of one side (+1 or −1) behind the tip, as a polygon.
    func region(_ side: Double, cut: [(p: CGPoint, core: Double)]) -> Path {
        let far = 2000.0
        let u = self.u, n = self.n
        let t = tip
        let first = cut[0].p
        var path = Path()
        path.move(to: first)
        for c in cut.dropFirst() { path.addLine(to: c.p) }
        path.addLine(to: CGPoint(x: t.x + n.dx * far * side, y: t.y + n.dy * far * side))
        path.addLine(to: CGPoint(x: first.x - u.dx * far + n.dx * far * side, y: first.y - u.dy * far + n.dy * far * side))
        path.addLine(to: CGPoint(x: first.x - u.dx * far, y: first.y - u.dy * far))
        path.closeSubpath()
        return path
    }

    /// The part past the tip, not torn yet.
    func rest() -> Path {
        let far = 2000.0
        let u = self.u, n = self.n
        let t = tip
        var path = Path()
        path.move(to: CGPoint(x: t.x - n.dx * far, y: t.y - n.dy * far))
        path.addLine(to: CGPoint(x: t.x + n.dx * far, y: t.y + n.dy * far))
        path.addLine(to: CGPoint(x: t.x + n.dx * far + u.dx * far, y: t.y + n.dy * far + u.dy * far))
        path.addLine(to: CGPoint(x: t.x - n.dx * far + u.dx * far, y: t.y - n.dy * far + u.dy * far))
        path.closeSubpath()
        return path
    }

    /// The white paper core along the cut on one side.
    func core(_ side: Double, cut: [(p: CGPoint, core: Double)]) -> Path {
        let n = self.n
        let k = side > 0 ? 1.0 : 0.55
        var path = Path()
        path.move(to: cut[0].p)
        for c in cut.dropFirst() { path.addLine(to: c.p) }
        for c in cut.reversed() {
            path.addLine(to: CGPoint(x: c.p.x + n.dx * c.core * k * side, y: c.p.y + n.dy * c.core * k * side))
        }
        path.closeSubpath()
        return path
    }

    /// The shear of one side, then its fall (pivot: the middle of that half of the card).
    func transform(_ side: Double, card: CGSize) -> CGAffineTransform {
        let u = self.u, n = self.n
        let m = side * open
        let c = length + u.dx * start.x + u.dy * start.y
        var t = CGAffineTransform(a: 1 - m * n.dx * u.dx, b: -m * n.dy * u.dx,
                                  c: -m * n.dx * u.dy, d: 1 - m * n.dy * u.dy,
                                  tx: m * n.dx * c, ty: m * n.dy * c)
        if let f = fall {
            let pivot = CGPoint(x: card.width / 2 + n.dx * side * card.width * 0.25,
                                y: card.height / 2 + n.dy * side * card.width * 0.25)
            let out = side * n.dx >= 0 ? 1.0 : -1.0
            let dx = out * 90 * f
            let dy = -150 * f + 1300 * f * f
            let spin = out * (38 * f + 30 * f * f) * .pi / 180
            let fallT = CGAffineTransform(translationX: -pivot.x, y: -pivot.y)
                .concatenating(CGAffineTransform(rotationAngle: spin))
                .concatenating(CGAffineTransform(translationX: pivot.x + dx, y: pivot.y + dy))
            t = t.concatenating(fallT)
        }
        return t
    }

    /// How far the cut can run from `start` along `angle` before it leaves the card.
    static func span(start: CGPoint, angle: Double, card: CGSize) -> Double {
        let u = CGVector(dx: cos(angle * .pi / 180), dy: sin(angle * .pi / 180))
        var best = Double.infinity
        if u.dx > 1e-6 { best = min(best, (card.width - start.x) / u.dx) }
        if u.dx < -1e-6 { best = min(best, -start.x / u.dx) }
        if u.dy > 1e-6 { best = min(best, (card.height - start.y) / u.dy) }
        if u.dy < -1e-6 { best = min(best, -start.y / u.dy) }
        return best.isFinite ? max(0, best) : 0
    }

    /// The opening for a cut of this length: it grows over the first 60 pt.
    static func opening(_ length: Double) -> Double {
        0.07 * TornPhotoDeleteEase.smoothstep(0, 60, length)
    }
}

/// Seeded noise: the same numbers on every platform (32-bit integer hash).
enum TornPhotoDeleteNoise {
    static func hash(_ i: Int, _ seed: Int) -> Double {
        var x = UInt32(truncatingIfNeeded: i &* 374_761_393 &+ seed &* 668_265_263)
        x = (x ^ (x >> 13)) &* 1_274_126_177
        x ^= x >> 16
        return Double(x) / 4_294_967_296
    }

    /// The sideways offset of the cut's i-th point: ±3 pt grain over a ±5 pt wander that turns every 5 points.
    static func offset(_ i: Int, seed: Int) -> Double {
        let j = i / 5
        let f = Double(i % 5) / 5
        let s = f * f * (3 - 2 * f)
        let a = hash(j + 9000, seed), b = hash(j + 9001, seed)
        let wander = (a + (b - a) * s - 0.5) * 10
        return wander + (hash(i, seed) - 0.5) * 6
    }
}

enum TornPhotoDeleteEase {
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    static func inOut(_ x: Double) -> Double {
        let t = clamp(x)
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }
    static func out(_ x: Double) -> Double { 1 - pow(1 - clamp(x), 3) }
    static func outBack(_ x: Double) -> Double {
        let t = clamp(x), c1 = 1.70158, c3 = c1 + 1
        return 1 + c3 * pow(t - 1, 3) + c1 * pow(t - 1, 2)
    }
    /// Critically damped return of an opening to 0 (ω = 16 /s).
    static func heal(_ open: Double, _ t: Double) -> Double {
        guard t > 0 else { return open }
        let w = 16.0
        let v = open * (1 + w * t) * exp(-w * t)
        return v < 0.0005 ? 0 : v
    }
}

/// What the screen shows at one moment.
struct TornPhotoDeleteScene {
    var tear: TornPhotoDeleteTear? = nil
    var cardAlpha = 1.0
    var cardScale = 1.0
    var cardLift = 0.0
    /// The scripted finger: where (screen points), how visible, how pressed.
    var dot: (p: CGPoint, alpha: Double, press: Double)? = nil
    var toast = 0.0
    var undoPress = 0.0
    var hint = 1.0
    var empty = 0.0
}

/*
 autoPlay timeline (loop = 9 s). Card sizes are fractions of the card; lengths are fractions of the span.
   0.00–0.70  rest.
   0.70       the finger lands on the top edge; 0.85–1.75 tear A runs down 42 % of the card (ease in-out),
              the flaps opening a wedge; 1.95 the finger lifts and the tear heals (critically damped, ~0.35 s).
   2.55       the finger lands on the right edge; 2.70–3.55 tear B runs left and a little down, 58 % across;
              3.70 lift, heal.
   4.30       the finger lands on the top edge again; 4.45–5.45 tear C rips all the way to the bottom,
              speeding up; 5.45 it crosses the card: commit (thud). 5.45–6.45 the two halves fall away.
   5.45–5.75  the hint fades; 5.80–6.20 the toast "Photo deleted · Undo" rises (ease-out-back);
   6.00–6.40  "Nothing kept for this day" fades in where the card was.
   6.90       the finger lands on Undo; 7.10–7.28 Undo is pressed (released by 7.43); 7.43–7.68 the toast
              drops away.
   7.50–7.70  the empty note fades; 7.68–8.48 the card comes back (fade, scale 0.92 → 1 with a small
              overshoot, 14 pt lift); 8.20–8.60 the hint returns.
   8.60–9.00  rest, identical to t = 0.
 */
enum TornPhotoDeleteTimeline {
    static let loop = 9.0

    struct Script {
        var start: CGPoint   // fractions of the card
        var angle: Double
        var reach: Double    // fraction of the span
        var seed: Int
        var land: Double, from: Double, to: Double, lift: Double
    }

    static let tearA = Script(start: CGPoint(x: 0.57, y: 0), angle: 96, reach: 0.42, seed: 11,
                              land: 0.70, from: 0.85, to: 1.75, lift: 1.95)
    static let tearB = Script(start: CGPoint(x: 1, y: 0.30), angle: 168, reach: 0.58, seed: 23,
                              land: 2.55, from: 2.70, to: 3.55, lift: 3.70)
    static let tearC = Script(start: CGPoint(x: 0.38, y: 0), angle: 80, reach: 1.06, seed: 37,
                              land: 4.30, from: 4.45, to: 5.45, lift: 5.45)
    static let commit = 5.45
    static let undoLand = 6.90, undoPress = 7.10, undoUp = 7.28
    static let toastIn = 5.80, toastOut = 7.43
    static let restore = 7.68

    typealias E = TornPhotoDeleteEase

    static func scene(_ t: Double, card: CGRect, undo: CGPoint, quiet: Bool = false) -> TornPhotoDeleteScene {
        var s = TornPhotoDeleteScene()
        func local(_ p: CGPoint) -> CGPoint { CGPoint(x: card.minX + p.x, y: card.minY + p.y) }

        func run(_ sc: Script, curve: (Double) -> Double) -> (TornPhotoDeleteTear, Double) {
            let st = CGPoint(x: sc.start.x * card.width, y: sc.start.y * card.height)
            let span = TornPhotoDeleteTear.span(start: st, angle: sc.angle, card: card.size)
            let len = sc.reach * span * curve((t - sc.from) / (sc.to - sc.from))
            let tear = TornPhotoDeleteTear(start: st, angle: sc.angle, length: len,
                                           open: TornPhotoDeleteTear.opening(len), seed: sc.seed)
            return (tear, len)
        }
        func dot(_ sc: Script, _ tear: TornPhotoDeleteTear) {
            guard t >= sc.land, t < sc.lift + 0.2 else { return }
            let a = E.smoothstep(sc.land, sc.land + 0.15, t) * (1 - E.smoothstep(sc.lift, sc.lift + 0.2, t))
            let press = E.smoothstep(sc.land, sc.land + 0.15, t)
            let u = tear.u
            let p = tear.length > 0 ? tear.tip : CGPoint(x: tear.start.x - u.dx * 6, y: tear.start.y - u.dy * 6)
            s.dot = (local(p), a, press)
        }

        if t < tearB.land {
            let (tear, _) = run(tearA, curve: E.inOut)
            var tr = tear
            if t > tearA.lift { tr.open = E.heal(TornPhotoDeleteTear.opening(tear.length), t - tearA.lift) }
            if t >= tearA.from && tr.open > 0 { s.tear = tr }
            dot(tearA, tear)
        } else if t < tearC.land {
            let (tear, _) = run(tearB, curve: E.inOut)
            var tr = tear
            if t > tearB.lift { tr.open = E.heal(TornPhotoDeleteTear.opening(tear.length), t - tearB.lift) }
            if t >= tearB.from && tr.open > 0 { s.tear = tr }
            dot(tearB, tear)
        } else if t < restore {
            // Tear C speeds up like a real rip: 0.35 x + 0.65 x².
            var (tear, _) = run(tearC, curve: { x in let c = E.clamp(x); return 0.35 * c + 0.65 * c * c })
            if t >= commit { tear.fall = t - commit }
            if t >= tearC.from && (tear.fall ?? 0) < 1.2 { s.tear = tear }
            if t >= commit + 1.2 { s.cardAlpha = 0 }
            dot(tearC, tear)
        }

        // Toast and undo.
        if t >= toastIn && t < toastOut + 0.3 {
            s.toast = E.outBack((t - toastIn) / 0.4) * (1 - E.smoothstep(toastOut, toastOut + 0.25, t))
        }
        if t >= undoPress && t < undoUp + 0.15 {
            s.undoPress = E.smoothstep(undoPress, undoPress + 0.08, t) * (1 - E.smoothstep(undoUp, undoUp + 0.15, t))
        }
        if !quiet && t >= undoLand && t < undoUp + 0.25 {
            let a = E.smoothstep(undoLand, undoLand + 0.15, t) * (1 - E.smoothstep(undoUp + 0.05, undoUp + 0.25, t))
            s.dot = (undo, a, E.smoothstep(undoLand, undoLand + 0.15, t))
        }
        s.empty = E.smoothstep(6.0, 6.4, t) * (1 - E.smoothstep(7.5, 7.7, t))
        s.hint = 1 - E.smoothstep(5.45, 5.75, t) + E.smoothstep(8.2, 8.6, t)
        if t >= restore {
            let r = t - restore
            s.cardAlpha = E.out(r / 0.4)
            s.cardScale = 0.92 + 0.08 * E.outBack(r / 0.8)
            s.cardLift = 14 * (1 - E.out(r / 0.8))
        }
        return s
    }
}
