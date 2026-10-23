import SwiftUI
import UIKit

/// A spirograph on a pad of drawing paper: a clear plastic ring with inward teeth and a tinted gear
/// inside it. Dragging round the ring rolls the gear and its pen draws a hypotrochoid in real ink
/// that builds up into a pattern, while the rest of the pattern shows faintly in pencil; a release
/// keeps it rolling until friction stops it. Below, the gear, the pen hole and the ink are picked
/// from three rows (a tap on the paper also moves the pen to the next hole); Clear tears the sheet off.
struct SpirographDraw: View {
    /// Draws two layered patterns, tears the sheet off and starts again. Off by default.
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = SpirographDrawEngine()
    @State private var shown = SpirographDrawShown()

    private typealias M = SpirographDrawMetrics
    private typealias G = SpirographDrawModel

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
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let layout = SpirographDrawLayout(size: size, insets: insets)
        let auto = autoPlay && !reduceMotion
        let gear = G.gears[shown.gear]
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation) { timeline in
                let frame = engine.step(to: timeline.date, autoPlay: auto, reducedMotion: reduceMotion, scale: displayScale)
                let ink = engine.ink
                Canvas { ctx, _ in
                    SpirographDrawDrawing.scene(ctx, frame: frame, ink: ink, size: size, layout: layout, reducedMotion: reduceMotion)
                }
                .frame(width: size.width, height: size.height)
            }
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
            .gesture(
                // One recogniser stands in for the race of a pan (after 2 pt of travel) and a tap (a
                // touch lifted within 300 ms that never travelled that far).
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        engine.touchChanged(
                            location: layout.relative(value.location),
                            onPaper: layout.onPaper(value.startLocation),
                            time: value.time
                        )
                    }
                    .onEnded { engine.touchEnded(location: layout.relative($0.location), velocity: $0.velocity, time: $0.time) }
            )
            .accessibilityElement()
            .accessibilityLabel("Spirograph. Drag round the ring to roll the gear and draw; tap the paper to move the pen to the next hole.")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { engine.nextHole() }

            header(width: size.width, gear: gear)
                .offset(y: insets.top + 8)

            controls(width: size.width)
                .frame(width: size.width, height: size.height - insets.bottom - 10, alignment: .bottom)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(Color(hex: M.desk))
        .clipped()
        .onAppear {
            engine.onShow = { g, h, i in shown = SpirographDrawShown(gear: g, hole: h, ink: i) }
        }
    }

    private func header(width: Double, gear: G.Gear) -> some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Spirograph")
                    .font(.custom("AvenirNext-Bold", fixedSize: 28))
                    .foregroundStyle(Color(hex: M.text))
                Text("\(gear.teeth)-tooth gear · hole \(shown.hole + 1) · \(G.pattern(gear.teeth).petals) loops")
                    .font(.custom("AvenirNext-Medium", fixedSize: 14).monospacedDigit())
                    .foregroundStyle(Color(hex: M.muted))
                    .lineLimit(1)
            }
            .allowsHitTesting(false)
            Spacer(minLength: 0)
            Button("Clear") { engine.clear() }
                .buttonStyle(SpirographDrawPillStyle())
                .accessibilityLabel("Clear the sheet")
        }
        .padding(.horizontal, 20)
        .frame(width: width)
    }

    private func controls(width: Double) -> some View {
        let inkColor = Color(hex: G.inks[shown.ink].color)
        return VStack(spacing: 0) {
            Text("Drag around the ring to draw · Tap the paper for the next hole")
                .font(.custom("AvenirNext-Medium", fixedSize: 13))
                .foregroundStyle(Color(hex: M.muted))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(height: M.hintH, alignment: .top)
                .allowsHitTesting(false)
            row("GEAR") {
                ForEach(G.gears.indices, id: \.self) { i in
                    let g = G.gears[i]
                    let selected = i == shown.gear
                    Button { engine.pickGear(i) } label: {
                        HStack(spacing: 4) {
                            SpirographDrawGearGlyph(index: i, tint: g.tint)
                            Text("\(g.teeth)")
                                .font(.custom("AvenirNext-DemiBold", fixedSize: 15).monospacedDigit())
                                .foregroundStyle(selected ? Color.white : Color(hex: M.muted))
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 36)
                        .background(SpirographDrawOption(fill: selected ? .white.opacity(0.15) : .white.opacity(0.06), border: selected ? .white.opacity(0.3) : .clear))
                    }
                    .buttonStyle(SpirographDrawPressStyle())
                    .accessibilityLabel("\(g.teeth) tooth gear")
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
            }
            row("HOLE") {
                ForEach(G.holes.indices, id: \.self) { i in
                    let selected = i == shown.hole
                    Button { engine.pickHole(i) } label: {
                        Text("\(i + 1)")
                            .font(.custom("AvenirNext-DemiBold", fixedSize: 15).monospacedDigit())
                            .foregroundStyle(selected ? Color.white : Color(hex: M.muted))
                            .frame(maxWidth: .infinity)
                            .frame(height: 36)
                            .background(SpirographDrawOption(fill: selected ? inkColor : .white.opacity(0.06), border: selected ? .white.opacity(0.3) : .clear))
                    }
                    .buttonStyle(SpirographDrawPressStyle())
                    .accessibilityLabel("Pen hole \(i + 1)")
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
            }
            row("INK") {
                ForEach(G.inks.indices, id: \.self) { i in
                    let k = G.inks[i]
                    let selected = i == shown.ink
                    Button { engine.pickInk(i) } label: {
                        Circle()
                            .fill(Color(hex: k.color))
                            .overlay(Circle().strokeBorder(.white.opacity(0.4), lineWidth: 1.5))
                            .frame(width: 24, height: 24)
                            .frame(width: 34, height: 34)
                            .overlay(Circle().strokeBorder(selected ? Color(hex: M.text) : .clear, lineWidth: 2))
                            .frame(maxWidth: .infinity)
                            .frame(height: 40)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(SpirographDrawPressStyle())
                    .accessibilityLabel("\(k.name) ink")
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
            }
        }
        .padding(.horizontal, M.margin)
        .frame(width: width)
    }

    private func row<Content: View>(_ label: String, @ViewBuilder content: () -> Content) -> some View {
        HStack(spacing: 0) {
            Text(label)
                .font(.custom("AvenirNext-DemiBold", fixedSize: 12))
                .kerning(1.4)
                .foregroundStyle(Color(hex: M.muted))
                .frame(width: 56, alignment: .leading)
                .allowsHitTesting(false)
            HStack(spacing: 8) { content() }
        }
        .frame(height: 48)
        .overlay(alignment: .top) {
            Rectangle().fill(.white.opacity(0.1)).frame(height: 1 / 3)
        }
    }
}

/// The gear, hole and ink the controls and the status line show.
struct SpirographDrawShown {
    var gear = SpirographDrawModel.startGear
    var hole = SpirographDrawModel.startHole
    var ink = SpirographDrawModel.startInk
}

/// Where the paper sits: a square under the header, as large as the width and the room above the
/// controls allow, with the ring scaled to fit inside it.
struct SpirographDrawLayout {
    let side: Double
    let paper: CGRect
    let center: CGPoint
    let scale: Double

    init(size: CGSize, insets: EdgeInsets) {
        typealias M = SpirographDrawMetrics
        typealias G = SpirographDrawModel
        let roomTop = insets.top + M.headerH
        let roomBottom = size.height - insets.bottom - M.controlsH - M.hintH - 16
        side = max(0, min(size.width - 2 * M.margin, roomBottom - roomTop - 8))
        let x = (size.width - side) / 2
        let y = roomTop + max(0, (roomBottom - roomTop - side) / 2)
        paper = CGRect(x: x, y: y, width: side, height: side)
        center = CGPoint(x: x + side / 2, y: y + side / 2)
        scale = side > 0 ? (side / 2 - M.paperPad) / (G.ringR + G.ringBody) : 1
    }

    func relative(_ p: CGPoint) -> CGPoint { CGPoint(x: p.x - center.x, y: p.y - center.y) }

    func onPaper(_ p: CGPoint) -> Bool { side > 0 && paper.contains(p) }
}

/// A small gear, sized by its place in the set, for the gear buttons.
private struct SpirographDrawGearGlyph: View {
    let index: Int
    let tint: UInt32

    var body: some View {
        let pts = SpirographDrawModel.toothOutline(8 + index * 2, 5.4 + Double(index) * 1.25, 1, depth: 2.2)
        var outline = Path()
        outline.addLines(pts.map { CGPoint(x: $0.x + 12, y: $0.y + 12) })
        outline.closeSubpath()
        return ZStack {
            outline.fill(Color(hex: tint).opacity(0.75))
            outline.stroke(Color(hex: tint), lineWidth: 0.8)
            Circle().fill(Color(hex: SpirographDrawMetrics.desk)).frame(width: 3.2, height: 3.2)
        }
        .frame(width: 24, height: 24)
    }
}

/// An option's rounded background and border.
private struct SpirographDrawOption: View {
    let fill: Color
    let border: Color

    var body: some View {
        RoundedRectangle(cornerRadius: 10, style: .continuous)
            .fill(fill)
            .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(border, lineWidth: 1))
    }
}

/// Pressed: a little dimmer and smaller, like the React Native Pressable.
private struct SpirographDrawPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.75 : 1)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}

