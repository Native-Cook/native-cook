import SwiftUI

/// A 3×3 pattern lock on a dark screen lit by its own glow. Drawing connects the dots with an
/// elastic, glowing line whose open end lags the finger like a rubber band; each dot pops with a
/// ripple as it is picked up, and dots passed over are picked up too. A wrong pattern turns red
/// and shakes the grid; the right one pulses, collapses into the centre and becomes an open
/// padlock with a check, then the grid comes back.
struct PatternLockInput: View {
    /// The pattern that unlocks, as dot numbers 0–8 read like a page. Defaults to a "Z".
    var pattern = PatternLockInputMetrics.patternZ
    /// Draws a wrong pattern, then the right one, on a loop of 10 s.
    var autoPlay = false
    var onUnlock: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The lock's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = PatternLockInputEngine()
    // True while the touch is down; the system resets it when it takes the touch away without onEnded.
    @GestureState private var touchDown = false

    private typealias M = PatternLockInputMetrics
    static let background: UInt32 = 0x05060a
    static let neutral: UInt32 = 0x6fe3ff
    static let wrongTone: UInt32 = 0xff4d63
    static let rightTone: UInt32 = 0x59f5a6
    private static let text = Color(hex: 0xeef3ff)
    private static let muted = Color.rgba(214, 224, 245, 0.5)

    var body: some View {
        // The block draws under the system bars and keeps its text clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(grid: PatternLockInputGrid(width: size.width, height: size.height), insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(hex: Self.background).ignoresSafeArea())
    }

