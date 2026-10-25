import SwiftUI
import UIKit

/*
 Iron Filings Magnet — "Field Notes": 900 iron filings on a sheet of graph paper, a bar magnet
 under it. The filings line up along the field and crowd at the poles; move the magnet and they
 turn after it, the far ones later. A second magnet waits at the right edge.

 Rendering: the paper is a Canvas drawn once; per frame one Canvas draws the two magnets (one
 pre-rendered image each), then 900 filing shadows and 900 filings (pre-rendered sprites placed
 with one affine transform and an opacity each; the metal sprite comes in six darkness steps for
 the tufts, where React Native modulates a colour per filing), then the readings. Angles and
 positions are computed per frame: in the loop from the magnets' path a little in the past
 (16 delay buckets, 30 – 520 ms by distance), under the finger with one damped spring per filing
 (stiffer near the magnet).

 Touch: drag either magnet (pull B in by its tab on the right); it follows the finger on a spring
 (ζ 0.8). Tap a magnet to turn it end for end (north and south swap: attraction instead of
 repulsion). Haptics: a tick on pick-up and on a flip, a light tap when two poles come within
 70 pt. 2 s after the last touch both magnets ease home (≈ 0.6 s) and the loop restarts.
 Reduced motion: no loop; filings settle at once instead of turning.
 */
struct IronFilingsMagnet: View {
    /// Sweeps magnet A in a figure eight, brings magnet B in against it and out again, on a 9 s loop.
    var autoPlay = false

    static let loop = IronFilingsMagnetMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = IronFilingsMagnetEngine()
    /// Keeps the timeline running outside autoPlay while something moves.
    @State private var awake = true
    @State private var touch: (start: Double, moved: Bool)?

