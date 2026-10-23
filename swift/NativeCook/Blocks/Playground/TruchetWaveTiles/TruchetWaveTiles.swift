import SwiftUI

/// A full-screen grid of Truchet tiles whose quarter-circle arcs join into continuous lines in two
/// colours. Tiles turn 90° on a spring in waves that travel out from the finger — a tap sends a
/// ring, a drag paints a trail of flips — so the pattern keeps re-knitting. A glass segmented
/// control morphs the tiles between arcs, diagonals and triangles; the swatch beside it crossfades
/// to the next palette, which grows out of the button as a circle.
struct TruchetWaveTiles: View {
    /// Sends waves from set points, cycles the tile styles and the palettes, on a loop.
    var autoPlay = false
    /// Two-colour palettes the swatch button steps through.
    var palettes = TruchetWaveTilesMetrics.palettes

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = TruchetWaveTilesEngine()
    /// A control is being pressed; SwiftUI resets it when the press ends or is cancelled.
    @GestureState private var pressing = false
    /// True while a drag is down on the tiles; reset the same way.
    @GestureState private var touching = false

    private typealias M = TruchetWaveTilesMetrics

    var body: some View {
        // The block draws under the system bars and keeps its controls clear of them itself.
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
        .background(Color(hex: 0x101a2e).ignoresSafeArea())
    }

    private func palette(_ i: Int) -> TruchetWaveTilesPalette {
        let list = palettes.isEmpty ? M.palettes : palettes
        let n = list.count
        return list[((i % n) + n) % n]
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let auto = autoPlay && !reduceMotion
        let swatch = CGPoint(
            x: (size.width - M.controlsWidth) / 2 + M.controlsWidth - M.swatch / 2,
            y: size.height - insets.bottom - M.controlsBottom - M.swatch / 2
        )
        return TimelineView(.animation) { timeline in
            let _ = engine.step(to: timeline.date, size: size, autoPlay: auto, reducedMotion: reduceMotion)
            ZStack(alignment: .bottom) {
                Canvas { context, canvasSize in
                    draw(in: &context, size: canvasSize, swatch: swatch)
                }
                .frame(width: size.width, height: size.height)
                .contentShape(Rectangle())
                .gesture(
                    // One recogniser stands in for the race of a painting pan (after 4 pt) and a tap.
                    DragGesture(minimumDistance: 0)
                        .updating($touching) { _, state, _ in state = true }
                        .onChanged { engine.touchChanged($0, reducedMotion: reduceMotion) }
                        .onEnded { engine.touchEnded($0) }
                )
                .onChange(of: touching) { _, now in
                    // A drag the system cancelled (a swipe from the edge, an alert) never reports
                    // its end; after a normal end (onEnded has run) this does nothing.
                    if !now { engine.touchCancelled() }
                }
                .accessibilityElement()
                .accessibilityLabel("Truchet tiles")

                controls
                    .padding(.bottom, insets.bottom + M.controlsBottom)
            }
            .frame(width: size.width, height: size.height)
        }
    }

    // MARK: Controls

    private var controls: some View {
        HStack(spacing: M.controlsGap) {
            ZStack(alignment: .leading) {
                TruchetWaveTilesGlass(cornerRadius: (M.segmentHeight + M.barPad * 2) / 2)
                Capsule()
                    .fill(Color.white.opacity(0.9))
                    .shadow(color: .black.opacity(0.25), radius: 4, y: 2)
                    .frame(width: M.segmentWidth, height: M.segmentHeight)
                    .offset(x: M.barPad + engine.thumb.value * M.segmentWidth)
                HStack(spacing: 0) {
                    ForEach(M.styles.indices, id: \.self) { i in
                        let on = i == engine.selected
                        Text(M.styles[i])
                            .font(.system(size: 14, weight: .semibold))
                            .kerning(0.1)
                            .foregroundStyle(on ? Color(hex: 0x141821) : Color.white.opacity(0.78))
                            .lineLimit(1)
                            .frame(width: M.segmentWidth, height: M.segmentHeight)
                            .contentShape(Rectangle())
                            .gesture(pressIn { engine.pick(i, reducedMotion: reduceMotion) })
                            .accessibilityElement()
                            .accessibilityLabel(M.styles[i])
                            .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
                    }
                }
                .padding(M.barPad)
            }
            .fixedSize()

            let next = palette(Int(ceil(engine.palette.value - 0.001)) + 1)
            ZStack {
                TruchetWaveTilesGlass(cornerRadius: M.swatch / 2)
                Circle().fill(M.color(next.ink, next.ink, 0)).frame(width: 24, height: 24)
                Circle().fill(M.color(next.ground, next.ground, 0)).frame(width: 12, height: 12)
            }
            .frame(width: M.swatch, height: M.swatch)
            .contentShape(Circle().inset(by: -8))
            .gesture(pressIn { engine.cycle() })
            .accessibilityElement()
            .accessibilityLabel("Next palette")
            .accessibilityAddTraits(.isButton)
        }
    }

