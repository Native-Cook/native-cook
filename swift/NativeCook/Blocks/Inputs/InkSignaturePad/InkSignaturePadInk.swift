import SwiftUI

/// The ink: how samples are laid down, cut into pieces along their curves and drawn as fountain-pen
/// ink over the paper, with the wiper and the nib.
///
/// Ink is kept as one flat list of samples, `stride` numbers each: x, y (pt in the pad), the time it
/// was laid down (ms on the pad's clock), the nib's width there (pt) and the stroke it belongs to.
enum InkSignaturePadInk {
    static let stride = 5

    // The nib: slow ink is wide, fast ink thin. width = MIN + (MAX − MIN)·e^(−speed / FALLOFF), speed
    // in pt/s, and each sample moves EASE of the way from the last width to that, so it swells and
    // thins smoothly instead of jumping with every jitter of the finger.
    static let nibMax = 7.0
    static let nibMin = 1.1
    static let nibFalloff = 500.0
    static let nibEase = 0.3
    // The width a stroke lands with, as a share of nibMax: the pen pools a little where it touches.
    static let nibLanding = 0.82
    // Samples closer than this to the last one (pt) are dropped.
    static let minStep = 0.8
    // Each span between two samples is drawn through as a Catmull-Rom curve in pieces about SUBSTEP pt
    // long, at most MAX_PIECES of them.
    static let substep = 2.0
    static let maxPieces = 8

    // Wet ink is a brighter, glossier blue that darkens to blue-black over dryMs, and carries a thin
    // highlight on its upper left that fades as it dries. Wetness is drawn in wetLevels steps.
    static let dryMs = 1100.0
    static let wetLevels = 8
    static let inkWet: [Double] = [0.16, 0.25, 0.6]
    static let inkDry: [Double] = [0.1, 0.13, 0.32]
    static let sheen: [Double] = [0.72, 0.8, 1]
    static let sheenMix = 0.62
    static let sheenWidth = 0.3
    // While a signature replays, the old ink shows as a pale ghost under the new.
    static let ghostAlpha = 0.16

    // The wiper Clear sweeps across: a walnut block faced with felt on its leading edge, as tall as
    // the pad less wiperInset at each end, with a brass knob to hold it by. It travels from off the
    // left edge to off the right edge.
    static let wiperWidth = 46.0
    static let wiperInset = 22.0
    static let felt = 12.0
    static let knob = 11.0
    static let wiperLean = 7.0
    // Ink dust brushed off by the felt: crumbs specks, each living crumbLife of the sweep.
    static let crumbs = 34
    static let crumbLife = 0.32

    // The fountain pen's nib, drawn while the pad writes or replays by itself, leaning nibAngle°.
    static let nibAngle = 38.0