/// The Clear button: a hairline pill on the dark desk.
private struct SpirographDrawPillStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.custom("AvenirNext-DemiBold", fixedSize: 15))
            .foregroundStyle(Color(hex: SpirographDrawMetrics.text))
            .padding(.horizontal, 18)
            .frame(height: 38)
            .background(Capsule().fill(Color.white.opacity(0.1)))
            .overlay(Capsule().strokeBorder(Color.white.opacity(0.18), lineWidth: 1 / 3))
            .opacity(configuration.isPressed ? 0.75 : 1)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}

/// The ring, its printed ticks and the gears' outlines (with their holes cut), built once.
private struct SpirographDrawParts {
    let ring: Path
    let ticks: Path
    let gears: [Path]

    static let shared = SpirographDrawParts()

    private init() {
        typealias G = SpirographDrawModel
        func outline(_ points: [CGPoint]) -> Path {
            var p = Path()
            p.addLines(points)
            p.closeSubpath()
            return p
        }
        func circle(_ x: Double, _ y: Double, _ r: Double) -> CGRect {
            CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)
        }
        // The ring's body: between its outer rim and its inward teeth (filled even-odd).
        var ring = outline(G.toothOutline(G.ringTeeth, G.ringR, -1))
        ring.addEllipse(in: circle(0, 0, G.ringR + G.ringBody))
        self.ring = ring
        var ticks = Path()
        for k in 0..<G.ringTeeth {
            let a = Double(k) / Double(G.ringTeeth) * 2 * .pi
            let long = k % 8 == 0
            let r0 = G.ringR + G.ringBody - (long ? 11 : 7)
            let r1 = G.ringR + G.ringBody - 4
            ticks.move(to: CGPoint(x: r0 * cos(a), y: r0 * sin(a)))
            ticks.addLine(to: CGPoint(x: r1 * cos(a), y: r1 * sin(a)))
        }
        self.ticks = ticks
        gears = G.gears.map { gear in
            let radius = G.gearRadius(gear.teeth)
            // The pen holes and the hub are cut through the plastic.
            var p = outline(G.toothOutline(gear.teeth, radius, 1))
            for k in 0..<G.holes.count {
                let h = G.holeAt(Double(k), radius)
                p.addEllipse(in: circle(h.d * cos(h.psi), h.d * sin(h.psi), 3.3))
            }
            p.addEllipse(in: circle(0, 0, 5))
            return p
        }
    }
}