    /// Acts the moment a finger lands, like `onPressIn`.
    /// The pressed flag is a gesture state, so a press the system cancels cannot leave it stuck on
    /// (which would make every control dead).
    private func pressIn(_ action: @escaping () -> Void) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($pressing) { _, state, _ in
                guard !state else { return }
                state = true
                action()
            }
    }

    // MARK: Drawing

    private struct Scene {
        var lines = Path()
        var fills = Path()
        var shadows = Path()
        var faces = Path()
        var edges = Path()
        var liftedLines = Path()
        var liftedFills = Path()
        var most = 0.0
        var fill = 0.0
        var width = 0.0
    }

    private func draw(in context: inout GraphicsContext, size: CGSize, swatch: CGPoint) {
        let count = engine.count
        guard count > 0, engine.angle.count == count else { return }
        let side = engine.side
        let top = engine.top
        let look = M.style(engine.morphValue)

        // Resting tiles share one path; turning ones rise as slabs, batched too, so a wave of forty
        // turning tiles still costs a handful of draws.
        var scene = Scene(fill: look.fill, width: look.width * side)
        for i in 0..<count {
            let rad = engine.angle[i] * .pi / 180
            let cos = Foundation.cos(rad)
            let sin = Foundation.sin(rad)
            let cx = (Double(i % M.columns) + 0.5) * side
            let cy = top + (Double(i / M.columns) + 0.5) * side
            let off = abs(engine.angle[i] - Double(engine.turns[i]) * 90)
            if off <= 0.5 {
                Self.addMotif(&scene.lines, cx, cy, side, cos, sin, 1, look.reach, look.round, false)
                if look.fill > 0.001 { Self.addMotif(&scene.fills, cx, cy, side, cos, sin, 1, look.reach, look.round, true) }
                continue
            }
            let lift = Foundation.sin(.pi * M.clamp01(1 - off / 90))
            scene.most = max(scene.most, lift)
            let scale = 1 + M.lift * lift
            let half = side * scale / 2
            Self.addSquare(&scene.shadows, cx, cy + M.liftShadow * lift, half * (0.94 + 0.06 * lift), cos, sin)
            Self.addSquare(&scene.faces, cx, cy, half, cos, sin)
            // Only a tile well off the ground gets a light edge; one settling in blends back in.
            if lift > 0.3 { Self.addSquare(&scene.edges, cx, cy, half, cos, sin) }
            Self.addMotif(&scene.liftedLines, cx, cy, side, cos, sin, scale, look.reach, look.round, false)
            if look.fill > 0.001 { Self.addMotif(&scene.liftedFills, cx, cy, side, cos, sin, scale, look.reach, look.round, true) }
        }

        // A new palette grows out of the swatch button as a circle over the old one.
        let p = engine.palette.value
        let from = Int(floor(p))
        let grow = p - Double(from)
        let a = palette(from)
        paint(scene, ground: a.ground, ink: a.ink, size: size, in: context)
        if grow > 0.001 {
            let b = palette(from + 1)
            let reach = hypot(max(swatch.x, size.width - swatch.x), swatch.y) + 20
            let r = max(0.1, reach * grow)
            let circle = Path(ellipseIn: CGRect(x: swatch.x - r, y: swatch.y - r, width: r * 2, height: r * 2))
            var inside = context
            inside.clip(to: circle)
            paint(scene, ground: b.ground, ink: b.ink, size: size, in: inside)
            context.drawLayer { layer in
                layer.addFilter(.blur(radius: 8))
                layer.stroke(circle, with: .color(M.color(b.ink, b.ink, 0, 0.5 * (1 - grow))), lineWidth: 3)
            }
        }
        let ink = palette(grow > 0.5 ? from + 1 : from).ink
        let rect = CGRect(origin: .zero, size: size)
        context.fill(Path(rect), with: .shader(ShaderLibrary.truchetWaveTilesGrain(.float(M.grainStrength)), bounds: rect))

        // A faint ring rides each wave front.
        for wave in engine.waves {
            let age = engine.elapsed - wave.at
            if age > M.ringLife { continue }
            let r = age * M.waveSpeed
            let ring = Path(ellipseIn: CGRect(x: wave.x - r, y: wave.y - r, width: r * 2, height: r * 2))
            context.drawLayer { layer in
                layer.addFilter(.blur(radius: 16))
                layer.stroke(ring, with: .color(M.color(ink, ink, 0, 0.16 * (1 - age / M.ringLife))), lineWidth: 10)
            }
        }
    }

    /// The whole grid in one palette: ground, resting ink, then the turning tiles as lifted slabs.
    private func paint(_ scene: Scene, ground: [Double], ink: [Double], size: CGSize, in context: GraphicsContext) {
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(M.color(ground, ground, 0)))
        drawInk(scene.lines, scene.fills, width: scene.width, ink: ink, fill: scene.fill, in: context)
        guard scene.most > 0 else { return }
        context.drawLayer { layer in
            // Skia's blur sigma of 5 pt; a SwiftUI blur radius spreads about half as far per unit.
            layer.addFilter(.blur(radius: 10))
            layer.fill(scene.shadows, with: .color(.black.opacity(0.34)))
        }
        context.fill(scene.faces, with: .color(M.color(ground, ink, 0.05 * scene.most)))
        context.stroke(scene.edges, with: .color(M.color(ink, ink, 0, 0.14)), lineWidth: 1)
        drawInk(scene.liftedLines, scene.liftedFills, width: scene.width * (1 + M.lift * 0.5), ink: ink, fill: scene.fill, in: context)
    }

    /// Draws a motif path as glazed ink: a soft shadow under it, the ink, and a sheen along its top.
    private func drawInk(_ lines: Path, _ fills: Path, width: Double, ink: [Double], fill: Double, in context: GraphicsContext) {
        if fill > 0.001 {
            var shadow = context
            shadow.translateBy(x: 0, y: 1.2)
            shadow.fill(fills, with: .color(.black.opacity(0.2)))
            let solid = M.color(ink, ink, 0, fill)
            context.fill(fills, with: .color(solid))
            // A hairline in the same colour closes the seams between neighbouring triangles.
            context.stroke(fills, with: .color(solid), lineWidth: 1.1)
        }
        guard width > 0.2 else { return }
        var shadow = context
        shadow.translateBy(x: 0, y: 1.4)
        shadow.stroke(lines, with: .color(.black.opacity(0.2)), style: StrokeStyle(lineWidth: width + 1, lineCap: .round))
        context.stroke(lines, with: .color(M.color(ink, ink, 0)), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
        var sheen = context
        sheen.translateBy(x: -width * 0.12, y: -width * 0.18)
        sheen.stroke(lines, with: .color(.white.opacity(0.22)), style: StrokeStyle(lineWidth: width * 0.28, lineCap: .round))
    }

    /// Adds a tile's motif: the edges of two corner caps (top left and bottom right), turned by
    /// (cos, sin) about the centre and scaled by `scale`. `closed` adds the first cap as a filled shape.
    private static func addMotif(_ path: inout Path, _ cx: Double, _ cy: Double, _ side: Double, _ cos: Double, _ sin: Double,
                                 _ scale: Double, _ reach: Double, _ round: Double, _ closed: Bool) {
        let half = side / 2
        let r = reach * side
        // Filled, only the top-left cap counts: with the caps reaching the far corners it is half
        // the tile, the classic triangle tile.
        for k in 0..<(closed ? 1 : 2) {
            let sign: Double = k == 0 ? -1 : 1
            let ox = sign * half
            let oy = sign * half
            let p0x = ox - sign * r
            let p0y = oy
            let p1x = ox
            let p1y = oy - sign * r
            // Control points: along the tangents for a quarter circle, along the chord for a line.
            let a0x = p0x + ((p1x - p0x) / 3) * (1 - round)
            let a0y = p0y + (-sign * M.kappa * r) * round + ((p1y - p0y) / 3) * (1 - round)
            let a1x = p1x + (-sign * M.kappa * r) * round + ((p0x - p1x) / 3) * (1 - round)
            let a1y = p1y + ((p0y - p1y) / 3) * (1 - round)
            func at(_ x: Double, _ y: Double) -> CGPoint {
                let sx = x * scale
                let sy = y * scale
                return CGPoint(x: cx + sx * cos - sy * sin, y: cy + sx * sin + sy * cos)
            }
            if closed {
                path.move(to: at(ox, oy))
                path.addLine(to: at(p0x, p0y))
            } else {
                path.move(to: at(p0x, p0y))
            }
            path.addCurve(to: at(p1x, p1y), control1: at(a0x, a0y), control2: at(a1x, a1y))
            if closed { path.closeSubpath() }
        }
    }

    /// Adds a square of half side `half` centred on (cx, cy), turned by (cos, sin).
    private static func addSquare(_ path: inout Path, _ cx: Double, _ cy: Double, _ half: Double, _ cos: Double, _ sin: Double) {
        let xs = [-half, half, half, -half]
        let ys = [-half, -half, half, half]
        for k in 0..<4 {
            let p = CGPoint(x: cx + xs[k] * cos - ys[k] * sin, y: cy + xs[k] * sin + ys[k] * cos)
            if k == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        path.closeSubpath()
    }
}

/// Dark frosted glass with a hairline rim, standing in for the React Native blur view.
private struct TruchetWaveTilesGlass: View {
    var cornerRadius: CGFloat

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .circular)
        ZStack {
            shape.fill(.ultraThinMaterial)
            shape.fill(Color.rgba(10, 12, 18, 0.28))
        }
        .environment(\.colorScheme, .dark)
        .overlay(shape.strokeBorder(Color.white.opacity(0.18), lineWidth: 1 / 3))
    }
}