    static func color(_ c: [Double], _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: c[0], green: c[1], blue: c[2], opacity: alpha)
    }

    static func mixed(_ a: [Double], _ b: [Double], _ k: Double) -> [Double] {
        [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k]
    }

    static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }

    static func nibWidth(_ speed: Double) -> Double {
        nibMin + (nibMax - nibMin) * exp(-speed / nibFalloff)
    }

    static func catmull(_ p0: Double, _ p1: Double, _ p2: Double, _ p3: Double, _ u: Double) -> Double {
        0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u)
    }

    // MARK: - Samples

    /// Appends a sample to `ink` and says whether it was kept. `speed` is the finger's speed in pt/s;
    /// leave it below 0 to take it from the last sample's place and time.
    @discardableResult
    static func addSample(_ ink: inout [Double], x: Double, y: Double, t: Double, stroke: Double, starts: Bool, speed: Double) -> Bool {
        let n = ink.count
        if starts || n < stride || ink[n - 1] != stroke {
            ink.append(contentsOf: [x, y, t, nibMax * nibLanding, stroke])
            return true
        }
        let d = hypot(x - ink[n - 5], y - ink[n - 4])
        if d < minStep { return false }
        let v = speed >= 0 ? speed : d / max((t - ink[n - 3]) / 1000, 1.0 / 240)
        let last = ink[n - 2]
        ink.append(contentsOf: [x, y, t, last + (nibWidth(v) - last) * nibEase, stroke])
        return true
    }

    /// A copy of `ink` for replaying from `start` (ms): the same samples, the same rhythm, but with the
    /// pauses between strokes cut to `maxGap` ms.
    static func retime(_ ink: [Double], start: Double, maxGap: Double) -> [Double] {
        var out: [Double] = []
        out.reserveCapacity(ink.count)
        var shift = start - ink[2]
        var i = 0
        while i < ink.count {
            if i > 0 && ink[i + 4] != ink[i - 1] {
                let gap = ink[i + 2] - ink[i - 3]
                if gap > maxGap { shift -= gap - maxGap }
            }
            out.append(contentsOf: [ink[i], ink[i + 1], ink[i + 2] + shift, ink[i + 3], ink[i + 4]])
            i += stride
        }
        return out
    }

    /// Where the pen is at `time` along `ink`, given that the first `cursor` samples are down: on the
    /// last of them, or, between two strokes, gliding from the end of one to the start of the next and
    /// lifted (lift 0…1) on the way.
    static func penAt(_ ink: [Double], cursor: Int, time: Double) -> (x: Double, y: Double, lift: Double) {
        if cursor <= 0 { return (ink.first ?? 0, ink.count > 1 ? ink[1] : 0, 1) }
        let last = (cursor - 1) * stride
        let next = cursor * stride
        if next >= ink.count || ink[next + 4] == ink[last + 4] { return (ink[last], ink[last + 1], 0) }
        let f = clamp01((time - ink[last + 2]) / max(1, ink[next + 2] - ink[last + 2]))
        let e = f * f * (3 - 2 * f)
        return (
            ink[last] + (ink[next] - ink[last]) * e,
            ink[last + 1] + (ink[next + 1] - ink[last + 1]) * e,
            sin(.pi * f)
        )
    }

    /// Cuts the ink drawn up to `upTo` (ms) into short straight pieces along its curves: x0, y0, x1,
    /// y1, width, time for each.
    static func pieces(_ ink: [Double], upTo: Double) -> [Double] {
        var out: [Double] = []
        out.reserveCapacity(ink.count * 4)
        var a = 0
        while a < ink.count {
            // The stroke runs from sample a to sample b (indices into ink).
            var b = a
            while b + stride < ink.count && ink[b + stride + 4] == ink[a + 4] { b += stride }
            if ink[a + 2] <= upTo {
                if a == b {
                    out.append(contentsOf: [ink[a], ink[a + 1], ink[a], ink[a + 1], ink[a + 3], ink[a + 2]])
                }
                var i = a
                while i < b {
                    let j = i + stride
                    if ink[j + 2] > upTo && ink[i + 2] > upTo { break }
                    let h = max(a, i - stride)
                    let k = min(b, j + stride)
                    let reach = ink[j + 2] <= upTo ? 1 : clamp01((upTo - ink[i + 2]) / max(1, ink[j + 2] - ink[i + 2]))
                    // Capped before Int(): a non-finite or huge span must not trap.
                    let span = hypot(ink[j] - ink[i], ink[j + 1] - ink[i + 1])
                    let steps = span.isFinite ? max(1, Int(min(Double(maxPieces), ceil(span / substep)))) : 1
                    var px = ink[i]
                    var py = ink[i + 1]
                    for s in 1...steps {
                        var u = Double(s) / Double(steps)
                        if u > reach { u = reach }
                        let qx = catmull(ink[h], ink[i], ink[j], ink[k], u)
                        let qy = catmull(ink[h + 1], ink[i + 1], ink[j + 1], ink[k + 1], u)
                        out.append(contentsOf: [px, py, qx, qy, ink[i + 3] + (ink[j + 3] - ink[i + 3]) * u, ink[i + 2] + (ink[j + 2] - ink[i + 2]) * u])
                        px = qx
                        py = qy
                        if u >= reach { break }
                    }
                    if reach < 1 { break }
                    i += stride
                }
            }
            a = b + stride
        }
        return out
    }

    // MARK: - Outlines

    /// Adds the outline of a round-ended piece of ink from (x0, y0) to (x1, y1), `r` wide on each side:
    /// two half discs joined by straight sides, always wound the same way so that overlapping pieces
    /// fill as one. A piece with no length is a disc.
    static func addPiece(_ path: inout Path, _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ r: Double) {
        let half = r < 1.2 ? 5 : r < 2.5 ? 7 : 9
        let step = Double.pi / Double(half)
        let dx = x1 - x0
        let dy = y1 - y0
        let length = hypot(dx, dy)
        if length < 1e-4 {
            path.move(to: CGPoint(x: x0 + r, y: y0))
            for k in 1..<(half * 2) {
                let a = -Double(k) * step
                path.addLine(to: CGPoint(x: x0 + r * cos(a), y: y0 + r * sin(a)))
            }
            path.closeSubpath()
            return
        }
        let nx = -dy / length
        let ny = dx / length
        let a0 = atan2(ny, nx)
        path.move(to: CGPoint(x: x0 + nx * r, y: y0 + ny * r))
        path.addLine(to: CGPoint(x: x1 + nx * r, y: y1 + ny * r))
        for k in 1...half {
            let a = a0 - Double(k) * step
            path.addLine(to: CGPoint(x: x1 + r * cos(a), y: y1 + r * sin(a)))
        }
        path.addLine(to: CGPoint(x: x0 - nx * r, y: y0 - ny * r))
        for k in 1..<half {
            let a = a0 - .pi - Double(k) * step
            path.addLine(to: CGPoint(x: x0 + r * cos(a), y: y0 + r * sin(a)))
        }
        path.closeSubpath()
    }

    /// The whole of a cut as one outline.
    static func outline(_ cut: [Double]) -> Path {
        var path = Path()
        var i = 0
        while i < cut.count {
            addPiece(&path, cut[i], cut[i + 1], cut[i + 2], cut[i + 3], cut[i + 4] / 2)
            i += 6
        }
        return path
    }

    // MARK: - Drawing

    struct Frame {
        var ink: [Double]
        /// While replaying: the retimed copy being drawn, else empty.
        var replay: [Double]
        /// The pad's clock, ms.
        var now: Double
        var ghost: Double
        /// 0…1 through a sweep of the wiper; 0 when none.
        var sweep: Double
        /// The whole ink's opacity: reduced motion fades it out instead of sweeping it away.
        var alpha: Double
        var width: Double
        var height: Double
        var pen: (x: Double, y: Double, lift: Double, alpha: Double)
    }

    /// Everything drawn over the paper: the ink (multiplied into it), the wiper and the pen.
    static func paint(_ context: GraphicsContext, _ f: Frame) {
        // The ink is drawn opaque into a layer that is multiplied into the paper, so the paper's grain
        // shows through it and pieces that overlap never darken each other.
        var ink = context
        ink.blendMode = .multiply
        ink.opacity = f.alpha
        ink.drawLayer { layer in
            if f.sweep > 0 {
                layer.clip(to: Path(CGRect(x: wiperEdge(f.sweep, width: f.width) - felt / 2, y: -50, width: f.width + 100, height: f.height + 100)))
            }
            if f.ghost > 0.002 && !f.ink.isEmpty {
                layer.fill(outline(pieces(f.ink, upTo: .infinity)), with: .color(color(inkDry, f.ghost * ghostAlpha)))
            }
            if f.replay.isEmpty {
                drawPieces(layer, pieces(f.ink, upTo: .infinity), now: f.now)
            } else {
                drawPieces(layer, pieces(f.replay, upTo: f.now), now: f.now)
            }
        }
        if f.sweep > 0 && f.sweep < 1 { drawWiper(context, sweep: f.sweep, width: f.width, height: f.height) }
        drawNib(context, x: f.pen.x, y: f.pen.y, lift: f.pen.lift, alpha: f.pen.alpha)
    }

    /// The pieces in their colours, wettest last, then their highlights on top, so no piece hides
    /// another's shine.
    private static func drawPieces(_ context: GraphicsContext, _ cut: [Double], now: Double) {
        var inks = [Path](repeating: Path(), count: wetLevels)
        var sheens = [Path](repeating: Path(), count: wetLevels)
        var i = 0
        while i < cut.count {
            let wet = clamp01(1 - (now - cut[i + 5]) / dryMs)
            let level = Int((wet * Double(wetLevels - 1)).rounded())
            let w = cut[i + 4]
            addPiece(&inks[level], cut[i], cut[i + 1], cut[i + 2], cut[i + 3], w / 2)
            if level > 0 {
                let dx = -w * 0.17
                let dy = -w * 0.2
                addPiece(&sheens[level], cut[i] + dx, cut[i + 1] + dy, cut[i + 2] + dx, cut[i + 3] + dy, w * sheenWidth / 2)
            }
            i += 6
        }
        for level in 0..<wetLevels where !inks[level].isEmpty {
            let wet = Double(level) / Double(wetLevels - 1)
            context.fill(inks[level], with: .color(color(mixed(inkDry, inkWet, wet))))
        }
        for level in 1..<wetLevels where !sheens[level].isEmpty {
            let wet = Double(level) / Double(wetLevels - 1)
            let tone = mixed(inkDry, inkWet, wet)
            context.fill(sheens[level], with: .color(color(mixed(tone, sheen, sheenMix * wet))))
        }
    }

    private static func hash(_ n: Double) -> Double {
        let s = sin(n * 127.1 + 311.7) * 43758.5453
        return s - floor(s)
    }

    /// Where the felt edge of the wiper is at a point of the sweep (0…1).
    static func wiperEdge(_ sweep: Double, width: Double) -> Double {
        -wiperWidth + (width + 2 * wiperWidth + felt) * sweep
    }

    private static func rounded(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r, style: .circular)
    }

    private static func drawWiper(_ context: GraphicsContext, sweep: Double, width: Double, height: Double) {
        let edge = wiperEdge(sweep, width: width)
        let top = wiperInset
        let h = height - 2 * wiperInset
        // Dust first, behind the block.
        for i in 0..<crumbs {
            let born = (Double(i) + 0.5) / Double(crumbs)
            let age = (sweep - born) / crumbLife
            if age <= 0 || age >= 1 { continue }
            let bornX = wiperEdge(born, width: width)
            let y = top + 12 + hash(Double(i)) * (h - 24) + age * age * 22
            let x = bornX - age * (10 + hash(Double(i + 7)) * 18)
            let r = 0.7 + hash(Double(i + 3)) * 1.1
            context.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(color(inkDry, 0.55 * (1 - age))))
        }
        // The block leans into its travel, most in the middle of the sweep.
        var block = context
        block.translateBy(x: edge, y: top + h / 2)
        block.rotate(by: .degrees(wiperLean * sin(.pi * sweep)))
        block.translateBy(x: -edge, y: -(top + h / 2))
        let left = edge - wiperWidth
        block.drawLayer { shadow in
            shadow.addFilter(.blur(radius: 7))
            shadow.fill(rounded(left - 4, top + 8, wiperWidth + 4, h, 14), with: .color(Color(.sRGB, red: 0.1, green: 0.06, blue: 0.03, opacity: 0.32)))
        }
        block.fill(
            rounded(left, top, wiperWidth, h, 14),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x6b4a33), location: 0),
                    .init(color: Color(hex: 0x4a3121), location: 0.55),
                    .init(color: Color(hex: 0x2d1d13), location: 1),
                ]),
                startPoint: CGPoint(x: left, y: 0),
                endPoint: CGPoint(x: edge, y: 0)
            )
        )
        // The grain: a few darker lines along the block.
        var grain = Path()
        for g in 0..<4 {
            let gx = left + 6 + Double(g) * 7 + hash(Double(g + 11)) * 3
            grain.move(to: CGPoint(x: gx, y: top + 10))
            grain.addLine(to: CGPoint(x: gx + hash(Double(g + 5)) * 2 - 1, y: top + h - 10))
        }
        block.stroke(grain, with: .color(Color(.sRGB, red: 0.12, green: 0.07, blue: 0.04, opacity: 0.35)), lineWidth: 0.8)
        // A brass knob in the middle, where a hand would hold it.
        let knobX = left + (wiperWidth - felt) / 2
        let knobY = top + h / 2
        block.fill(
            Path(ellipseIn: CGRect(x: knobX - knob, y: knobY - knob, width: knob * 2, height: knob * 2)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0xfbe7b5), location: 0),
                    .init(color: Color(hex: 0xc99a46), location: 0.5),
                    .init(color: Color(hex: 0x6d5020), location: 1),
                ]),
                center: CGPoint(x: knobX - 3, y: knobY - 4),
                startRadius: 0,
                endRadius: knob * 1.3
            )
        )
        block.fill(rounded(edge - felt, top + 2, felt, h - 4, 4), with: .color(Color(hex: 0xd9d0bf)))
        // The felt picks up ink as it goes.
        block.fill(rounded(edge - felt, top + 2, 3, h - 4, 1.5), with: .color(color(inkDry, 0.45 * clamp01(sweep * 1.6))))
    }

    private static let nibShape: Path = {
        var p = Path()
        p.move(to: .zero)
        p.addCurve(to: CGPoint(x: -8, y: -24), control1: CGPoint(x: -3, y: -6), control2: CGPoint(x: -8, y: -15))
        p.addLine(to: CGPoint(x: -8, y: -38))
        p.addLine(to: CGPoint(x: 8, y: -38))
        p.addLine(to: CGPoint(x: 8, y: -24))
        p.addCurve(to: .zero, control1: CGPoint(x: 8, y: -15), control2: CGPoint(x: 3, y: -6))
        p.closeSubpath()
        return p
    }()

    /// A gold nib with its slit and breather hole, its point on the ink, lifting on a growing shadow.
    private static func drawNib(_ context: GraphicsContext, x: Double, y: Double, lift: Double, alpha: Double) {
        if alpha <= 0.01 { return }
        let up = lift * 7
        var nib = context
        nib.translateBy(x: x, y: y)
        nib.rotate(by: .degrees(nibAngle))
        nib.drawLayer { shadow in
            shadow.addFilter(.blur(radius: 2.5 + up * 0.5))
            shadow.translateBy(x: 3 + up, y: 2 + up)
            shadow.fill(nibShape, with: .color(Color(.sRGB, red: 0.12, green: 0.08, blue: 0.02, opacity: 0.28 * alpha)))
        }
        nib.translateBy(x: -up * 0.4, y: -up)
        var gold = nib
        gold.opacity = alpha
        gold.fill(
            nibShape,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x8a6a2c), location: 0),
                    .init(color: Color(hex: 0xf4dc9a), location: 0.35),
                    .init(color: Color(hex: 0xc99a46), location: 0.65),
                    .init(color: Color(hex: 0x6d5020), location: 1),
                ]),
                startPoint: CGPoint(x: -8, y: 0),
                endPoint: CGPoint(x: 8, y: 0)
            )
        )
        let line = Color(.sRGB, red: 0.25, green: 0.17, blue: 0.05, opacity: alpha)
        var slit = Path()
        slit.move(to: CGPoint(x: 0, y: -1.5))
        slit.addLine(to: CGPoint(x: 0, y: -17))
        nib.stroke(slit, with: .color(line), lineWidth: 0.9)
        nib.fill(Path(ellipseIn: CGRect(x: -1.8, y: -18.5 - 1.8, width: 3.6, height: 3.6)), with: .color(line))
        // The ink on the point.
        nib.fill(Path(ellipseIn: CGRect(x: -1.2, y: -0.8 - 1.2, width: 2.4, height: 2.4)), with: .color(color(inkWet, alpha)))
    }
}