/// Everything drawn, as plain functions on a graphics context.
private enum SpirographDrawDrawing {
    typealias M = SpirographDrawMetrics
    typealias G = SpirographDrawModel

    private static let eo = FillStyle(eoFill: true)
    private static let labelFont = UIFont(name: "AvenirNext-DemiBold", size: 9) ?? .systemFont(ofSize: 9, weight: .semibold)

    static func stroke(_ ctx: GraphicsContext, _ path: Path, _ color: Color, _ width: Double) {
        ctx.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    /// A hex colour mixed toward black by `amount`.
    private static func darker(_ hex: UInt32, _ amount: Double, _ alpha: Double) -> Color {
        let k = 1 - amount
        return Color(
            .sRGB,
            red: Double((hex >> 16) & 255) / 255 * k,
            green: Double((hex >> 8) & 255) / 255 * k,
            blue: Double(hex & 255) / 255 * k,
            opacity: max(0, min(1, alpha))
        )
    }

    private static func paperShading() -> GraphicsContext.Shading {
        .shader(ShaderLibrary.spirographDrawPaper())
    }

    static func scene(_ ctx: GraphicsContext, frame: SpirographDrawFrame, ink: SpirographDrawInk?, size: CGSize, layout: SpirographDrawLayout, reducedMotion: Bool) {
        let side = layout.side
        guard side > 0 else { return }
        let cx = layout.center.x
        let cy = layout.center.y
        let px = layout.paper.minX
        let py = layout.paper.minY

        // The desk: dark, a little lighter under the pad.
        ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .radialGradient(
            Gradient(stops: [
                .init(color: Color(hex: 0x303136), location: 0),
                .init(color: Color(hex: 0x232428), location: 0.55),
                .init(color: Color(hex: 0x18191c), location: 1),
            ]),
            center: layout.center,
            startRadius: 0,
            endRadius: max(size.width, size.height) * 0.75
        ))

        // The pad: two sheets' edges under the top one, and its shadow on the desk.
        let sheet = Path(roundedRect: layout.paper, cornerRadius: 5)
        ctx.drawLayer { layer in
            layer.addFilter(.blur(radius: 16))
            layer.translateBy(x: 0, y: 12)
            layer.fill(sheet, with: .color(.black.opacity(0.5)))
        }
        ctx.fill(Path(roundedRect: CGRect(x: px + 1, y: py + 5, width: side - 2, height: side), cornerRadius: 5), with: .color(Color(hex: 0xC9C3B6)))
        ctx.fill(Path(roundedRect: CGRect(x: px + 0.5, y: py + 2.5, width: side - 1, height: side), cornerRadius: 5), with: .color(Color(hex: 0xE1DBCE)))

        let w = frame.tear
        if w < 0 {
            drawSheet(ctx, sheet, ink, layout)
        } else {
            // A fresh sheet waits under the one being torn off.
            ctx.fill(sheet, with: paperShading())
            var torn = ctx
            if reducedMotion {
                torn.opacity = 1 - w
                drawSheet(torn, sheet, ink, layout)
            } else {
                let e = M.easeInOut(w)
                torn.translateBy(x: -e * (side + 90), y: e * 46)
                torn.translateBy(x: cx, y: py)
                torn.rotate(by: .degrees(M.sheetTurn * e))
                torn.translateBy(x: -cx, y: -py)
                let lift = 0.28 * sin(.pi * min(1, w * 1.6))
                torn.drawLayer { layer in
                    layer.addFilter(.blur(radius: 12))
                    layer.translateBy(x: 4, y: 10)
                    layer.fill(sheet, with: .color(.black.opacity(lift)))
                }
                drawSheet(torn, sheet, ink, layout)
            }
        }

        var c = ctx
        c.translateBy(x: cx, y: cy)
        c.scaleBy(x: layout.scale, y: layout.scale)

        // The whole pattern the pen is on, faint in pencil: it morphs as the hole or the gear changes.
        let g = frame.gear
        let sw = M.easeInOut(frame.swap)
        let radius = G.gearRadius(G.gears[frame.gearFrom].teeth) * (1 - sw) + G.gearRadius(G.gears[g].teeth) * sw
        let pen = G.holeAt(frame.hole, radius)
        if frame.ghost > 0.01 {
            let turns = G.pattern(G.gears[g].teeth).turns
            let steps = turns * M.ghostStepsPerTurn
            let rate = G.spinRate(radius)
            var ghost = Path()
            for k in 0...steps {
                let d = Double(k) / Double(steps) * Double(turns) * 2 * .pi
                let p = G.penAt(frame.phi + d, frame.spin + d * rate, radius, pen.d, pen.psi)
                if k == 0 { ghost.move(to: p) } else { ghost.addLine(to: p) }
            }
            stroke(c, ghost, Color(hex: 0x4a4740, opacity: 0.2 * frame.ghost), 0.7)
        }

        ring(c)

        // The coach: an arrow sweeping round the ring ahead of the gear, while nothing is drawn yet.
        if frame.coach > 0.01 {
            let reach = G.ringR - radius
            let rho = G.ringR - 15
            let cosStart = (rho * rho + reach * reach - radius * radius) / max(1, 2 * rho * reach)
            let start = frame.phi + acos(max(-1, min(1, cosStart))) + 0.14
            let local = frame.coachPhase
            let grow = M.easeOut(local / 1.1)
            let fade = local > 1.6 ? 1 - (local - 1.6) / 0.4 : 1
            let sweep = max(0.02, M.coachSweep * grow)
            let end = start + sweep
            var arc = Path()
            let segments = 32
            for i in 0...segments {
                let a = start + sweep * Double(i) / Double(segments)
                let p = CGPoint(x: rho * cos(a), y: rho * sin(a))
                if i == 0 { arc.move(to: p) } else { arc.addLine(to: p) }
            }
            let tx = -sin(end)
            let ty = cos(end)
            let hx = rho * cos(end)
            let hy = rho * sin(end)
            for side2 in [-1.0, 1.0] {
                let ax = -tx * 0.86 + side2 * -ty * 0.5
                let ay = -ty * 0.86 + side2 * tx * 0.5
                arc.move(to: CGPoint(x: hx, y: hy))
                arc.addLine(to: CGPoint(x: hx + ax * 8, y: hy + ay * 8))
            }
            stroke(c, arc, Color(hex: 0x2b2a30, opacity: 0.5 * frame.coach * max(0, fade)), 2.2)
        }

        // The gear over the ink: while it changes, the old one fades out as the new one grows in.
        let from = frame.gearFrom
        let swapping = sw < 1 && from != g
        if swapping { gear(c, index: from, frame: frame, alpha: 1 - sw, scale: 1 - 0.08 * sw) }
        gear(c, index: g, frame: frame, alpha: swapping ? sw : 1, scale: swapping ? 0.92 + 0.08 * sw : 1)
    }