    private func stage(grid: PatternLockInputGrid, insets: EdgeInsets) -> some View {
        let settings = PatternLockInputSettings(grid: grid, pattern: pattern, autoPlay: autoPlay, reducedMotion: reduceMotion, onUnlock: onUnlock)
        return TimelineView(.animation) { timeline in
            let _ = engine.step(to: timeline.date, settings: settings)
            ZStack(alignment: .topLeading) {
                Canvas { ctx, _ in draw(in: ctx, grid: grid) }
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .updating($touchDown) { _, state, _ in state = true }
                            .onChanged { engine.touchMoved($0, settings: settings) }
                            .onEnded { _ in engine.touchEnded(settings: settings) }
                    )

                VStack(spacing: 8) {
                    Text("PRIVATE NOTES")
                        .font(.custom("AvenirNext-DemiBold", fixedSize: 11))
                        .kerning(2.4)
                        .foregroundStyle(Self.muted)
                    Text("Draw to unlock")
                        .font(.custom("AvenirNext-DemiBold", fixedSize: 28))
                        .kerning(-0.4)
                        .foregroundStyle(Self.text)
                }
                .frame(width: grid.width)
                .offset(y: max(insets.top + 40, grid.centre.y - grid.spacing * 1.5 - 150))
                .allowsHitTesting(false)

                statusLine
                    .frame(width: grid.width, height: 24, alignment: .top)
                    .offset(y: grid.centre.y + grid.spacing * 1.5 + 54)
                    .allowsHitTesting(false)

                Text("Forgot pattern?")
                    .font(.custom("AvenirNext-Medium", fixedSize: 14))
                    .foregroundStyle(Self.muted)
                    .frame(width: grid.width, height: max(0, grid.height - insets.bottom - 26), alignment: .bottom)
                    .allowsHitTesting(false)
            }
            .frame(width: grid.width, height: grid.height, alignment: .topLeading)
        }
        .onChange(of: touchDown) { _, now in
            // After onEnded has had its turn: a touch that did end is already over by then.
            if !now { DispatchQueue.main.async { engine.touchCancelled(settings: settings) } }
        }
        .frame(width: grid.width, height: grid.height)
        .clipped()
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Pattern lock")
    }

    /// The status under the grid: each new message rises in over 260 ms as the old one fades.
    @ViewBuilder
    private var statusLine: some View {
        let now = engine.now
        ZStack(alignment: .top) {
            if let old = engine.oldStatus {
                let t = M.clamp((now - old.at) / 0.14, 0, 1)
                if t < 1 { status(old.copy).opacity(1 - M.inOutQuad(t)) }
            }
            let t = M.clamp((now - engine.status.at) / (reduceMotion ? 0.2 : 0.26), 0, 1)
            let e = reduceMotion ? M.inOutQuad(t) : M.out(t)
            status(engine.status.copy)
                .opacity(e)
                .offset(y: reduceMotion ? 0 : 25 * (1 - e))
        }
    }

    private func status(_ copy: PatternLockInputStatus) -> some View {
        let colour: Color = switch copy {
        case .idle: Self.muted
        case .wrong: Color(hex: Self.wrongTone)
        case .right: Color(hex: Self.rightTone)
        }
        return Text(copy.copy)
            .font(.custom("AvenirNext-Medium", fixedSize: 15))
            .kerning(0.1)
            .foregroundStyle(colour)
            .fixedSize()
    }

    // MARK: Drawing

    /// The colour of the lock's light for its state: neutral, red as `wrong` → 1, green as `right` → 1.
    private func tone() -> (r: Double, g: Double, b: Double) {
        let base = PatternLockInputColor.mix(Self.neutral, Self.wrongTone, engine.wrong.value)
        return PatternLockInputColor.mix(base, PatternLockInputColor.rgb(Self.rightTone), engine.right.value)
    }

    private func draw(in ctx: GraphicsContext, grid: PatternLockInputGrid) {
        let rect = Path(CGRect(x: 0, y: 0, width: grid.width, height: grid.height))
        ctx.fill(rect, with: .color(Color(hex: Self.background)))
        let t = tone()
        let toneColor = Color(.sRGB, red: t.r, green: t.g, blue: t.b, opacity: 1)

        // A soft aura behind the grid in the lock's colour, brighter as the pattern grows.
        var aura = ctx
        aura.opacity = 0.13 + engine.glow.value * 0.1 + engine.pulse.value * 0.12 + engine.lock.value * 0.08
        // Skia blends the gradient's colours before their alpha, so the light falls off faster
        // than SwiftUI's premultiplied blend; the stops spell that curve out.
        let dark = PatternLockInputColor.rgb(Self.background)
        let stops = (0...8).map { k -> Gradient.Stop in
            let u = Double(k) / 8
            let colour = Color(.sRGB, red: t.r + (dark.r - t.r) * u, green: t.g + (dark.g - t.g) * u, blue: t.b + (dark.b - t.b) * u, opacity: 1 - u)
            return Gradient.Stop(color: colour, location: u)
        }
        aura.fill(rect, with: .radialGradient(Gradient(stops: stops), center: grid.centre, startRadius: 0, endRadius: grid.spacing * 2.6))

        var shifted = ctx
        shifted.translateBy(x: engine.shake, y: 0)
        drawLine(in: shifted, grid: grid, tone: toneColor)
        for k in 0..<9 { drawDot(k, in: shifted, grid: grid, tone: t) }
        drawPadlock(in: shifted, grid: grid)

        // The virtual finger of autoPlay, so a recording shows what is drawing.
        let ghost = engine.ghostAlpha.value
        if ghost > 0.001 {
            var c = ctx
            c.opacity = ghost
            let f = engine.finger
            let dot = Path(ellipseIn: CGRect(x: f.x - 24, y: f.y - 24, width: 48, height: 48))
            c.fill(dot, with: .color(.rgba(255, 255, 255, 0.12)))
            c.stroke(dot, with: .color(.rgba(255, 255, 255, 0.45)), lineWidth: 1.4)
        }
    }

    /// The pattern's line: through the picked dots, the last one springing in, then the open end.
    private func linePath(grid: PatternLockInputGrid) -> Path {
        var path = Path()
        let list = engine.selected
        guard !list.isEmpty else { return path }
        let c = reduceMotion ? 0 : engine.collapse.value
        let snap = engine.snap
        var a = CGPoint.zero
        for (k, d) in list.enumerated() {
            let dot = grid.dots[d]
            var x = dot.x + (grid.centre.x - dot.x) * c
            var y = dot.y + (grid.centre.y - dot.y) * c
            if k == list.count - 1 {
                x += snap.x
                y += snap.y
            }
            if k == 0 { path.move(to: CGPoint(x: x, y: y)) } else { path.addLine(to: CGPoint(x: x, y: y)) }
            a = CGPoint(x: x, y: y)
        }
        let tail = engine.tail
        if engine.stage == 0 && (engine.drawing || hypot(tail.x - a.x, tail.y - a.y) > 0.8) {
            // A rubber band: the open end bows back against the way it is moving.
            var bx = -tail.z * M.bow
            var by = -tail.w * M.bow
            let bend = hypot(bx, by)
            if bend > M.bowMax {
                bx *= M.bowMax / bend
                by *= M.bowMax / bend
            }
            path.addQuadCurve(to: CGPoint(x: tail.x, y: tail.y), control: CGPoint(x: (a.x + tail.x) / 2 + bx, y: (a.y + tail.y) / 2 + by))
        }
        return path
    }

    private func drawLine(in context: GraphicsContext, grid: PatternLockInputGrid, tone: Color) {
        let path = linePath(grid: grid)
        guard !path.isEmpty else { return }
        let pulse = engine.pulse.value
        var ctx = context
        ctx.opacity = engine.lineAlpha.value * (1 - M.clamp(engine.collapse.value * 1.4, 0, 1))
        guard ctx.opacity > 0.001 else { return }
        func style(_ width: Double) -> StrokeStyle { StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round) }
        var glow = ctx
        glow.opacity *= 0.32
        glow.addFilter(.blur(radius: 20))
        glow.stroke(path, with: .color(tone), style: style(M.glowWidth + pulse * 14))
        var soft = ctx
        soft.opacity *= 0.55
        soft.addFilter(.blur(radius: 6))
        soft.stroke(path, with: .color(tone), style: style(7))
        ctx.stroke(path, with: .color(.rgba(240, 250, 255, 0.92)), style: style(M.coreWidth + pulse * 2.5))
    }

    /// One dot: an idle ring and core, and once hit a springy core, a halo and a ripple.
    private func drawDot(_ index: Int, in context: GraphicsContext, grid: PatternLockInputGrid, tone: (r: Double, g: Double, b: Double)) {
        let reduced = reduceMotion
        let age = engine.hits[index].map { engine.now - $0 }
        let p: Double = {
            guard let age else { return 0 }
            if reduced { return min(1, age * 8) }
            return age < 0 ? 0 : 1 - exp(-age * 13) * cos(age * 21)
        }()
        let collapse = engine.collapse.value
        let c = reduced ? 0 : collapse
        let rest = grid.dots[index]
        let x = rest.x + (grid.centre.x - rest.x) * c
        let y = rest.y + (grid.centre.y - rest.y) * c
        // The dots fade as the padlock forms out of them (and, with reduced motion, as they would collapse).
        let fade = reduced ? collapse : min(1, engine.lock.value * 3)
        var ctx = context
        ctx.opacity = max(0, 1 - fade)
        guard ctx.opacity > 0.001 else { return }
        ctx.translateBy(x: x, y: y)
        func circle(_ r: Double) -> Path { Path(ellipseIn: CGRect(x: -r, y: -r, width: r * 2, height: r * 2)) }
        let toneColor = Color(.sRGB, red: tone.r, green: tone.g, blue: tone.b, opacity: 1)
        let pulse = engine.pulse.value
        let hit = age != nil

        if hit {
            var halo = ctx
            halo.opacity *= M.clamp(p, 0, 1) * (0.55 + pulse * 0.45)
            halo.addFilter(.blur(radius: 24))
            halo.fill(circle(15 + pulse * 10), with: .color(toneColor))
        }
        var ring = ctx
        ring.opacity *= hit ? 0.45 : 1
        ring.stroke(circle(22 * (1 - 0.25 * c)), with: .color(hit ? toneColor : .rgba(232, 240, 255, 0.13)), lineWidth: 1.2)
        if let age, age <= 0.65 {
            let e = max(0, age) / 0.65
            var ripple = ctx
            ripple.opacity *= (1 - e) * (1 - e) * 0.8
            let r = reduced ? 22 : 4.5 + (46 - 4.5) * (1 - pow(1 - e, 3))
            ripple.stroke(circle(r), with: .color(toneColor), lineWidth: 2)
        }
        let coreR = 4.5 + 4.5 * p + pulse * 2.5 * (hit ? 1 : 0)
        let idle = (r: 232.0 / 255, g: 240.0 / 255, b: 1.0, a: 0.55)
        let core: Color
        if hit {
            let m = PatternLockInputColor.mixRGBA(idle, (tone.r, tone.g, tone.b, 1), M.clamp(p, 0, 1))
            core = Color(.sRGB, red: m.r, green: m.g, blue: m.b, opacity: m.a)
        } else {
            core = Color(.sRGB, red: idle.r, green: idle.g, blue: idle.b, opacity: idle.a)
        }
        ctx.fill(circle(coreR), with: .color(core))
    }

    private static let shackle: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -18, y: 0))
        p.addLine(to: CGPoint(x: -18, y: -30 + 18))
        p.addRelativeArc(center: CGPoint(x: 0, y: -12), radius: 18, startAngle: .degrees(180), delta: .degrees(180))
        p.addLine(to: CGPoint(x: 18, y: 0))
        return p
    }()

    private static let tick: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -12, y: 1))
        p.addLine(to: CGPoint(x: -3.5, y: 9.5))
        p.addLine(to: CGPoint(x: 13, y: -8))
        return p
    }()

    /// The padlock the dots collapse into: its body grows out of a dot-sized blob and sinks a
    /// little to make room for the shackle, which draws on, then lifts and swings open.
    private func drawPadlock(in context: GraphicsContext, grid: PatternLockInputGrid) {
        let reduced = reduceMotion
        let lock = engine.lock.value
        let opacity = M.clamp(reduced ? lock : lock * 3, 0, 1)
        guard opacity > 0.001 else { return }
        let e = reduced ? min(1, lock) : lock
        let blob = 20.0
        let w = blob + (66 - blob) * e
        let h = blob + (54 - blob) * e
        let body = CGRect(x: -w / 2, y: -h / 2 + 14 * e, width: w, height: h)
        let radius = blob / 2 + (15 - blob / 2) * min(1, e)
        let shape = Path(roundedRect: body, cornerRadius: max(0, radius), style: .continuous)
        let green = Color(hex: Self.rightTone)

        var ctx = context
        ctx.translateBy(x: grid.centre.x, y: grid.centre.y)
        ctx.opacity = opacity
        var halo = ctx
        halo.opacity *= 0.5 * opacity
        halo.addFilter(.blur(radius: 44))
        halo.fill(shape, with: .color(green))

        let shackleEnd = M.clamp(reduced ? lock : lock * 1.6 - 0.6, 0, 1)
        if shackleEnd > 0 {
            let o = reduced ? 0 : engine.open.value
            var s = ctx
            // Pivot on the right leg's foot, so the left leg swings out of the body as it lifts.
            s.translateBy(x: 18, y: -54.0 / 2 + 14 + 3 - 13 * o)
            s.rotate(by: .radians(-0.32 * o))
            s.translateBy(x: -18, y: 0)
            s.stroke(Self.shackle.trimmedPath(from: 0, to: shackleEnd), with: .color(green), style: StrokeStyle(lineWidth: 7.5, lineCap: .round))
        }
        ctx.fill(shape, with: .color(green))
        let checkEnd = M.clamp(engine.check.value, 0, 1)
        if checkEnd > 0 {
            var c = ctx
            c.translateBy(x: 0, y: 14)
            c.stroke(Self.tick.trimmedPath(from: 0, to: checkEnd), with: .color(Color(hex: Self.background)), style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
        }
    }
}

