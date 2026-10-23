import SwiftUI
import UIKit

/// Paper marbling (ebru). Paint dropped on size spreads into rings, pushing every colour already
/// floating outwards so the area of each is kept; a stylus or a comb dragged through the paint pulls
/// it into feathered, combed patterns. Print lays a sheet over the tray and peels it back to show the
/// pattern taken up on its underside, leaving the size clean.
struct MarblingComb: View {
    /// Drops rings, combs a nonpareil and prints it, on a loop.
    var autoPlay = false
    /// The size (index 0, the ground) and the paints: an ebru palette of lapis, bone, madder, ochre,
    /// cobalt, sage, rose and ink.
    var palette: [UInt32] = [0x16243c, 0xefe4ca, 0xb5432f, 0xd99a36, 0x3d68aa, 0x7e9b83, 0xe1a693, 0x1d1a17]

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var model = MarblingCombModel()
    @State private var tool = 0
    @State private var ink = 2
    @State private var printing = false
    /// True while a drag is down; SwiftUI resets it when the drag ends or is cancelled.
    @GestureState private var touching = false

    private typealias M = MarblingCombMetrics

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
        .onChange(of: tool) { model.engine?.selectedTool = tool }
        .onChange(of: ink) { model.engine?.ink = ink }
        .onAppear {
            model.onPrinting = { printing = $0 }
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let settings = MarblingCombSettings(
            auto: autoPlay && !reduceMotion,
            reduced: reduceMotion,
            palette: palette,
            tool: tool,
            ink: ink,
            scale: displayScale,
            sheetTop: insets.top + MarblingCombTuning.sheetTop,
            sheetBottom: size.height - insets.bottom - MarblingCombTuning.sheetBottom
        )
        return ZStack(alignment: .top) {
            Color(hex: palette[0])

            TimelineView(.animation) { timeline in
                let date = timeline.date
                Canvas { context, canvasSize in
                    model.render(context, size: canvasSize, date: date, settings: settings)
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

            VStack(alignment: .leading, spacing: 3) {
                Text("Ebru")
                    .font(.system(size: 22, weight: .semibold))
                    .kerning(0.3)
                    .foregroundStyle(M.ink)
                    .shadow(color: .black.opacity(0.45), radius: 3, y: 1)
                Text("Tap to drop · hold for rings · drag to comb")
                    .font(.system(size: 12.5, weight: .medium))
                    .kerning(0.2)
                    .foregroundStyle(M.ink.opacity(0.78))
                    .shadow(color: .black.opacity(0.5), radius: 2.5, y: 1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 22)
            .padding(.top, insets.top + 18)
            .allowsHitTesting(false)

            controls
                .frame(width: max(0, size.width), height: max(0, size.height - insets.bottom - 16), alignment: .bottom)
        }
        .frame(width: size.width, height: size.height)
        .clipped()
    }

    private var controls: some View {
        VStack(spacing: 14) {
            HStack(spacing: 10) {
                ForEach(1..<palette.count, id: \.self) { index in
                    Button {
                        ink = index
                        model.selection.selectionChanged()
                    } label: {
                        Circle()
                            .fill(Color(hex: palette[index]))
                            .overlay(
                                Circle()
                                    .inset(by: 0.5)
                                    .stroke(
                                        LinearGradient(colors: [.white.opacity(0.35), .clear], startPoint: .top, endPoint: .center),
                                        lineWidth: 1
                                    )
                            )
                            .frame(width: 20, height: 20)
                            .shadow(color: .black.opacity(0.45), radius: 1.5, y: 1)
                            .frame(width: 30, height: 30)
                            .overlay(
                                Circle()
                                    .strokeBorder(index == ink ? M.ink.opacity(0.9) : .clear, lineWidth: 1.5)
                            )
                            .padding(6)
                            .contentShape(Circle())
                            .padding(-6)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Paint \(index)")
                    .accessibilityAddTraits(index == ink ? .isSelected : [])
                }
            }
            HStack(spacing: 10) {
                HStack(spacing: 0) {
                    ForEach(Array(["Stylus", "Comb"].enumerated()), id: \.offset) { k, label in
                        Button {
                            tool = k
                            model.selection.selectionChanged()
                        } label: {
                            Text(label)
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(tool == k ? Color(hex: 0x16243c) : M.ink.opacity(0.8))
                                .padding(.horizontal, 18)
                                .frame(height: 36)
                                .background(Capsule().fill(tool == k ? M.ink.opacity(0.92) : .clear))
                                .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(tool == k ? .isSelected : [])
                    }
                }
                .padding(4)
                .background(Color.rgba(14, 20, 32, 0.35))
                .background(.ultraThinMaterial)
                .environment(\.colorScheme, .dark)
                .clipShape(Capsule())

                Button {
                    model.printButton()
                } label: {
                    Text("Print")
                        .font(.system(size: 15, weight: .bold))
                        .kerning(0.2)
                        .foregroundStyle(Color(hex: 0x16243c))
                        .padding(.horizontal, 24)
                        .frame(height: 44)
                        .background(Capsule().fill(M.paper))
                        .shadow(color: .black.opacity(0.35), radius: 7, y: 4)
                }
                .buttonStyle(MarblingCombPressStyle())
                .accessibilityLabel("Print the pattern")
            }
        }
        .padding(.horizontal, 16)
        .opacity(printing ? 0.5 : 1)
        .allowsHitTesting(!printing)
    }
}

/// The Print button shrinks a little while held.
private struct MarblingCombPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}

enum MarblingCombMetrics {
    static let paper = Color(hex: 0xf2ebdc)
    static let ink = Color(hex: 0xf4ecdb)
    // The paper's grain strength.
    static let grain = 0.09
}

/// What a frame needs from the view.
struct MarblingCombSettings {
    var auto: Bool
    var reduced: Bool
    var palette: [UInt32]
    var tool: Int
    var ink: Int
    var scale: Double
    var sheetTop: Double
    var sheetBottom: Double
}

/// The engine, the drawing caches and the touch tracking, stepped once a frame like the RN frame
/// callback.
@MainActor
final class MarblingCombModel {
    private typealias T = MarblingCombTuning

    private(set) var engine: MarblingCombEngine?
    private var last: Date?
    private var wasAuto: Bool?
    private var size = CGSize.zero

    // Each outline's path, with the outline revision it was built from.
    private var paths: [Int: (rev: Int, path: Path)] = [:]
    private var pathsRev = -1
    // The paint taken up by the sheet: the ground and every outline, oldest first.
    private var taken: (ground: Int, blots: [(path: Path, color: Int)])?
    private var fills: [Color] = []
    private var rims: [Color] = []
    private var paletteKey: [UInt32] = []

    var onPrinting: ((Bool) -> Void)?
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    let selection = UISelectionFeedbackGenerator()

    // The touch: Gesture Handler's race of a pan (6 pt), a long press (0.3 s, 12 pt) and a tap
    // (0.28 s, 10 pt), tracked by hand from one drag gesture.
    private var touchStart: CGPoint?
    private var touchTime = Date.distantPast
    private var panning = false
    private var dripping = false
    private var settled = false
    private var pressTimer: DispatchWorkItem?

    // MARK: Frame

    func render(_ context: GraphicsContext, size: CGSize, date: Date, settings s: MarblingCombSettings) {
        self.size = size
        if paletteKey != s.palette {
            paletteKey = s.palette
            fills = s.palette.map { Color(hex: $0) }
            rims = s.palette.map { hex in
                let r = (Double((hex >> 16) & 255) * 0.62).rounded()
                let g = (Double((hex >> 8) & 255) * 0.62).rounded()
                let b = (Double(hex & 255) * 0.62).rounded()
                return Color.rgba(r, g, b, 0.85)
            }
        }
        let engine: MarblingCombEngine
        if let e = self.engine, e.reduced == s.reduced {
            engine = e
        } else {
            engine = MarblingCombEngine(auto: s.auto, reduced: s.reduced, paints: s.palette.count)
            engine.selectedTool = s.tool
            engine.ink = s.ink
            engine.onTakePrint = { [unowned self, unowned engine] in self.takePrint(engine) }
            self.engine = engine
            wasAuto = s.auto
        }
        if wasAuto != s.auto {
            wasAuto = s.auto
            engine.setAuto(s.auto)
        }
        // RN's first frame reports no previous frame: 16 ms.
        let raw = last.map { date.timeIntervalSince($0) } ?? 0.016
        last = date
        engine.frame(max(0, raw), width: size.width, height: size.height)
        feel(engine)
        draw(context, engine: engine, settings: s)
    }

    private func feel(_ engine: MarblingCombEngine) {
        let ev = engine.events
        if ev == 0 { return }
        engine.events = 0
        if ev & T.eventDropped != 0 { light.impactOccurred() }
        if ev & T.eventStroked != 0 { selection.selectionChanged() }
        if ev & T.eventPressed != 0 { medium.impactOccurred() }
        if ev & (T.eventPrintStart | T.eventPrintDone) != 0 {
            let on = engine.printT >= 0
            let callback = onPrinting
            DispatchQueue.main.async { callback?(on) }
        }
    }

    /// The outline as a closed, smooth path: quadratic curves through the midpoints of its edges.
    private static func outlinePath(_ xs: [Double], _ ys: [Double]) -> Path {
        var p = Path()
        let n = xs.count
        p.move(to: CGPoint(x: (xs[n - 1] + xs[0]) / 2, y: (ys[n - 1] + ys[0]) / 2))
        for i in 0..<n {
            let j = i + 1 < n ? i + 1 : 0
            p.addQuadCurve(
                to: CGPoint(x: (xs[i] + xs[j]) / 2, y: (ys[i] + ys[j]) / 2),
                control: CGPoint(x: xs[i], y: ys[i])
            )
        }
        p.closeSubpath()
        return p
    }

    /// Brings every outline's path up to date.
    private func refreshPaths(_ m: MarblingCombMarble) {
        if pathsRev == m.rev { return }
        pathsRev = m.rev
        var next: [Int: (rev: Int, path: Path)] = [:]
        next.reserveCapacity(m.blots.count)
        for blot in m.blots where blot.x.count >= 3 {
            if let cached = paths[blot.id], cached.rev == blot.rev {
                next[blot.id] = cached
            } else {
                next[blot.id] = (blot.rev, Self.outlinePath(blot.x, blot.y))
            }
        }
        paths = next
    }

    /// The sheet lies flat: it takes up the paint as it is now.
    private func takePrint(_ engine: MarblingCombEngine) {
        guard let m = engine.marble else { return }
        refreshPaths(m)
        var blots: [(path: Path, color: Int)] = []
        for blot in m.blots {
            if let cached = paths[blot.id] { blots.append((cached.path, blot.color)) }
        }
        taken = (m.ground, blots)
    }

    /// The fill and the rim of palette colour `index` (wrapped, so a short palette never runs out).
    private func fillColor(_ index: Int) -> Color {
        fills.isEmpty ? .clear : fills[((index % fills.count) + fills.count) % fills.count]
    }

    private func rimColor(_ index: Int) -> Color {
        rims.isEmpty ? .clear : rims[((index % rims.count) + rims.count) % rims.count]
    }

    /// The ground and every outline, oldest first, each with a fine darker rim.
    private func drawPaint(_ context: GraphicsContext, ground: Int, blots: [(path: Path, color: Int)], hairline: Double) {
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(fillColor(ground)))
        for blot in blots {
            context.fill(blot.path, with: .color(fillColor(blot.color)))
            context.stroke(blot.path, with: .color(rimColor(blot.color)), lineWidth: hairline)
        }
    }

    private func draw(_ context: GraphicsContext, engine: MarblingCombEngine, settings s: MarblingCombSettings) {
        let width = size.width
        let height = size.height
        let full = CGRect(origin: .zero, size: size)
        let hairline = 1 / max(1, s.scale)
        guard let m = engine.marble else {
            context.fill(Path(full), with: .color(fillColor(0)))
            return
        }
        refreshPaths(m)
        context.fill(Path(full), with: .color(fillColor(m.ground)))
        for blot in m.blots {
            guard let cached = paths[blot.id] else { continue }
            context.fill(cached.path, with: .color(fillColor(blot.color)))
            context.stroke(cached.path, with: .color(rimColor(blot.color)), lineWidth: hairline)
        }

        // What the last print left behind on the size, fading.
        let left = engine.residue
        if left > 0, let print = taken {
            context.drawLayer { layer in
                layer.opacity = T.residue * left
                drawPaint(layer, ground: print.ground, blots: print.blots, hairline: hairline)
            }
        }

        // Light on the size: sheen, grain and a vignette. The sheen drifts once per loop, so a
        // recording of one loop joins up.
        let phase = engine.elapsed / MarblingCombScript.loop * Double.pi * 2
        context.fill(
            Path(full),
            with: .shader(ShaderLibrary.marblingCombWater(.float2(width, height), .float(phase)), bounds: full)
        )

        // The tool.
        let a = engine.toolAlpha
        if a > 0.01 {
            if engine.toolKind == 1 {
                drawComb(context, engine.toolX, engine.toolY, engine.combAX, engine.combAY, engine.combSpacing, engine.combSide, a)
            } else {
                drawStylus(context, engine.toolX, engine.toolY, a)
            }
        }

        // The sheet.
        let p = engine.printT
        if p >= 0 {
            drawSheet(context, p: p, settings: s, hairline: hairline)
        }
    }

    // MARK: Tools

    /// The stylus, a steel pin seen from above, with its shadow and the meniscus round it.
    private func drawStylus(_ context: GraphicsContext, _ x: Double, _ y: Double, _ alpha: Double) {
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 3.5))
            layer.fill(Self.circle(x + 4, y + 7, 6), with: .color(.black.opacity(0.4 * alpha)))
        }
        context.stroke(Self.circle(x, y, 10), with: .color(.white.opacity(0.28 * alpha)), lineWidth: 1.2)
        var body = context
        body.opacity = alpha
        body.fill(
            Self.circle(x, y, 5.5),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0xffffff), location: 0),
                    .init(color: Color(hex: 0xb9bec6), location: 0.45),
                    .init(color: Color(hex: 0x5d636d), location: 1),
                ]),
                center: CGPoint(x: x - 2, y: y - 2), startRadius: 0, endRadius: 8
            )
        )
    }

    /// The comb: a walnut rod across the tray along its axis, its steel tines in the size, and its shadow.
    private func drawComb(
        _ context: GraphicsContext, _ x: Double, _ y: Double, _ ax: Double, _ ay: Double,
        _ spacing: Double, _ side: Double, _ alpha: Double
    ) {
        let reach = spacing * (side + 0.5)
        var c = context
        c.translateBy(x: x, y: y)
        c.rotate(by: .radians(atan2(ay, ax)))
        c.drawLayer { layer in
            layer.addFilter(.blur(radius: 5))
            layer.fill(Path(CGRect(x: -reach + 5, y: -2, width: reach * 2, height: 12)), with: .color(.black.opacity(0.42 * alpha)))
        }
        let count = side.isFinite ? max(0, min(400, Int(side))) : 0
        var wake = Path()
        for j in -count...count {
            wake.addEllipse(in: CGRect(x: Double(j) * spacing - 4.5, y: -4.5, width: 9, height: 9))
        }
        c.stroke(wake, with: .color(.white.opacity(0.22 * alpha)), lineWidth: 1)
        var solid = c
        solid.opacity = alpha
        solid.fill(
            Path(roundedRect: CGRect(x: -reach, y: -9, width: reach * 2, height: 8), cornerRadius: 3),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x8a5d38), location: 0),
                    .init(color: Color(hex: 0x5b3a1f), location: 0.45),
                    .init(color: Color(hex: 0x2f1d0e), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: -5), endPoint: CGPoint(x: 0, y: 5)
            )
        )
        var shine = Path()
        shine.move(to: CGPoint(x: -reach + 4, y: -7.6))
        shine.addLine(to: CGPoint(x: reach - 4, y: -7.6))
        c.stroke(shine, with: .color(Color.rgba(255, 228, 190, 0.35 * alpha)), style: StrokeStyle(lineWidth: 1, lineCap: .round))
        for j in -count...count {
            let tx = Double(j) * spacing
            solid.fill(
                Self.circle(tx, 0, 2.2),
                with: .radialGradient(
                    Gradient(colors: [Color(hex: 0xffffff), Color(hex: 0x8c929b)]),
                    center: CGPoint(x: tx - 0.6, y: -0.6), startRadius: 0, endRadius: 2.6
                )
            )
        }
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    // MARK: Sheet

    /// The sheet's rectangle clipped to one side of the fold line: the part already lifted (s < f,
    /// where s runs along `d` from the lower right corner) or the part still down. With `mirror`, the
    /// lifted part is folded over the line, which is where the flap lies.
    private static func sheetPart(
        _ l: Double, _ t: Double, _ r: Double, _ b: Double, _ dx: Double, _ dy: Double, _ f: Double,
        lifted: Bool, mirror: Bool
    ) -> Path? {
        let xs = [r, l, l, r]
        let ys = [b, b, t, t]
        var sides = [Double](repeating: 0, count: 4)
        for k in 0..<4 {
            let s = (xs[k] - r) * dx + (ys[k] - b) * dy - f
            sides[k] = lifted ? -s : s
        }
        var px: [Double] = []
        var py: [Double] = []
        for k in 0..<4 {
            let j = (k + 1) % 4
            let a = sides[k]
            let c = sides[j]
            if a >= 0 {
                px.append(xs[k])
                py.append(ys[k])
            }
            if (a >= 0) != (c >= 0) {
                let u = a / (a - c)
                px.append(xs[k] + (xs[j] - xs[k]) * u)
                py.append(ys[k] + (ys[j] - ys[k]) * u)
            }
        }
        if px.count < 3 { return nil }
        var out = Path()
        for k in 0..<px.count {
            var x = px[k]
            var y = py[k]
            if mirror {
                // Reflect across the fold: s' = 2f − s.
                let s = (x - r) * dx + (y - b) * dy
                x += 2 * (f - s) * dx
                y += 2 * (f - s) * dy
            }
            if k == 0 { out.move(to: CGPoint(x: x, y: y)) } else { out.addLine(to: CGPoint(x: x, y: y)) }
        }
        out.closeSubpath()
        return out
    }

    /// The fold's travel along the sheet's diagonal (0…1+) at `p` = 0…1 through the peel, with a pause to show the print.
    private static func peelAt(_ p: Double) -> Double {
        if p < 0.42 { return 0.5 * MarblingCombScript.easeInOut(p / 0.42) }
        if p < 0.7 { return 0.5 + 0.05 * ((p - 0.42) / 0.28) }
        let k = (p - 0.7) / 0.3
        return 0.55 + 0.65 * k * k
    }

    /// Fills a path with the paper and its grain.
    private func drawPaper(_ context: GraphicsContext, _ clip: Path) {
        var c = context
        c.clip(to: clip)
        let full = Path(CGRect(origin: .zero, size: size))
        c.fill(full, with: .color(MarblingCombMetrics.paper))
        c.fill(full, with: grainShading)
    }

    private var grainShading: GraphicsContext.Shading {
        .shader(ShaderLibrary.marblingCombPaper(.float(MarblingCombMetrics.grain)), bounds: CGRect(origin: .zero, size: size))
    }

    private func drawSheet(_ context: GraphicsContext, p: Double, settings s: MarblingCombSettings, hairline: Double) {
        let l = T.sheetSide
        let r = size.width - T.sheetSide
        let t = s.sheetTop
        let b = s.sheetBottom
        if p < T.lay {
            // Coming down: from a little above and larger, its shadow tightening as it lands.
            let k = MarblingCombScript.easeInOut(p / T.lay)
            let lift = s.reduced ? 0 : 1 - k
            let scale = 1 + 0.06 * lift
            let cx = (l + r) / 2
            let cy = (t + b) / 2
            let sheet = Path(roundedRect: CGRect(x: l, y: t, width: r - l, height: b - t), cornerRadius: 2)
            var c = context
            c.translateBy(x: cx, y: cy - 26 * lift)
            c.scaleBy(x: scale, y: scale)
            c.translateBy(x: -cx, y: -cy)
            c.drawLayer { layer in
                layer.addFilter(.blur(radius: 2 + 18 * lift))
                layer.translateBy(x: 0, y: 3 + 34 * lift)
                layer.fill(sheet, with: .color(.black.opacity(0.45 * min(1, k * 2))))
            }
            c.drawLayer { layer in
                layer.opacity = min(1, k * 2.5)
                drawPaper(layer, sheet)
            }
            return
        }
        let print = taken
        let soak = min(1, (p - T.lay) / T.soak)
        let peel = max(0, (p - T.lay - T.soak) / T.peel)
        let wx = l - r
        let wy = t - b
        let diag = max(1, hypot(wx, wy))
        let dx = wx / diag
        let dy = wy / diag
        if s.reduced {
            // No peel: the sheet fades to its print, then away.
            let show = min(1, peel * 3)
            let gone = max(0, (peel - 0.75) / 0.25)
            let sheet = Path(CGRect(x: l, y: t, width: r - l, height: b - t))
            context.drawLayer { layer in
                layer.opacity = 1 - gone
                drawPaper(layer, sheet)
                if let print {
                    var c = layer
                    c.clip(to: sheet)
                    c.drawLayer { ink in
                        ink.opacity = max(T.soakGhost * soak, show * 0.92)
                        ink.translateBy(x: l + r, y: 0)
                        ink.scaleBy(x: -1, y: 1)
                        drawPaint(ink, ground: print.ground, blots: print.blots, hairline: hairline)
                    }
                    c.fill(Path(CGRect(origin: .zero, size: size)), with: grainShading)
                }
            }
            return
        }
        let f = Self.peelAt(peel) * diag
        // Under the lifting edge, a soft shadow on the size.
        let rest = Self.sheetPart(l, t, r, b, dx, dy, f, lifted: false, mirror: false)
        let lifted = Self.sheetPart(l, t, r, b, dx, dy, f, lifted: true, mirror: false)
        let fx = r + dx * f
        let fy = b + dy * f
        let full = Path(CGRect(origin: .zero, size: size))
        if let lifted, f > 0 {
            var c = context
            c.clip(to: lifted)
            c.fill(full, with: .linearGradient(
                Gradient(colors: [.black.opacity(0.38), .black.opacity(0)]),
                startPoint: CGPoint(x: fx, y: fy),
                endPoint: CGPoint(x: fx - dx * 46, y: fy - dy * 46)
            ))
        }
        // The sheet still lying on the size: its back, the paint showing through where it is wet.
        if let rest {
            drawPaper(context, rest)
            if let print {
                var c = context
                c.clip(to: rest)
                c.drawLayer { ghost in
                    ghost.opacity = T.soakGhost * soak
                    drawPaint(ghost, ground: print.ground, blots: print.blots, hairline: hairline)
                }
            }
        }
        // The flap: the peeled part folded over the fold line, its printed face up.
        if let lifted, f > 0 {
            // Reflection across the fold line through F = corner + f·d, normal d.
            let fd = fx * dx + fy * dy
            let mirror = CGAffineTransform(
                a: 1 - 2 * dx * dx, b: -2 * dx * dy,
                c: -2 * dx * dy, d: 1 - 2 * dy * dy,
                tx: 2 * fd * dx, ty: 2 * fd * dy
            )
            let flap = Self.sheetPart(l, t, r, b, dx, dy, f, lifted: true, mirror: true) ?? lifted
            // Its shadow on the sheet below.
            context.drawLayer { layer in
                layer.addFilter(.blur(radius: 7))
                layer.translateBy(x: dx * 7 + 2, y: dy * 7 + 5)
                layer.fill(flap, with: .color(Color.rgba(40, 26, 10, 0.32)))
            }
            var c = context
            c.clip(to: flap)
            c.fill(full, with: .color(MarblingCombMetrics.paper))
            if let print {
                var printed = c
                printed.concatenate(mirror)
                drawPaint(printed, ground: print.ground, blots: print.blots, hairline: hairline)
            }
            // The paper's tooth through the print, and the light rolling over the fold.
            c.fill(full, with: .color(Color.rgba(242, 235, 220, 0.10)))
            c.fill(full, with: grainShading)
            c.fill(full, with: .linearGradient(
                Gradient(stops: [
                    .init(color: .white.opacity(0.34), location: 0),
                    .init(color: .white.opacity(0.06), location: 0.35),
                    .init(color: .black.opacity(0.12), location: 1),
                ]),
                startPoint: CGPoint(x: fx, y: fy),
                endPoint: CGPoint(x: fx + dx * 60, y: fy + dy * 60)
            ))
        }
    }

    // MARK: Touch

    func touchChanged(_ value: DragGesture.Value) {
        guard let engine else { return }
        let now = Date()
        // A new drag while the last one never reported its end: close that one first.
        if let start = touchStart, start != value.startLocation { cancelNow() }
        if touchStart == nil {
            touchStart = value.startLocation
            touchTime = now
            panning = false
            dripping = false
            settled = false
            let at = value.startLocation
            let item = DispatchWorkItem { [weak self] in
                guard let self, self.touchStart != nil, !self.panning, !self.settled else { return }
                self.dripping = true
                engine.dripStart(x: at.x, y: at.y)
            }
            pressTimer = item
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3, execute: item)
        }
        guard let start = touchStart, !settled else { return }
        let dist = hypot(value.location.x - start.x, value.location.y - start.y)
        if dripping {
            // A long press that wanders too far ends.
            if dist > 12 {
                dripping = false
                settled = true
                engine.dripEnd()
            }
            return
        }
        if !panning && dist >= 6 {
            panning = true
            pressTimer?.cancel()
            engine.panStart(x0: start.x, y0: start.y, x: value.location.x, y: value.location.y)
        } else if panning {
            engine.panMove(x: value.location.x, y: value.location.y)
        }
    }

    func touchEnded(_ value: DragGesture.Value) {
        pressTimer?.cancel()
        pressTimer = nil
        defer { touchStart = nil }
        guard let engine, let start = touchStart else { return }
        if panning {
            engine.panEnd()
        } else if dripping {
            engine.dripEnd()
        } else if !settled {
            let dist = hypot(value.location.x - start.x, value.location.y - start.y)
            if Date().timeIntervalSince(touchTime) <= 0.28 && dist <= 10 {
                engine.tap(x: value.location.x, y: value.location.y)
            }
        }
        panning = false
        dripping = false
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
        pressTimer?.cancel()
        pressTimer = nil
        guard touchStart != nil else { return }
        if panning { engine?.panEnd() }
        if dripping { engine?.dripEnd() }
        touchStart = nil
        panning = false
        dripping = false
    }

    func printButton() {
        engine?.printButton()
    }
}