    /// The top sheet of the pad with its ink.
    private static func drawSheet(_ ctx: GraphicsContext, _ sheet: Path, _ ink: SpirographDrawInk?, _ layout: SpirographDrawLayout) {
        ctx.fill(sheet, with: paperShading())
        guard let ink else { return }
        var c = ctx
        c.clip(to: sheet)
        c.translateBy(x: layout.center.x, y: layout.center.y)
        c.scaleBy(x: layout.scale, y: layout.scale)
        // Ink soaks into the paper: the layer multiplies, so crossing colours darken like real ink.
        c.blendMode = .multiply
        let rect = CGRect(x: -M.bounds, y: -M.bounds, width: 2 * M.bounds, height: 2 * M.bounds)
        c.drawLayer { layer in
            if let image = ink.flat { layer.draw(Image(decorative: image, scale: ink.scale), in: rect) }
            for chunk in ink.chunks { stroke(layer, chunk.path, Color(hex: G.inks[chunk.ink].color), M.inkWidth) }
            if ink.live.count >= 2 {
                stroke(layer, SpirographDrawInk.polyline(ink.live), Color(hex: G.inks[ink.ink].color), M.inkWidth)
            }
        }
    }

    /// The clear plastic ring with its printed ticks and size.
    private static func ring(_ c: GraphicsContext) {
        let parts = SpirographDrawParts.shared
        c.drawLayer { layer in
            layer.addFilter(.blur(radius: 3.5))
            layer.translateBy(x: 1.5, y: 4)
            layer.fill(parts.ring, with: .color(Color(hex: 0x1a1408, opacity: 0.2)), style: eo)
        }
        c.fill(parts.ring, with: .linearGradient(
            Gradient(stops: [
                .init(color: .rgba(226, 236, 244, 0.62), location: 0),
                .init(color: .rgba(196, 212, 226, 0.4), location: 0.55),
                .init(color: .rgba(214, 226, 238, 0.55), location: 1),
            ]),
            startPoint: CGPoint(x: -M.bounds, y: -M.bounds),
            endPoint: CGPoint(x: M.bounds, y: M.bounds)
        ), style: eo)
        c.stroke(parts.ring, with: .color(.rgba(58, 76, 96, 0.5)), lineWidth: 0.9)
        // A bright bevel just inside the outer rim.
        stroke(c, circle(0, 0, G.ringR + G.ringBody - 1.4), .white.opacity(0.75), 1.2)
        stroke(c, parts.ticks, Color(hex: 0x2e3c4c, opacity: 0.5), 0.7)
        let label = c.resolve(Text("\(G.ringTeeth)").font(Font(labelFont)).foregroundStyle(Color.rgba(46, 60, 76, 0.62)))
        let base = label.firstBaseline(in: CGSize(width: 100, height: 100))
        let width = label.measure(in: CGSize(width: 100, height: 100)).width
        c.draw(label, at: CGPoint(x: -width / 2, y: -(G.ringR + G.ringBody * 0.5) + 3.5 - base), anchor: .topLeading)
    }

