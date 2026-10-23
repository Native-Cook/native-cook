import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

/// A focus timer with an hourglass as its clock: set the minutes on the ruler, turn the glass over
/// and a few thousand grains of terracotta sand, simulated one cell at a time, run through the neck at
/// the rate that empties it on time. The glass stands on a table in afternoon light, casting a long
/// shadow that brightens where light passes through the empty bulb. Turning it mid-session runs the
/// sand back.
struct SandHourglass: View {
    /// Sets a session on the ruler, turns the glass over, lets it run out and rests, on a loop.
    var autoPlay = false
    /// The heading and the task under it.
    var title = "Focus"
    var task = "Draft the quarterly letter"
    /// Seed of the grains' shades and of the sand's random slides.
    var seed = 20261014

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = SandHourglassEngine()

    private typealias M = SandHourglassMetrics
    private typealias Sand = SandHourglassSand

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
        let layout = SandHourglassLayout(size: size, top: insets.top, bottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        return ZStack(alignment: .topLeading) {
            // The room, the table and the heading never move: drawn once.
            Canvas { ctx, _ in
                SandHourglassDrawing.room(ctx, layout: layout, title: title, task: task, top: insets.top)
            }
            .frame(width: size.width, height: size.height)
            .allowsHitTesting(false)

            TimelineView(.animation) { timeline in
                let frame = engine.step(to: timeline.date, seed: seed, autoPlay: auto, reducedMotion: reduceMotion)
                scene(frame: frame, layout: layout)
            }
            .allowsHitTesting(false)

            SandHourglassTouchSurface(
                engine: engine,
                center: CGPoint(x: layout.cx, y: layout.cy),
                glassHalf: CGSize(width: M.capHalfW * layout.scale, height: M.halfHeight * layout.scale),
                rulerY: layout.rulerY
            )
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(Color(hex: SandHourglassPalette.wall))
        .clipped()
        .accessibilityElement()
        .accessibilityLabel("Focus timer. Set the minutes on the ruler, then turn the hourglass over to start.")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { engine.accessibilityFlip() }
    }

    @ViewBuilder
    private func scene(frame: SandHourglassFrame, layout: SandHourglassLayout) -> some View {
        let a = frame.angle
        let sin2 = sin(a) * sin(a)
        let scale = layout.scale * (reduceMotion ? 1 : 1 - M.lift * sin2)
        let cosA = cos(a)
        let sinA = sin(a)
        let lx = cosA * M.lightX + sinA * M.lightY
        let ly = -sinA * M.lightX + cosA * M.lightY
        let center = CGPoint(x: layout.cx, y: layout.cy)
        let sweep = reduceMotion || frame.doneAge < 0 ? -1 : frame.doneAge / M.sweepFor
        ZStack(alignment: .topLeading) {
            // The shadow on the table and the inside of the glass behind the sand.
            Canvas { ctx, _ in
                SandHourglassDrawing.shadow(ctx, layout: layout, frame: frame, scale: scale, sin2: sin2)
                var glass = ctx
                glass.translateBy(x: center.x, y: center.y)
                glass.rotate(by: .radians(a))
                glass.scaleBy(x: scale, y: scale)
                SandHourglassDrawing.glassBack(glass)
            }
            .frame(width: layout.width, height: layout.height)

            // The grains: one bead per cell, lit from the upper left of the screen.
            Rectangle()
                .fill(ShaderLibrary.sandHourglassGrains(.data(frame.pixels), .float(Sand.cell), .float2(lx, ly)))
                .frame(width: Double(Sand.cols) * Sand.cell, height: Double(Sand.rows) * Sand.cell)
                .scaleEffect(scale)
                .rotationEffect(.radians(a))
                .position(center)

            // The wall, its highlights and the caps, then the timer and the ruler.
            Canvas { ctx, _ in
                var glass = ctx
                glass.translateBy(x: center.x, y: center.y)
                glass.rotate(by: .radians(a))
                glass.scaleBy(x: scale, y: scale)
                SandHourglassDrawing.glassFront(glass, angle: a, sweep: sweep)
                SandHourglassDrawing.timer(ctx, layout: layout, frame: frame)
                SandHourglassDrawing.ruler(ctx, layout: layout, frame: frame)
            }
            .frame(width: layout.width, height: layout.height)
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
    }
}

enum SandHourglassPalette {
    static let wallLight: UInt32 = 0xf6f0e6
    static let wall: UInt32 = 0xece3d5
    static let wallDeep: UInt32 = 0xddd1bf
    static let tableNear: UInt32 = 0xd9cab4
    static let tableFar: UInt32 = 0xe4d8c6
    static let ink: UInt32 = 0x2a2119
    static let muted: UInt32 = 0x8b7a66
    static let accent: UInt32 = 0xc4532a
    static let shadow: UInt32 = 0x4a3420
}

/// Where everything sits, in pt.
struct SandHourglassLayout {
    let width: Double
    let height: Double
    let cx: Double
    let cy: Double
    let scale: Double
    let baseY: Double
    let horizonY: Double
    let timerY: Double
    let rulerY: Double

    init(size: CGSize, top: Double, bottom: Double) {
        typealias M = SandHourglassMetrics
        width = size.width
        height = size.height
        rulerY = size.height - bottom - 70
        let bandTop = top + 96
        let bandBottom = rulerY - 54 - 112
        scale = max(0.5, min(1.08, (bandBottom - bandTop) / (2 * M.halfHeight)))
        let spare = bandBottom - bandTop - 2 * M.halfHeight * scale
        cx = size.width / 2
        cy = bandTop + spare * 0.4 + M.halfHeight * scale
        baseY = cy + M.halfHeight * scale
        horizonY = baseY - 64 * scale
        timerY = baseY + 82
    }
}

/// The glass's outline from the grain grid's profile: inner edge, outer edge, highlight strips and
/// the silhouette's half-width from the base up, for the shadow.
private struct SandHourglassGeometry {
    let inner: Path
    let outer: Path
    // Highlights for the left half of the lower bulb; drawn mirrored for the other three quarters.
    let sheen: Path
    let streak: Path
    let rim: Path
    let window: Path
    let window2: Path
    let profile: [Double]

    static let shared = SandHourglassGeometry()
    static let profileSteps = 64

    private init() {
        typealias M = SandHourglassMetrics
        let h = M.innerHalfH
        let steps = 140
        func half(_ t: Double) -> Double { (SandHourglassSand.halfWidth(t) + 0.5) * SandHourglassSand.cell }
        var side: [CGPoint] = []
        var outerSide: [CGPoint] = []
        for i in 0...steps {
            let y = -h + 2 * h * Double(i) / Double(steps)
            let t = abs(y) / h
            let w = half(t)
            // The wall keeps its thickness along the slope of the funnel.
            let slope = (half(min(1, t + 0.01)) - half(max(0, t - 0.01))) / (0.02 * h)
            side.append(CGPoint(x: w, y: y))
            outerSide.append(CGPoint(x: w + M.glassWall * (1 + slope * slope).squareRoot(), y: y))
        }
        func trace(_ side: [CGPoint], capOut: Double) -> Path {
            var p = Path()
            p.move(to: CGPoint(x: -side[0].x, y: -h - capOut))
            p.addLine(to: CGPoint(x: side[0].x, y: -h - capOut))
            for point in side { p.addLine(to: point) }
            p.addLine(to: CGPoint(x: side[side.count - 1].x, y: h + capOut))
            p.addLine(to: CGPoint(x: -side[side.count - 1].x, y: h + capOut))
            for point in side.reversed() { p.addLine(to: CGPoint(x: -point.x, y: point.y)) }
            p.closeSubpath()
            return p
        }
        inner = trace(side, capOut: 0)
        outer = trace(outerSide, capOut: M.capGap)

        // Strips along the left wall of the lower bulb, following its curve.
        func strip(_ from: Double, _ to: Double, _ inset: (Double) -> Double) -> Path {
            var p = Path()
            for i in 0...40 {
                let t = from + (to - from) * Double(i) / 40
                let point = CGPoint(x: -inset(half(t)), y: t * h)
                if i == 0 { p.move(to: point) } else { p.addLine(to: point) }
            }
            return p
        }
        sheen = strip(0.24, 0.9) { $0 * 0.78 }
        streak = strip(0.34, 0.84) { $0 - 3.2 }
        rim = strip(0.12, 0.96) { $0 - 1.4 }
        // A window behind the viewer, reflected in the bulb as two tall panes.
        window = strip(0.36, 0.8) { $0 * 0.5 }
        window2 = strip(0.38, 0.78) { $0 * 0.38 }

        var prof: [Double] = []
        for i in 0...Self.profileSteps {
            let hh = 2 * M.halfHeight * Double(i) / Double(Self.profileSteps)
            let y = abs(M.halfHeight - hh)
            if y > M.innerHalfH + M.capGap + M.collarH {
                prof.append(M.capHalfW)
            } else if y > M.innerHalfH {
                prof.append(half(1) + 7)
            } else {
                prof.append(half(min(1, y / M.innerHalfH)) + M.glassWall)
            }
        }
        profile = prof
    }
}

/// Everything drawn, as plain functions on a graphics context.
private enum SandHourglassDrawing {
    typealias M = SandHourglassMetrics
    typealias P = SandHourglassPalette

    /// A stroke, softened by a Gaussian blur of `blur` (a Skia sigma, as a SwiftUI radius).
    static func stroke(_ ctx: GraphicsContext, _ path: Path, _ color: UInt32, _ alpha: Double, _ width: Double, blur: Double = 0) {
        guard alpha > 0.003 else { return }
        let shading = GraphicsContext.Shading.color(Color(hex: color, opacity: min(1, alpha)))
        if blur > 0 {
            ctx.drawLayer { layer in
                layer.addFilter(.blur(radius: blur))
                layer.stroke(path, with: shading, lineWidth: width)
            }
        } else {
            ctx.stroke(path, with: shading, lineWidth: width)
        }
    }

    static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    /// A horizontal cylinder gradient across [x0, x1]: dark at both ends, brightest at `light`
    /// (0…1 across), so a turned part looks round and lit from the left of the screen.
    static func turned(_ x0: Double, _ x1: Double, _ light: Double, _ dark: UInt32, _ mid: UInt32, _ bright: UInt32) -> GraphicsContext.Shading {
        .linearGradient(
            Gradient(stops: [
                .init(color: Color(hex: dark), location: 0),
                .init(color: Color(hex: mid), location: light - 0.22),
                .init(color: Color(hex: bright), location: light),
                .init(color: Color(hex: mid), location: light + 0.26),
                .init(color: Color(hex: dark), location: 1),
            ]),
            startPoint: CGPoint(x: x0, y: 0),
            endPoint: CGPoint(x: x1, y: 0)
        )
    }

    /// Text whose baseline sits on `y`, as Skia draws it, centred on `x` or starting at it.
    static func text(_ ctx: GraphicsContext, _ string: String, x: Double, y: Double, size: Double, weight: Font.Weight, uiWeight: UIFont.Weight, color: UInt32, alpha: Double, centred: Bool = true, mono: Bool = false) {
        guard alpha > 0.005 else { return }
        let font = UIFont.systemFont(ofSize: size, weight: uiWeight)
        var swiftFont = Font.system(size: size, weight: weight)
        if mono { swiftFont = swiftFont.monospacedDigit() }
        let t = Text(string).font(swiftFont).foregroundStyle(Color(hex: color, opacity: min(1, alpha)))
        ctx.draw(t, at: CGPoint(x: x, y: y - font.descender), anchor: centred ? .bottom : .bottomLeading)
    }

    /// The room: a warm wall lit from the left, the table the glass stands on, and the heading.
    static func room(_ ctx: GraphicsContext, layout l: SandHourglassLayout, title: String, task: String, top: Double) {
        let size = CGSize(width: l.width, height: l.height)
        ctx.fill(
            Path(CGRect(origin: .zero, size: size)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(hex: P.wallLight), location: 0),
                    .init(color: Color(hex: P.wall), location: 0.5),
                    .init(color: Color(hex: P.wallDeep), location: 1),
                ]),
                center: CGPoint(x: l.width * 0.1, y: l.cy - 120),
                startRadius: 0,
                endRadius: max(l.width, l.height) * 0.9
            )
        )
        ctx.fill(
            Path(CGRect(x: 0, y: l.horizonY, width: l.width, height: l.height - l.horizonY)),
            with: .linearGradient(
                Gradient(colors: [Color(hex: P.tableFar), Color(hex: P.tableNear)]),
                startPoint: CGPoint(x: 0, y: l.horizonY),
                endPoint: CGPoint(x: 0, y: l.height)
            )
        )
        // The table's back edge, softened.
        stroke(ctx, line(0, l.horizonY, l.width, l.horizonY), 0xbfae96, 0.45, 1, blur: 0.6)
        stroke(ctx, line(0, l.horizonY + 1.2, l.width, l.horizonY + 1.2), 0xfffaf2, 0.5, 1, blur: 0.4)
        text(ctx, title, x: 24, y: top + 44, size: 30, weight: .bold, uiWeight: .bold, color: P.ink, alpha: 1, centred: false)
        text(ctx, task, x: 24, y: top + 68, size: 15, weight: .medium, uiWeight: .medium, color: P.muted, alpha: 1, centred: false)
    }

    /// The shadow on the table, lying to the right, and the light the empty glass lets through it.
    static func shadow(_ ctx: GraphicsContext, layout l: SandHourglassLayout, frame: SandHourglassFrame, scale s: Double, sin2: Double) {
        let geo = SandHourglassGeometry.shared
        let shade = 1 - 0.85 * sin2
        guard shade > 0.02 else { return }
        let bottomY = l.cy + M.halfHeight * s
        let rise = max(0, l.baseY - bottomY)
        let n = geo.profile.count
        var outline = Path()
        for i in 0..<n {
            let h = rise + 2 * M.halfHeight * Double(i) / Double(n - 1) * s
            let r = geo.profile[i] * s * M.shadowDepth
            let p = CGPoint(x: l.cx + h * M.shadowRun, y: l.baseY - h * M.shadowRise - r)
            if i == 0 { outline.move(to: p) } else { outline.addLine(to: p) }
        }
        for i in stride(from: n - 1, through: 0, by: -1) {
            let h = rise + 2 * M.halfHeight * Double(i) / Double(n - 1) * s
            let r = geo.profile[i] * s * M.shadowDepth
            outline.addLine(to: CGPoint(x: l.cx + h * M.shadowRun, y: l.baseY - h * M.shadowRise + r))
        }
        outline.closeSubpath()
        ctx.drawLayer { layer in
            layer.addFilter(.blur(radius: 4.5 + 6 * sin2))
            layer.fill(outline, with: .color(Color(hex: P.shadow, opacity: 0.24 * shade)))
        }
        // Where light passes through an empty bulb it gathers into a warm, brighter patch.
        let grains = Double(SandHourglassSand.grains)
        let bulbs = [
            (M.halfHeight + M.innerHalfH * 0.5, 1 - Double(frame.upper) / grains),
            (M.halfHeight - M.innerHalfH * 0.5, 1 - Double(frame.lower) / grains),
        ]
        for (height, empty) in bulbs {
            let h = rise + height * s
            let gx = l.cx + h * M.shadowRun
            let gy = l.baseY - h * M.shadowRise
            let alpha = 0.55 * empty * shade
            guard alpha > 0.005 else { continue }
            ctx.drawLayer { layer in
                layer.addFilter(.blur(radius: 5))
                layer.fill(Path(ellipseIn: CGRect(x: gx - 34 * s, y: gy - 7 * s, width: 68 * s, height: 14 * s)), with: .color(Color(hex: 0xfff4dc, opacity: alpha)))
            }
        }
        // Contact shadow under the base.
        ctx.drawLayer { layer in
            layer.addFilter(.blur(radius: 3))
            layer.fill(
                Path(ellipseIn: CGRect(x: l.cx - M.capHalfW * s * 1.02, y: l.baseY - 4, width: M.capHalfW * s * 2.04, height: 9)),
                with: .color(Color(hex: P.shadow, opacity: 0.34 * shade * shade))
            )
        }
    }

    /// The glass body: a little brighter than the room, darker towards the wall where it is thickest.
    static func glassBack(_ ctx: GraphicsContext) {
        let geo = SandHourglassGeometry.shared
        ctx.fill(geo.inner, with: .color(.white.opacity(0.2)))
        var clipped = ctx
        clipped.clip(to: geo.inner)
        stroke(clipped, geo.inner, 0x7b6650, 0.2, 12, blur: 7)
    }

    /// The wall, the strips the light makes, the neck's glint, the end-of-session sweep and the caps.
    static func glassFront(_ ctx: GraphicsContext, angle: Double, sweep: Double) {
        let geo = SandHourglassGeometry.shared
        let cosA = cos(angle)
        // Where the turned parts are brightest (0…1 across) and how much each side of the glass
        // faces the light: the light stays on the left of the screen as the glass turns.
        let light = 0.5 - 0.17 * cosA
        let leftLit = (1 + cosA) / 2

        stroke(ctx, geo.outer, 0x6e5844, 0.5, 1.1)
        stroke(ctx, geo.inner, 0x6e5844, 0.2, 0.8)
        for q in 0..<4 {
            let sx: Double = q % 2 == 0 ? 1 : -1
            let sy: Double = q < 2 ? 1 : -1
            var quarter = ctx
            quarter.scaleBy(x: sx, y: sy)
            let lit = sx > 0 ? leftLit : 1 - leftLit
            stroke(quarter, geo.sheen, 0xffffff, 0.32 * lit, 8, blur: 3.5)
            stroke(quarter, geo.window, 0xffffff, 0.5 * lit, 4.5, blur: 1.1)
            stroke(quarter, geo.window2, 0xffffff, 0.32 * lit, 3, blur: 1.1)
            stroke(quarter, geo.streak, 0xffffff, 0.85 * lit + 0.08, 1.4, blur: 0.3)
            stroke(quarter, geo.rim, 0xfffaf0, 0.16 + 0.3 * (1 - lit), 1.3, blur: 0.6)
        }
        // A small glint where the glass pinches into the neck.
        let glintX = -4.5 * cosA
        stroke(ctx, Path(ellipseIn: CGRect(x: glintX - 1.2, y: -7, width: 2.4, height: 14)), 0xffffff, 0.8, 1, blur: 1.2)

        // The end of a session: a band of light passes across the glass, left to right on screen.
        if sweep >= 0 && sweep <= 1 {
            var band = ctx
            band.clip(to: geo.outer)
            let dir: Double = cosA >= 0 ? 1 : -1
            let x = dir * (-150 + 300 * sweep)
            band.fill(
                Path(CGRect(x: -200, y: -M.innerHalfH - 10, width: 400, height: 2 * M.innerHalfH + 20)),
                with: .linearGradient(
                    Gradient(colors: [.white.opacity(0), Color(hex: 0xfffaee, opacity: 0.62), .white.opacity(0)]),
                    startPoint: CGPoint(x: x - 46 * dir, y: -60 * dir),
                    endPoint: CGPoint(x: x + 46 * dir, y: 60 * dir)
                )
            )
        }

        cap(ctx, side: -1, light: light, cosA: cosA)
        cap(ctx, side: 1, light: light, cosA: cosA)
    }

    /// One end: a narrow collar where the glass is set, then a pale oak cap with a little grain.
    private static func cap(_ ctx: GraphicsContext, side: Double, light: Double, cosA: Double) {
        // `side` is -1 for the cap above the glass's own middle, 1 below. All y here grow outwards.
        let collarNear = M.innerHalfH + M.capGap
        let capNear = collarNear + M.collarH
        let capFar = capNear + M.capH
        func span(_ near: Double, _ far: Double) -> Double { side < 0 ? -far : near }
        let collarHalf = (SandHourglassSand.halfWidth(1) + 0.5) * SandHourglassSand.cell + 7
        ctx.fill(
            Path(roundedRect: CGRect(x: -collarHalf, y: span(collarNear - 1, capNear + 1), width: 2 * collarHalf, height: M.collarH + 2), cornerRadius: 1.5, style: .circular),
            with: turned(-collarHalf, collarHalf, light, 0x5a3f27, 0x8d6842, 0xcfa97c)
        )
        let slabRect = CGRect(x: -M.capHalfW, y: span(capNear, capFar), width: 2 * M.capHalfW, height: M.capH)
        let slab = Path(roundedRect: slabRect, cornerRadius: M.capRadius, style: .circular)
        ctx.fill(slab, with: turned(-M.capHalfW, M.capHalfW, light, 0x7a5636, 0xb88d60, 0xefd6b0))
        // Grain: a few faint lines along the slab.
        var grain = ctx
        grain.clip(to: slab)
        for g in 0..<3 {
            let y = span(capNear, capFar) + 3.5 + Double(g) * 4.2
            stroke(grain, line(-M.capHalfW + 8 + Double(g) * 11, y, M.capHalfW - 14 + Double(g) * 5, y + 0.6), 0x6b4a2c, 0.14, 0.8, blur: 0.3)
        }
        // The edge that faces the top of the screen catches the light; the other falls into shadow.
        let upFacing = max(0, side < 0 ? cosA : -cosA)
        let downFacing = max(0, side < 0 ? -cosA : cosA)
        let outerEdge = side < 0 ? -capFar + 0.7 : capFar - 0.7
        if upFacing > 0.01 { stroke(ctx, line(-M.capHalfW + 6, outerEdge, M.capHalfW - 6, outerEdge), 0xfff6e6, 0.7 * upFacing, 1.1, blur: 0.3) }
        if downFacing > 0.01 { stroke(ctx, line(-M.capHalfW + 6, outerEdge, M.capHalfW - 6, outerEdge), 0x3b2715, 0.35 * downFacing, 1.1, blur: 0.4) }
    }

    /// A label swap in two halves: the old one fades out over the first half of `t`, the new one in over the second.
    static func fadeOut(_ t: Double) -> Double { max(0, min(1, 1 - 2 * t)) }
    static func fadeIn(_ t: Double) -> Double { max(0, min(1, 2 * t - 1)) }

    /// The timer: what the ruler sets, then what is left above the neck, then a word of thanks.
    static func timer(_ ctx: GraphicsContext, layout l: SandHourglassLayout, frame: SandHourglassFrame) {
        let done = frame.doneBlend
        text(ctx, M.formatClock(frame.secondsLeft), x: l.cx, y: l.timerY, size: 64, weight: .semibold, uiWeight: .semibold, color: P.ink, alpha: fadeOut(done), mono: true)
        text(ctx, "Well done", x: l.cx, y: l.timerY, size: 64, weight: .semibold, uiWeight: .semibold, color: P.ink, alpha: fadeIn(done))
        let run = frame.runBlend
        let t = ((frame.endsAt % 1440) + 1440) % 1440
        let ends = "Ends at \(t / 60 < 10 ? "0" : "")\(t / 60):\(t % 60 < 10 ? "0" : "")\(t % 60)"
        text(ctx, "Turn the glass to begin", x: l.cx, y: l.timerY + 32, size: 15, weight: .semibold, uiWeight: .semibold, color: P.muted, alpha: fadeOut(run))
        text(ctx, ends, x: l.cx, y: l.timerY + 32, size: 15, weight: .semibold, uiWeight: .semibold, color: P.muted, alpha: fadeIn(run) * fadeOut(done))
        text(ctx, "\(frame.duration) minutes of focus", x: l.cx, y: l.timerY + 32, size: 15, weight: .semibold, uiWeight: .semibold, color: P.accent, alpha: fadeIn(done))
    }

    /// The ruler: a tick a minute, a longer one and a number every five, under a fixed needle.
    static func ruler(_ ctx: GraphicsContext, layout l: SandHourglassLayout, frame: SandHourglassFrame) {
        let show = 1 - frame.runBlend
        guard show > 0.01 else { return }
        let halfSpan = l.width / 2 - 16
        for m in Int(M.minMinutes)...Int(M.maxMinutes) {
            let x = l.cx + (Double(m) - frame.minutes) * M.rulerPx
            let d = abs(x - l.cx) / halfSpan
            if d >= 1 { continue }
            let fade = (1 - d * d) * show
            let major = m % 5 == 0
            let h = major ? 22.0 : 12.0
            ctx.stroke(
                line(x, l.rulerY - h / 2, x, l.rulerY + h / 2),
                with: .color(Color(hex: P.ink, opacity: (major ? 0.78 : 0.4) * fade)),
                style: StrokeStyle(lineWidth: major ? 2 : 1.4, lineCap: .round)
            )
            if major {
                text(ctx, "\(m)", x: x, y: l.rulerY + 34, size: 12, weight: .semibold, uiWeight: .semibold, color: P.muted, alpha: fade)
            }
        }
        ctx.stroke(
            line(l.cx, l.rulerY - 20, l.cx, l.rulerY + 20),
            with: .color(Color(hex: P.accent, opacity: show)),
            style: StrokeStyle(lineWidth: 3, lineCap: .round)
        )
        ctx.fill(Path(ellipseIn: CGRect(x: l.cx - 3.2, y: l.rulerY - 25 - 3.2, width: 6.4, height: 6.4)), with: .color(Color(hex: P.accent, opacity: show)))
    }
}