enum PatternLockInputColor {
    static func rgb(_ hex: UInt32) -> (r: Double, g: Double, b: Double) {
        (Double((hex >> 16) & 0xff) / 255, Double((hex >> 8) & 0xff) / 255, Double(hex & 0xff) / 255)
    }

    /// Reanimated's `interpolateColor`: each channel mixed in linear light (gamma 2.2).
    static func mix(_ from: UInt32, _ to: UInt32, _ t: Double) -> (r: Double, g: Double, b: Double) {
        mix(rgb(from), rgb(to), t)
    }

    static func mix(_ a: (r: Double, g: Double, b: Double), _ b: (r: Double, g: Double, b: Double), _ t: Double) -> (r: Double, g: Double, b: Double) {
        let m = mixRGBA((a.r, a.g, a.b, 1), (b.r, b.g, b.b, 1), t)
        return (m.r, m.g, m.b)
    }

    static func mixRGBA(_ a: (r: Double, g: Double, b: Double, a: Double), _ b: (r: Double, g: Double, b: Double, a: Double), _ t: Double) -> (r: Double, g: Double, b: Double, a: Double) {
        let t = min(1, max(0, t))
        func channel(_ x: Double, _ y: Double) -> Double {
            let lx = pow(x, 2.2), ly = pow(y, 2.2)
            return pow(lx + (ly - lx) * t, 1 / 2.2)
        }
        return (channel(a.r, b.r), channel(a.g, b.g), channel(a.b, b.b), a.a + (b.a - a.a) * t)
    }
}