    /// One gear at its place on the ring, turned by `spin`, faded by `alpha`, scaled by `scale`.
    private static func gear(_ ctx: GraphicsContext, index: Int, frame: SpirographDrawFrame, alpha: Double, scale: Double) {
        if alpha <= 0.01 { return }
        let gear = G.gears[index]
        let radius = G.gearRadius(gear.teeth)
        let reach = G.ringR - radius
        let spin = frame.spin
        let outline = SpirographDrawParts.shared.gears[index]
        var c = ctx
        c.translateBy(x: reach * cos(frame.phi), y: reach * sin(frame.phi))
        c.scaleBy(x: scale, y: scale)
        // A soft shadow on the paper, cast down and to the right whatever the gear's turn.
        c.drawLayer { layer in
            layer.addFilter(.blur(radius: 2.6))
            layer.translateBy(x: 1.2, y: 3)
            layer.rotate(by: .radians(spin))
            layer.fill(outline, with: .color(Color(hex: 0x1a1408, opacity: 0.2 * alpha)), style: eo)
        }
        var body = c
        body.rotate(by: .radians(spin))
        // Tinted plastic, a little brighter toward the light on the upper left.
        body.fill(outline, with: .color(Color(hex: gear.tint, opacity: 0.5 * alpha)), style: eo)
        let lx = -cos(spin + .pi / 4) * radius
        let ly = sin(spin + .pi / 4) * radius
        body.fill(outline, with: .linearGradient(
            Gradient(colors: [.white.opacity(0.3 * alpha), .white.opacity(0)]),
            startPoint: CGPoint(x: lx, y: -ly),
            endPoint: CGPoint(x: -lx, y: ly)
        ), style: eo)
        body.stroke(outline, with: .color(darker(gear.tint, 0.35, 0.85 * alpha)), lineWidth: 0.9)
        // The pen standing in its hole: a round barrel end in its ink, lit from the upper left.
        let pen = G.holeAt(frame.hole, radius)
        let local = CGPoint(x: pen.d * cos(pen.psi), y: pen.d * sin(pen.psi))
        let p = local.applying(CGAffineTransform(rotationAngle: spin))
        let inkHex = G.inks[frame.ink].color
        c.drawLayer { layer in
            layer.addFilter(.blur(radius: 2.2))
            layer.fill(circle(p.x + 1.4, p.y + 2.4, 5.6), with: .color(.black.opacity(0.28 * alpha)))
        }
        c.fill(circle(p.x, p.y, 5.6), with: .radialGradient(
            Gradient(stops: [
                .init(color: .white.opacity(0.55 * alpha), location: 0),
                .init(color: Color(hex: inkHex, opacity: alpha), location: 0.45),
                .init(color: darker(inkHex, 0.45, alpha), location: 1),
            ]),
            center: CGPoint(x: p.x - 1.8, y: p.y - 1.8),
            startRadius: 0,
            endRadius: 7
        ))
        c.stroke(circle(p.x, p.y, 5.6), with: .color(darker(inkHex, 0.6, 0.9 * alpha)), lineWidth: 0.8)
    }
}