/// Reports every finger on the screen to the engine, which tells a drag on the ruler, a drag around
/// the glass, a two-finger twist and a tap apart itself (SwiftUI has no two-finger rotation that can
/// run with a one-finger drag on views rebuilt every frame).
private struct SandHourglassTouchSurface: UIViewRepresentable {
    let engine: SandHourglassEngine
    let center: CGPoint
    let glassHalf: CGSize
    let rulerY: Double

    func makeUIView(context: Context) -> Surface {
        let view = Surface()
        update(view)
        return view
    }

    func updateUIView(_ view: Surface, context: Context) {
        update(view)
    }

    private func update(_ view: Surface) {
        view.tracker.engine = engine
        view.tracker.center0 = center
        view.tracker.glassHalf = glassHalf
        view.tracker.rulerY = rulerY
    }

    final class Surface: UIView {
        let tracker = Tracker()

        override init(frame: CGRect) {
            super.init(frame: frame)
            isMultipleTouchEnabled = true
            backgroundColor = .clear
            addGestureRecognizer(tracker)
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }
    }

    /// Claims every touch on the block as soon as it lands, as the React Native gesture detector
    /// does, so a surrounding scroll view cannot take a drag away, and hands the fingers to the engine.
    final class Tracker: UIGestureRecognizer {
        weak var engine: SandHourglassEngine?
        var center0 = CGPoint.zero
        var glassHalf = CGSize.zero
        var rulerY = 0.0
        private var down = 0

        init() {
            super.init(target: nil, action: nil)
            delaysTouchesBegan = false
            delaysTouchesEnded = false
        }

        private func points(_ touches: Set<UITouch>) -> [(ObjectIdentifier, CGPoint)] {
            touches.map { (ObjectIdentifier($0), $0.location(in: view)) }
        }

        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
            down += touches.count
            engine?.touchesBegan(points(touches), time: touches.first?.timestamp ?? 0, center: center0, glassHalf: glassHalf, rulerY: rulerY)
            state = state == .possible ? .began : .changed
        }

        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
            engine?.touchesMoved(points(touches), time: touches.first?.timestamp ?? 0)
            state = .changed
        }

        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
            down -= touches.count
            engine?.touchesEnded(touches.map { ObjectIdentifier($0) }, time: touches.first?.timestamp ?? 0, cancelled: false)
            state = down <= 0 ? .ended : .changed
        }

        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
            down -= touches.count
            engine?.touchesEnded(touches.map { ObjectIdentifier($0) }, time: touches.first?.timestamp ?? 0, cancelled: true)
            state = down <= 0 ? .cancelled : .changed
        }

        override func reset() {
            super.reset()
            down = 0
        }
    }
}
