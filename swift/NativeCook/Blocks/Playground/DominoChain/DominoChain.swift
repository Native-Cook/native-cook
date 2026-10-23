import SwiftUI
import UIKit

/// A run of ivory dominoes on a green felt table seen from above at an angle. Tap the first (or flick
/// any) and the run topples, each tile turning about its leading edge and striking the next; drag
/// across the table to lay a new run along your finger; sweep across a fallen run to stand it back
/// up, last tile first.
struct DominoChain: View {
    /// Lays a spiral, tips it over, stands it back up and clears the table, on a loop.
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = DominoChainModel()
    @State private var fallen = false
    /// True while a drag is down; SwiftUI resets it when the drag ends or is cancelled.
    @GestureState private var touching = false

    var body: some View {
        // The block draws under the system bars and keeps its text clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .onAppear {
            model.onFallen = { fallen = $0 }
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let layout = DominoChainLayout(width: size.width, height: size.height, railY: insets.top + DominoChainTuning.railAt)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .top) {
            Color(hex: 0x07090a)

            TimelineView(.animation) { timeline in
                let date = timeline.date
                Canvas { context, _ in
                    model.render(context, date: date, layout: layout, auto: auto, reduced: reduced)
                }
            }
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .updating($touching) { _, state, _ in state = true }
                    .onChanged { model.touchChanged($0) }
                    .onEnded { model.touchEnded($0) }
            )
            .onChange(of: touching) { _, now in
                // A drag the system cancelled (a swipe from the edge, an alert) never reports its end;
                // after a normal end (onEnded has run) this does nothing.
                if !now { model.touchCancelled() }
            }
            .accessibilityElement()
            .accessibilityLabel("A run of dominoes on a felt table. Tap the first tile to topple the run, drag to lay a new run, sweep across a fallen run to stand it up.")

            VStack(alignment: .leading, spacing: 4) {
                Text("TABLE NO. 4")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(1.6)
                    .foregroundStyle(Color(hex: 0xc9a978))
                Text("Domino Run")
                    .font(.system(size: 28, weight: .semibold))
                    .kerning(-0.2)
                    .foregroundStyle(Color(hex: 0xf3ead8))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
            .padding(.top, insets.top + 22)
            .allowsHitTesting(false)

            Text(fallen ? "Sweep across to stand them back up" : "Tap the first tile · drag to lay a new run")
                .font(.system(size: 13, weight: .medium))
                .kerning(0.2)
                .foregroundStyle(Color.rgba(243, 234, 216, 0.82))
                .shadow(color: .black.opacity(0.5), radius: 2, y: 1)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
                .frame(width: max(0, size.width), height: max(0, size.height - insets.bottom - 18), alignment: .bottom)
                .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height)
        .clipped()
    }
}

/// The engine, stepped once a frame like the RN frame callback, its drawing and the touch tracking.
@MainActor
final class DominoChainModel {
    private typealias T = DominoChainTuning
    private typealias S = DominoChainSize

    private var engine: DominoChainEngine?
    private var last: Date?
    private var wasAuto: Bool?
    var onFallen: ((Bool) -> Void)?

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let rigid = UIImpactFeedbackGenerator(style: .rigid)

    // The touch: Gesture Handler's race of a pan (6 pt) and a tap (0.3 s, 10 pt), from one drag.
    private var touchStart: CGPoint?
    private var touchTime = Date.distantPast
    private var panning = false

    func render(_ context: GraphicsContext, date: Date, layout: DominoChainLayout, auto: Bool, reduced: Bool) {
        let engine: DominoChainEngine
        if let e = self.engine, e.reduced == reduced {
            engine = e
        } else {
            engine = DominoChainEngine(auto: auto, reduced: reduced)
            self.engine = engine
            wasAuto = auto
        }
        if wasAuto != auto {
            wasAuto = auto
            engine.setAuto(auto)
        }
        engine.setLayout(layout)
        // RN's first frame reports no previous frame: 16 ms.
        let raw = last.map { date.timeIntervalSince($0) } ?? 0.016
        last = date
        engine.frame(max(0, raw))
        let ev = engine.events
        if ev != 0 {
            engine.events = 0
            if ev & T.eventClick != 0 { light.impactOccurred() }
            if ev & T.eventTipped != 0 { medium.impactOccurred() }
            if ev & T.eventRaised != 0 { rigid.impactOccurred() }
            if ev & T.eventFallen != 0 {
                let down = engine.wasFallen
                let callback = onFallen
                DispatchQueue.main.async { callback?(down) }
            }
        }
        if engine.started {
            DominoChainDrawing(engine: engine, layout: layout).draw(context)
        }
    }