    private typealias Art = IronFilingsMagnetArt

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, top: insets.top, bottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, top: Double, bottom: Double) -> some View {
        let g = IronFilingsMagnetGeo(w: size.width, h: size.height, top: top, bottom: bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let running = auto || awake
        let sprites = Art.sprites(g, scale: min(3, max(1, displayScale)))
        return ZStack(alignment: .topLeading) {
            Canvas { c, _ in
                Art.drawPaper(c, g)
            }
            .frame(width: size.width, height: size.height)

            TimelineView(.animation(paused: !running)) { timeline in
                let n = timeline.date.timeIntervalSinceReferenceDate
                let frame = engine.step(n, g: g, auto: auto, reduced: reduced)
                Canvas { c, _ in
                    Self.draw(c, g, frame, sprites)
                }
                .frame(width: size.width, height: size.height)
                .onChange(of: frame.idle, initial: true) { _, idle in
                    if idle && !auto { awake = false }
                }
            }
            .allowsHitTesting(false)

            Color.clear
                .contentShape(Rectangle())
                .frame(width: size.width, height: size.height)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            awake = true
                            let now = Date().timeIntervalSinceReferenceDate
                            var t = touch ?? (start: now, moved: false)
                            if !t.moved && hypot(v.translation.width, v.translation.height) >= 4 {
                                t.moved = true
                                engine.dragBegan(v.startLocation, g: g)
                            }
                            if t.moved { engine.dragMoved(v.location, g: g) }
                            touch = t
                        }
                        .onEnded { v in
                            let now = Date().timeIntervalSinceReferenceDate
                            if let t = touch {
                                if t.moved {
                                    engine.dragEnded(auto: auto)
                                } else if now - t.start <= 0.26 {
                                    engine.tap(v.location, g: g, auto: auto)
                                }
                            }
                            touch = nil
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("Iron filings on paper over a bar magnet. Drag a magnet to move the field, tap it to flip its poles.")
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private static func draw(_ c: GraphicsContext, _ g: IronFilingsMagnetGeo, _ f: IronFilingsMagnetFrame, _ sp: IronFilingsMagnetArt.Sprites) {
        guard let fl = f.filings else { return }
        Art.drawUnder(c, g, f.mag, sp)
        let shadow = c.resolve(sp.shadow)
        let metal = sp.metal.map { c.resolve($0) }
        let box = CGRect(x: 0, y: 0, width: Art.spriteW, height: Art.spriteH)
        var l = c
        let base = l.transform
        let steps = Double(Art.standSteps - 1)
        // Shadows: a standing filing casts a longer, offset contact shadow.
        for i in 0..<fl.x.count {
            let st = f.stand[i]
            let sc = (fl.len[i] / 48) * (0.8 + 0.2 * f.pow[i] + 0.8 * st)
            let cs = cos(f.ang[i]) * sc
            let sn = sin(f.ang[i]) * sc
            let x = fl.x[i] + f.ox[i] + 0.7 + 2.4 * st
            let y = fl.y[i] + f.oy[i] + 1.2 + 3.2 * st
            l.transform = CGAffineTransform(a: cs, b: sn, c: -sn, d: cs, tx: x - (cs * 24 - sn * 6), ty: y - (sn * 24 + cs * 6)).concatenating(base)
            l.opacity = (0.55 + 0.45 * st) * (0.5 + 0.5 * max(f.pow[i], st))
            l.draw(shadow, in: box)
        }
        // Metal: tuft filings longer and darker, weak-field ones a little shorter and fainter.
        for i in 0..<fl.x.count {
            let st = f.stand[i]
            let sc = (fl.len[i] / 48) * (0.8 + 0.2 * f.pow[i] + 0.5 * st)
            let cs = cos(f.ang[i]) * sc
            let sn = sin(f.ang[i]) * sc
            let x = fl.x[i] + f.ox[i]
            let y = fl.y[i] + f.oy[i]
            l.transform = CGAffineTransform(a: cs, b: sn, c: -sn, d: cs, tx: x - (cs * 24 - sn * 6), ty: y - (sn * 24 + cs * 6)).concatenating(base)
            l.opacity = 0.5 + 0.5 * max(f.pow[i], st)
            let k = Int((min(1, max(0, st)) * steps).rounded())
            l.draw(metal[k], in: box)
        }
        let onPaper = f.mag[3] < g.paperX + g.paperW - 10 ? 4 : 2
        Art.drawReadings(c, g, millitesla: f.centre, poles: onPaper)
    }
}

/// What one frame draws.
struct IronFilingsMagnetFrame {
    var filings: IronFilingsMagnetMotion.Filings?
    var ang: [Double]
    var ox: [Double]
    var oy: [Double]
    var stand: [Double]
    var pow: [Double]
    var mag: [Double]
    var centre: Double
    var idle: Bool
}

/// The clock, the script and the hand-driven field, stepped once a frame like the RN frame callback.
@MainActor
final class IronFilingsMagnetEngine {
    private typealias M = IronFilingsMagnetMotion
    private static let buckets = 16
    private static let magK = 300.0
    private static let magC = 2 * 0.8 * sqrt(magK)

    private var geo: IronFilingsMagnetGeo?
    private var filings: M.Filings?
    private var prev: Double?
    private var start: Double?
    private var resumeAt: Double?
    private var ang: [Double] = []
    private var vel: [Double] = []
    private var ox: [Double] = []
    private var oy: [Double] = []
    /// How much a filing stands up in a pole's tuft (0 … 1), and how strong its field is (0 … 1).
    private var stand: [Double] = []
    private var pow: [Double] = []
    /// Magnets now, their velocities, and where the finger wants them.
    private var mag: [Double] = [0, 0, 0, 0, 0, Double.pi]
    private var magV: [Double] = [0, 0, 0, 0, 0, 0]
    private var target: [Double] = [0, 0, 0, 0, 0, Double.pi]
    /// Which magnet the finger holds (−1: none) and the grab offset.
    private var held = -1
    private var grab = (0.0, 0.0)
    private var near = false
    private var tension = 0.0

    private let tick = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)

    private var clock: Double { Date().timeIntervalSinceReferenceDate }

    /// Angle, pull and tuft of filing i in a field of magnets m.
    private func aim(_ i: Int, _ m: [Double], _ g: IronFilingsMagnetGeo, _ fl: M.Filings) -> (Double, Double, Double, Double, Double) {
        let x = fl.x[i]
        let y = fl.y[i]
        let soft = 64 * g.u * g.u
        let (bx, by) = M.fieldAt(x, y, m, g.half, soft)
        let strength = hypot(bx, by) * g.half * g.half
        let k = min(1, max(0, (strength - 0.003) / 0.03))
        let align = k * k * (3 - 2 * k)
        let a0 = fl.a0[i]
        let a = a0 + M.axial(atan2(by, bx) - a0) * align
        // Tufts: filings within ~160 pt of a pole are drawn in towards it (up to 26 pt, never past
        // it), so the ends crowd; the closest stand up.
        let (dx, dy, d) = M.nearestPole(x, y, m, g.half)
        let reach = max(0, 1 - d / (160 * g.u))
        let move = min(26 * g.u * Foundation.pow(reach, 1.6) * min(1, strength * 4), d * 0.82)
        let pull = move / max(d, 1)
        let nr = max(0, 1 - (d - move) / (44 * g.u))
        return (a, dx * pull, dy * pull, nr * nr * align, align)
    }

    private func centreField(_ m: [Double], _ g: IronFilingsMagnetGeo) -> Double {
        let (bx, by) = M.fieldAt(g.cx, g.cy, m, g.half, 64 * g.u * g.u)
        return hypot(bx, by) * g.half * g.half * 19
    }

    private func setUp(_ g: IronFilingsMagnetGeo, n: Double, auto: Bool) {
        geo = g
        let fl = M.scatter(g)
        filings = fl
        let rest = M.magnetsAt(g, 0)
        let count = fl.x.count
        ang = Array(repeating: 0, count: count)
        vel = Array(repeating: 0, count: count)
        ox = Array(repeating: 0, count: count)
        oy = Array(repeating: 0, count: count)
        stand = Array(repeating: 0, count: count)
        pow = Array(repeating: 0, count: count)
        mag = rest
        magV = [0, 0, 0, 0, 0, 0]
        target = rest
        held = -1
        near = false
        tension = 0
        for i in 0..<count {
            let o = aim(i, rest, g, fl)
            ang[i] = o.0
            ox[i] = o.1
            oy[i] = o.2
            stand[i] = o.3
            pow[i] = o.4
        }
        start = auto ? n : nil
        resumeAt = nil
    }

    func step(_ n: Double, g: IronFilingsMagnetGeo, auto: Bool, reduced: Bool) -> IronFilingsMagnetFrame {
        if geo != g || filings == nil {
            setUp(g, n: n, auto: auto)
            prev = nil
        }
        guard let fl = filings else {
            return IronFilingsMagnetFrame(filings: nil, ang: [], ox: [], oy: [], stand: [], pow: [], mag: mag, centre: 0, idle: true)
        }
        let dt = prev.map { min(max(0, n - $0), 1.0 / 30) } ?? 0
        prev = n
        let count = fl.x.count

        if let st = start, n >= st {
            // The loop: each filing reads the magnets as they were a moment ago.
            let t = (n - st).truncatingRemainder(dividingBy: M.loop)
            let nowM = M.magnetsAt(g, t)
            var past: [[Double]] = []
            for b in 0..<Self.buckets {
                var tb = (t - 0.03 - Double(b) * 0.0327).truncatingRemainder(dividingBy: M.loop)
                if tb < 0 { tb += M.loop }
                past.append(M.magnetsAt(g, tb))
            }
            for i in 0..<count {
                let d = hypot(fl.x[i] - nowM[0], fl.y[i] - nowM[1])
                let b = min(Self.buckets - 1, Int(floor(d / (520 * g.u) * Double(Self.buckets))))
                let o = aim(i, past[b], g, fl)
                ang[i] = o.0
                ox[i] = o.1
                oy[i] = o.2
                stand[i] = o.3
                pow[i] = o.4
                vel[i] = 0
            }
            mag = nowM
            target = nowM
            magV = [0, 0, 0, 0, 0, 0]
            return frame(g, idle: false)
        }

        // Under the finger: magnets on springs, each filing on its own spring.
        var moving = 0.0
        for k in 0..<6 {
            let a = Self.magK * (target[k] - mag[k]) - Self.magC * magV[k]
            magV[k] += a * dt
            mag[k] += magV[k] * dt
            moving += abs(magV[k]) + abs(target[k] - mag[k])
        }
        if reduced { mag = target }
        let busy = moving > 0.05 || tension > 0.002 || held >= 0
        if busy || dt == 0 {
            var total = 0.0
            let f = 1 - exp(-dt / 0.12)
            for i in 0..<count {
                let o = aim(i, mag, g, fl)
                if reduced {
                    ang[i] = o.0
                    ox[i] = o.1
                    oy[i] = o.2
                    stand[i] = o.3
                    pow[i] = o.4
                    continue
                }
                let d = hypot(fl.x[i] - mag[0], fl.y[i] - mag[1])
                let q = 1 + d / (110 * g.u)
                let k = 340 / (q * q)
                let c = 2 * 0.7 * sqrt(k)
                let err = M.axial(o.0 - ang[i])
                vel[i] += (k * err - c * vel[i]) * dt
                ang[i] += vel[i] * dt
                ox[i] += (o.1 - ox[i]) * f
                oy[i] += (o.2 - oy[i]) * f
                stand[i] += (o.3 - stand[i]) * f
                pow[i] += (o.4 - pow[i]) * f
                total += abs(err) + abs(vel[i]) * 0.05
            }
            tension = total / Double(max(1, count))
        }
        // Two poles meeting: one light tap.
        let nn = hypot(
            mag[0] + cos(mag[2]) * g.half - (mag[3] + cos(mag[5]) * g.half),
            mag[1] + sin(mag[2]) * g.half - (mag[4] + sin(mag[5]) * g.half)
        )
        let close = nn < 70 * g.u
        if close != near {
            near = close
            if close && held >= 0 { light.impactOccurred() }
        }
        if let r = resumeAt, n >= r, held < 0 {
            resumeAt = nil
            target = M.magnetsAt(g, 0)
            start = n + 0.9
        }
        let idle = !busy && resumeAt == nil && start == nil
        return frame(g, idle: idle)
    }

    private func frame(_ g: IronFilingsMagnetGeo, idle: Bool) -> IronFilingsMagnetFrame {
        IronFilingsMagnetFrame(
            filings: filings,
            ang: ang,
            ox: ox,
            oy: oy,
            stand: stand,
            pow: pow,
            mag: mag,
            centre: centreField(mag, g),
            idle: idle
        )
    }

    private func takeOver() {
        let n = clock
        if let st = start, n >= st {
            target = mag
            magV = [0, 0, 0, 0, 0, 0]
        }
        start = nil
        resumeAt = nil
    }

    /// The magnet under (x, y): 0, 1 (B also by its tab at the right edge), or −1.
    private func pick(_ x: Double, _ y: Double, _ g: IronFilingsMagnetGeo) -> Int {
        for k in 0..<2 {
            let dx = x - mag[k * 3]
            let dy = y - mag[k * 3 + 1]
            let a = mag[k * 3 + 2]
            let along = abs(dx * cos(a) + dy * sin(a))
            let across = abs(-dx * sin(a) + dy * cos(a))
            if along < g.half + 26 * g.u && across < g.thick + 22 * g.u { return k }
        }
        if x > g.w - 56 && abs(y - mag[4]) < 44 { return 1 }
        return -1
    }

    func dragBegan(_ p: CGPoint, g: IronFilingsMagnetGeo) {
        takeOver()
        let k = pick(p.x, p.y, g)
        held = k
        if k < 0 { return }
        grab = (mag[k * 3] - p.x, mag[k * 3 + 1] - p.y)
        if k == 1 && mag[3] > g.w - 20 { grab = (-16, 0) }
        tick.selectionChanged()
        light.prepare()
    }

    func dragMoved(_ p: CGPoint, g: IronFilingsMagnetGeo) {
        guard held >= 0 else { return }
        let k = held
        let minY = g.fieldTop + 10
        let maxY = g.fieldBottom - 10
        target[k * 3] = max(g.paperX - 20, min(g.w + 30 * g.u, p.x + grab.0))
        target[k * 3 + 1] = max(minY, min(maxY, p.y + grab.1))
    }

    func dragEnded(auto: Bool) {
        held = -1
        if auto { resumeAt = clock + 2 }
    }

    func tap(_ p: CGPoint, g: IronFilingsMagnetGeo, auto: Bool) {
        takeOver()
        let k = pick(p.x, p.y, g)
        if k >= 0 {
            target[k * 3 + 2] += Double.pi
            tick.selectionChanged()
        }
        if auto { resumeAt = clock + 2 }
    }
}