    func touchChanged(_ value: DragGesture.Value) {
        guard let engine else { return }
        // A drag that was cancelled never reported its end: close it before starting the new one.
        if let start = touchStart, start != value.startLocation { cancelNow() }
        if touchStart == nil {
            touchStart = value.startLocation
            touchTime = Date()
            panning = false
        }
        guard let start = touchStart else { return }
        let tx = value.location.x - start.x
        let ty = value.location.y - start.y
        if !panning {
            if hypot(tx, ty) >= 6 {
                panning = true
                engine.panStart(x0: start.x, y0: start.y, x: value.location.x, y: value.location.y)
            }
        } else {
            engine.panMove(x: value.location.x, y: value.location.y, translationX: tx, translationY: ty)
        }
    }

    /// The drag may have been cancelled without an end. Checked a moment later, once a normal
    /// `onEnded` has surely run: if the same drag is still open, it is closed.
    func touchCancelled() {
        guard let start = touchStart else { return }
        Task { @MainActor [weak self] in
            guard let self, self.touchStart == start else { return }
            self.cancelNow()
        }
    }

    private func cancelNow() {
        guard touchStart != nil else { return }
        if panning { engine?.panEnd(velocityX: 0, velocityY: 0, cancelled: true) }
        touchStart = nil
        panning = false
    }

    func touchEnded(_ value: DragGesture.Value) {
        defer {
            touchStart = nil
            panning = false
        }
        guard let engine, let start = touchStart else { return }
        if panning {
            engine.panEnd(velocityX: value.velocity.width, velocityY: value.velocity.height)
        } else if Date().timeIntervalSince(touchTime) <= 0.3 && hypot(value.location.x - start.x, value.location.y - start.y) <= 10 {
            engine.tap(x: value.location.x, y: value.location.y)
        }
    }
}

/// Draws one frame: the table, the trace of a path, the shadows, the tiles, the glow and the finger.
struct DominoChainDrawing {
    private typealias T = DominoChainTuning
    private typealias S = DominoChainSize

    let engine: DominoChainEngine
    let layout: DominoChainLayout

    // The light: a lamp above and behind the table, to the left. Shadows fall towards the viewer.
    static let light: (Double, Double, Double) = {
        let l = (0.42 * 0.42 + 1 + 0.25).squareRoot()
        return (-0.42 / l, 1 / l, 0.5 / l)
    }()
    static let shadowAlpha = 0.42
    static let shadowBlur = 3.2
    // The tiles: ivory with ink pips, lit by the lamp.
    static let ivory = (0.95, 0.92, 0.85)
    static let pip = Color(hex: 0x1c1c1e)
    static let pipRadius = 2.1

    /// The pips of a domino half, as (u, v) in a −1…1 grid.
    static let pipLayouts: [[Double]] = [
        [],
        [0, 0],
        [-1, -1, 1, 1],
        [-1, -1, 0, 0, 1, 1],
        [-1, -1, 1, -1, -1, 1, 1, 1],
        [-1, -1, 1, -1, 0, 0, -1, 1, 1, 1],
        [-1, -1, 1, -1, -1, 0, 1, 0, -1, 1, 1, 1],
    ]

    // Faces as corner indices (counter-clockwise seen from outside): front, back, top, left, right.
    private static let faces: [(Int, Int, Int, Int)] = [(4, 5, 7, 6), (0, 2, 3, 1), (2, 6, 7, 3), (0, 4, 6, 2), (1, 3, 7, 5)]

    func draw(_ context: GraphicsContext) {
        drawTable(context)

        // The trace of a path being drawn, fading once the finger lifts.
        let time = engine.liveRun.time
        let traceAlpha = engine.drawing ? 1 : max(0, 1 - (time - engine.traceEnd) / 0.6)
        if traceAlpha > 0 && engine.pathX.count > 1 {
            var path = Path()
            for k in 0..<engine.pathX.count {
                let p = layout.project(engine.pathX[k], 0, engine.pathZ[k])
                if k == 0 { path.move(to: CGPoint(x: p.x, y: p.y)) } else { path.addLine(to: CGPoint(x: p.x, y: p.y)) }
            }
            context.drawLayer { layer in
                layer.addFilter(.blur(radius: 1.4))
                layer.stroke(
                    path,
                    with: .color(Color.rgba(235, 245, 225, 0.32 * traceAlpha)),
                    style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
                )
            }
        }

        // Every domino of both runs, far to near.
        var order: [(run: Int, i: Int, depth: Double)] = []
        for k in 0..<2 {
            let c = engine.chains[k]
            for i in 0..<c.count {
                let s = sin(c.tilt[i])
                let p = layout.project(c.x[i] + c.hx[i] * (S.h / 2) * s, 0, c.z[i] + c.hz[i] * (S.h / 2) * s)
                order.append((k, i, p.depth))
            }
        }
        // Insertion sort by depth, farthest first (stable, as in RN).
        var idx: [Int] = []
        for k in 0..<order.count {
            var j = idx.count
            idx.append(k)
            while j > 0 && order[idx[j - 1]].depth < order[k].depth {
                idx[j] = idx[j - 1]
                j -= 1
            }
            idx[j] = k
        }

        // Shadows first, all in one soft path, then the tiles.
        var shadows = Path()
        var contacts = Path()
        for k in idx {
            addShadow(&shadows, &contacts, engine.chains[order[k].run], order[k].i)
        }
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: Self.shadowBlur))
            layer.fill(shadows, with: .color(Color.rgba(4, 16, 10, Self.shadowAlpha)))
        }
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 1.2))
            layer.fill(contacts, with: .color(Color.rgba(2, 10, 6, 0.5)))
        }
        for k in idx {
            drawDomino(context, engine.chains[order[k].run], order[k].i)
        }

        // A soft glow under the first tile while the run waits to be tipped.
        let live = engine.liveRun
        if live.count > 1 && !live.anyFallen && !engine.drawing && live.sink[0] == .infinity {
            let settle = min(1, max(0, (live.time - live.born[live.count - 1] - 0.4) / 0.5))
            if settle > 0 {
                let pulse = 0.5 + 0.5 * sin(live.time * 3.2)
                let p = layout.project(live.x[0], 0, live.z[0])
                let r = 17 + 4 * pulse
                context.drawLayer { layer in
                    layer.addFilter(.blur(radius: 1.5))
                    layer.stroke(
                        Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r * 0.62, width: r * 2, height: r * 1.24)),
                        with: .color(Color.rgba(250, 236, 190, (0.22 + 0.3 * pulse) * settle)),
                        lineWidth: 1.4
                    )
                }
            }
        }

        // The autoPlay finger.
        let g = engine.ghost
        if g > 0.01 {
            let finger = Path(ellipseIn: CGRect(x: engine.fingerX - 17, y: engine.fingerY - 17, width: 34, height: 34))
            context.fill(finger, with: .color(.white.opacity(0.16 * g)))
            context.stroke(finger, with: .color(.white.opacity(0.5 * g)), lineWidth: 1.5)
        }
    }

    /// The room, the far rail and the felt.
    private func drawTable(_ context: GraphicsContext) {
        let width = layout.width
        let height = layout.height
        let railY = layout.railY
        context.fill(
            Path(CGRect(x: 0, y: 0, width: width, height: railY + 2)),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x07090a), Color(hex: 0x141a17)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: railY)
            )
        )
        let lamp = layout.project(T.spiralX - 30, 0, T.spiralZ + 40)
        let feltRect = CGRect(x: 0, y: railY, width: width, height: height - railY)
        context.fill(
            Path(feltRect),
            with: .shader(
                ShaderLibrary.dominoChainFelt(
                    .float2(width, height), .float2(lamp.x, lamp.y), .float(width * 0.95), .float(railY)
                ),
                bounds: CGRect(x: 0, y: 0, width: width, height: height)
            )
        )
        // The rail: its top and its front, with a lit edge between them.
        let farZ = layout.farZ
        let a = layout.project(0, T.railHeight, farZ)
        let b = layout.project(0, T.railHeight, farZ + T.railDepth)
        context.fill(
            Path(CGRect(x: 0, y: a.y, width: width, height: railY - a.y + 0.5)),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x6b4426), Color(hex: 0x2a170b)]),
                startPoint: CGPoint(x: 0, y: a.y), endPoint: CGPoint(x: 0, y: railY)
            )
        )
        context.fill(
            Path(CGRect(x: 0, y: b.y, width: width, height: a.y - b.y)),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x3d2514), Color(hex: 0x8a5a33)]),
                startPoint: CGPoint(x: 0, y: b.y), endPoint: CGPoint(x: 0, y: a.y)
            )
        )
        var lit = Path()
        lit.move(to: CGPoint(x: 0, y: a.y))
        lit.addLine(to: CGPoint(x: width, y: a.y))
        context.stroke(lit, with: .color(Color.rgba(255, 214, 160, 0.45)), lineWidth: 1)
        context.fill(
            Path(CGRect(x: 0, y: railY, width: width, height: 22)),
            with: .linearGradient(
                Gradient(colors: [.black.opacity(0.55), .black.opacity(0)]),
                startPoint: CGPoint(x: 0, y: railY), endPoint: CGPoint(x: 0, y: railY + 22)
            )
        )
    }

    /// The 3D corners of a domino: index = a·4 + b·2 + c over (back/front, bottom/top, left/right).
    private func corners(_ c: DominoChainRun, _ i: Int, _ out: inout [Double]) -> Double {
        let pop = c.pop(i)
        let sc = engine.reduced ? 1 : pop.scale
        let lift = engine.reduced ? 0 : pop.lift
        let fx = c.hx[i]
        let fz = c.hz[i]
        // Lateral axis: the heading turned a quarter.
        let lx = -fz
        let lz = fx
        let ct = cos(c.tilt[i])
        let st = sin(c.tilt[i])
        let th = S.t * sc
        let hh = S.h * sc
        let ww = S.w * sc
        // The pivot: the front bottom edge.
        let px = c.x[i] + fx * (th / 2)
        let pz = c.z[i] + fz * (th / 2)
        for a in 0..<2 {
            for b in 0..<2 {
                for k in 0..<2 {
                    let la = a == 0 ? -th : 0
                    let lb = b == 0 ? 0 : hh
                    let lc = (k == 0 ? -0.5 : 0.5) * ww
                    // Tip forward about the pivot.
                    let ra = la * ct + lb * st
                    let rb = -la * st + lb * ct
                    let n = (a * 4 + b * 2 + k) * 3
                    out[n] = px + fx * ra + lx * lc
                    out[n + 1] = rb + lift
                    out[n + 2] = pz + fz * ra + lz * lc
                }
            }
        }
        return pop.alpha
    }

    /// Adds a domino's shadow (its corners cast along the light onto the table) and its contact shade.
    private func addShadow(_ shadows: inout Path, _ contacts: inout Path, _ c: DominoChainRun, _ i: Int) {
        var q = [Double](repeating: 0, count: 24)
        let alpha = corners(c, i, &q)
        if alpha < 0.3 { return }
        let light = Self.light
        // Cast each corner along the light onto the table, then wrap the eight in a hull.
        var xs = [Double](repeating: 0, count: 8)
        var ys = [Double](repeating: 0, count: 8)
        for k in 0..<8 {
            let y = max(0, q[k * 3 + 1])
            let x = q[k * 3] - (light.0 / light.1) * y
            let z = q[k * 3 + 2] - (light.2 / light.1) * y
            let p = layout.project(x, 0, z)
            xs[k] = p.x
            ys[k] = p.y
        }
        // Gift wrap.
        var start = 0
        for k in 1..<8 where xs[k] < xs[start] || (xs[k] == xs[start] && ys[k] < ys[start]) { start = k }
        var cur = start
        var first = true
        for _ in 0..<9 {
            if first {
                shadows.move(to: CGPoint(x: xs[cur], y: ys[cur]))
            } else {
                shadows.addLine(to: CGPoint(x: xs[cur], y: ys[cur]))
            }
            first = false
            var next = (cur + 1) % 8
            for k in 0..<8 {
                let cross = (xs[next] - xs[cur]) * (ys[k] - ys[cur]) - (ys[next] - ys[cur]) * (xs[k] - xs[cur])
                if cross < 0 { next = k }
            }
            cur = next
            if cur == start { break }
        }
        shadows.closeSubpath()
        // A tight dark line where the base meets the felt.
        if q[1] < 1.5 {
            let a = layout.project(q[0], 0, q[2])
            let b = layout.project(q[3], 0, q[5])
            let e = layout.project(q[12], 0, q[14])
            let f = layout.project(q[15], 0, q[17])
            contacts.move(to: CGPoint(x: a.x, y: a.y))
            contacts.addLine(to: CGPoint(x: b.x, y: b.y))
            contacts.addLine(to: CGPoint(x: f.x, y: f.y))
            contacts.addLine(to: CGPoint(x: e.x, y: e.y))
            contacts.closeSubpath()
        }
    }

    /// One domino: its visible faces, lit by the lamp, the pips on its front.
    private func drawDomino(_ context: GraphicsContext, _ c: DominoChainRun, _ i: Int) {
        var q = [Double](repeating: 0, count: 24)
        let alpha = corners(c, i, &q)
        if alpha <= 0.01 { return }
        var sp = [Double](repeating: 0, count: 24)
        for k in 0..<8 {
            let p = layout.project(q[k * 3], q[k * 3 + 1], q[k * 3 + 2])
            sp[k * 3] = p.x
            sp[k * 3 + 1] = p.y
            sp[k * 3 + 2] = p.depth
        }
        let fx = c.hx[i]
        let fz = c.hz[i]
        let lx = -fz
        let lz = fx
        let ct = cos(c.tilt[i])
        let st = sin(c.tilt[i])
        // Forward and up after the tip, and the faces' outward normals.
        let normals: [(Double, Double, Double)] = [
            (fx * ct, -st, fz * ct),
            (-fx * ct, st, -fz * ct),
            (fx * st, ct, fz * st),
            (-lx, 0, -lz),
            (lx, 0, lz),
        ]
        let light = Self.light
        let body = { (ctx: inout GraphicsContext) in
            for f in 0..<5 {
                let face = Self.faces[f]
                let (a, b, cc, d) = face
                let (nx, ny, nz) = normals[f]
                // Visible when it faces the camera.
                let mx = (q[a * 3] + q[cc * 3]) / 2
                let my = (q[a * 3 + 1] + q[cc * 3 + 1]) / 2
                let mz = (q[a * 3 + 2] + q[cc * 3 + 2]) / 2
                if nx * (0 - mx) + ny * (T.camY - my) + nz * (T.camZ - mz) <= 0 { continue }
                let lit = max(0, nx * light.0 + ny * light.1 + nz * light.2)
                let shade = 0.52 + 0.5 * lit
                let r = (min(1, Self.ivory.0 * shade) * 255).rounded()
                let g = (min(1, Self.ivory.1 * shade) * 255).rounded()
                let bl = (min(1, Self.ivory.2 * shade * 0.98) * 255).rounded()
                var path = Path()
                path.move(to: CGPoint(x: sp[a * 3], y: sp[a * 3 + 1]))
                path.addLine(to: CGPoint(x: sp[b * 3], y: sp[b * 3 + 1]))
                path.addLine(to: CGPoint(x: sp[cc * 3], y: sp[cc * 3 + 1]))
                path.addLine(to: CGPoint(x: sp[d * 3], y: sp[d * 3 + 1]))
                path.closeSubpath()
                ctx.fill(path, with: .color(Color.rgba(r, g, bl, 1)))
                ctx.stroke(path, with: .color(Color.rgba(70, 58, 40, 0.55)), style: StrokeStyle(lineWidth: 0.6, lineJoin: .round))
                // The big faces: a groove across the middle, and pips on the front.
                if f <= 1 {
                    // Face frame: u across (corner a → the corner beside it on the bottom), v up.
                    let ox = sp[a * 3]
                    let oy = sp[a * 3 + 1]
                    let bottom = f == 0 ? 5 : 1
                    let topK = f == 0 ? 6 : 2
                    let ex = (sp[bottom * 3] - ox) / S.w
                    let ey = (sp[bottom * 3 + 1] - oy) / S.w
                    let vx = (sp[topK * 3] - ox) / S.h
                    let vy = (sp[topK * 3 + 1] - oy) / S.h
                    var inFace = ctx
                    inFace.concatenate(CGAffineTransform(a: ex, b: ey, c: vx, d: vy, tx: ox, ty: oy))
                    var groove = Path()
                    groove.move(to: CGPoint(x: 3, y: S.h / 2))
                    groove.addLine(to: CGPoint(x: S.w - 3, y: S.h / 2))
                    inFace.stroke(groove, with: .color(Color.rgba(40, 36, 30, 0.75)), style: StrokeStyle(lineWidth: 0.9, lineCap: .round))
                    if f == 0 {
                        var pips = Path()
                        let halves = [c.pipsA[i], c.pipsB[i]]
                        for h in 0..<2 {
                            let layout = Self.pipLayouts[halves[h]]
                            let v0 = h == 0 ? S.h * 0.25 : S.h * 0.75
                            var k = 0
                            while k < layout.count {
                                let px = S.w / 2 + layout[k] * 5.2
                                let py = v0 + layout[k + 1] * 5.2
                                pips.addEllipse(in: CGRect(x: px - Self.pipRadius, y: py - Self.pipRadius, width: Self.pipRadius * 2, height: Self.pipRadius * 2))
                                k += 2
                            }
                        }
                        inFace.fill(pips, with: .color(Self.pip))
                    }
                }
            }
        }
        if alpha < 1 {
            context.drawLayer { layer in
                layer.opacity = alpha
                body(&layer)
            }
        } else {
            var ctx = context
            body(&ctx)
        }
    }
}
