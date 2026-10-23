import SwiftUI

/// A glossy blob of modelling clay on a pale paper studio sweep, lit from the upper left. It is a
/// soft body that keeps what you do to it for a while: a press sinks a dent with a fingerprint in
/// it, a drag pulls out a lump, a flick throws it against the edge of the screen where it flattens
/// and wobbles back, and a pinch squeezes it. A tap recolours the clay from the finger.
struct ClayPressBlob: View {
    /// The clays a tap cycles through, in order.
    var palette: [ClayPressBlobColour] = ClayPressBlobMetrics.palette
    /// Pokes, pulls, recolours, throws and pinches the clay on a loop.
    var autoPlay = false
    /// Called with a clay when a tap recolours it.
    var onRecolour: ((ClayPressBlobColour) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The clay lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = ClayPressBlobEngine()

    private typealias M = ClayPressBlobMetrics
    private typealias S = ClayPressBlobStyle

    /// The palette, never empty: an empty one falls back to the six clays.
    private var clays: [ClayPressBlobColour] { palette.isEmpty ? M.palette : palette }

    var body: some View {
        // The block draws under the system bars and keeps its labels clear of them itself.
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
        let settings = ClayPressBlobSettings(
            autoPlay: autoPlay && !reduceMotion,
            reducedMotion: reduceMotion,
            paletteSize: clays.count,
            width: size.width,
            height: size.height,
            top: insets.top + M.header,
            bottom: size.height - insets.bottom - M.footer,
            onRecolour: { [clays, onRecolour] index in if clays.indices.contains(index) { onRecolour?(clays[index]) } }
        )
        let hsl = clays.map { ClayPressBlobStyle.hsl($0.hex) }
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation) { timeline in
                let _ = engine.step(to: timeline.date, settings: settings)
                let frame = engine.frameCount
                ZStack(alignment: .topLeading) {
                    Canvas { context, canvasSize in
                        _ = frame
                        guard let sim = engine.sim else { return }
                        ClayPressBlobPainter.draw(&context, sim: sim, size: canvasSize, hsl: hsl)
                    }
                    let ink = inkTone(hsl)
                    header(insets: insets, ink: ink)
                    Text("Press · Pull · Flick · Pinch · Tap to recolour")
                        .font(.system(size: 12, weight: .medium))
                        .kerning(0.3)
                        .foregroundStyle(ink)
                        .opacity(0.6)
                        .multilineTextAlignment(.center)
                        .frame(width: max(0, size.width - 48))
                        .frame(width: size.width, height: size.height - insets.bottom - 26, alignment: .bottom)
                }
                .allowsHitTesting(false)
            }
            ClayPressBlobTouches(engine: engine)
        }
        .frame(width: size.width, height: size.height)
        .background(S.color(S.paper(hsl.isEmpty ? (0, 0, 0.9) : hsl[min(engine.label, hsl.count - 1)], S.paperMid)))
        .clipped()
        .accessibilityElement()
        .accessibilityLabel("Clay study, \(clays[min(engine.label, clays.count - 1)].name) clay")
    }

    /// The ink turns with the paper, over the same 0.75 s as the clay's colour change.
    private func inkTone(_ hsl: [ClayPressBlobStyle.Hsl]) -> Color {
        guard !hsl.isEmpty else { return Color(hex: 0x3A3330) }
        guard let s = engine.sim else { return S.color(S.paper(hsl[min(engine.label, hsl.count - 1)], S.paperInk)) }
        let from = hsl[min(max(s.colourFrom, 0), hsl.count - 1)]
        let to = hsl[min(max(s.colourTo, 0), hsl.count - 1)]
        return S.paperMix(from, to, S.paperInk, M.easeInOut(s.recolour))
    }

    private func header(insets: EdgeInsets, ink: Color) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("CLAY STUDY")
                Spacer(minLength: 0)
                Text("Nº 04")
            }
            .font(.system(size: 11, weight: .bold))
            .kerning(2.6)
            .foregroundStyle(ink)
            .opacity(0.72)
            ZStack(alignment: .topLeading) {
                // The clay's name crossfades: the old one leaves over 180 ms, the new one arrives
                // over 300 ms after 120 ms.
                if engine.previousLabel >= 0 && engine.previousLabel < clays.count {
                    let p = (engine.elapsed - engine.labelAt) / M.labelOut
                    if p < 1 { name(clays[engine.previousLabel], ink: ink).opacity(1 - M.quadInOut(p)) }
                }
                name(clays[min(engine.label, clays.count - 1)], ink: ink)
                    .opacity(M.quadInOut((engine.elapsed - engine.labelAt - M.labelDelay) / M.labelIn))
            }
            .frame(height: S.nameSize * 1.18, alignment: .topLeading)
        }
        .padding(.horizontal, 24)
        .padding(.top, insets.top + 18)
    }

    private func name(_ colour: ClayPressBlobColour, ink: Color) -> some View {
        Text(colour.name)
            .font(.custom("Georgia-Bold", size: S.nameSize))
            .kerning(-1)
            .foregroundStyle(ink)
            .lineLimit(1)
            .fixedSize()
    }
}

enum ClayPressBlobStyle {
    // The studio: a seamless paper sweep tinted with the clay's own hue (a tonal product shot), lit
    // from the upper left, with a fine grain. Its tones are the clay's hue at these saturations and
    // lightnesses (a share of the clay's saturation, then lightness 0…1).
    static let paperTop = (s: 0.34, l: 0.86)
    static let paperMid = (s: 0.4, l: 0.91)
    static let paperFloor = (s: 0.3, l: 0.8)
    static let paperInk = (s: 0.45, l: 0.24)
    static let grain = 0.05
    // The header: the kicker line, then the clay's name, large, in a sturdy serif.
    static let nameSize = 46.0

    // Light comes from the upper left: the inner rim darkens toward the lower right and brightens
    // toward the upper left; the sheen sits on the ring point at `sheenAngle` degrees, `sheenIn` of
    // the way in from the centre.
    static let shadeOffset = (x: -8.0, y: -10.0, blur: 10.0)
    static let lightOffset = (x: 6.0, y: 7.0, blur: 7.0)
    static let sheenAngle = -125.0
    static let sheenIn = 0.56
    // Contact shadows, in the paper's deep tone: a wide soft one, a tight one where the clay meets
    // the floor, and the clay's own colour bounced onto the floor.
    static let shadowWide = (x: 10.0, y: 18.0, blur: 22.0, alpha: 0.24)
    static let shadowTight = (x: 2.0, y: 4.0, blur: 5.0, alpha: 0.36)
    static let bounce = (x: 4.0, y: 14.0, blur: 26.0, alpha: 0.22)
    // A satin sheen, not a gloss: a broad glow and a small, soft hot spot.
    static let sheenBroad = 0.22
    static let sheenHot = 0.6
    // Each fingerprint has `ridges` loops, spaced `ridgeStep` of the dent's radius.
    static let ridges = 6
    static let ridgeStep = 0.12

    typealias Hsl = (h: Double, s: Double, l: Double)

    static func hsl(_ hex: UInt32) -> Hsl {
        let r = Double((hex >> 16) & 255) / 255
        let g = Double((hex >> 8) & 255) / 255
        let b = Double(hex & 255) / 255
        let mx = max(r, g, b)
        let mn = min(r, g, b)
        let l = (mx + mn) / 2
        if mx == mn { return (0, 0, l) }
        let d = mx - mn
        let s = l > 0.5 ? d / (2 - mx - mn) : d / (mx + mn)
        var h = mx == r ? (g - b) / d + (g < b ? 6 : 0) : mx == g ? (b - r) / d + 2 : (r - g) / d + 4
        h *= 60
        return (h, s, l)
    }

    /// A colour from hue (degrees), saturation and lightness (0…1), with alpha.
    static func hsla(_ h: Double, _ s: Double, _ l: Double, _ a: Double) -> Color {
        let c = rgba(h, s, l, a)
        return Color(.sRGB, red: c.r, green: c.g, blue: c.b, opacity: c.a)
    }

    typealias Rgba = (r: Double, g: Double, b: Double, a: Double)

    /// The same colour as four numbers, for mixing.
    static func rgba(_ h: Double, _ s: Double, _ l: Double, _ a: Double) -> Rgba {
        let hh = (h.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360) / 60
        let c = (1 - abs(2 * l - 1)) * s
        let x = c * (1 - abs(hh.truncatingRemainder(dividingBy: 2) - 1))
        let m = l - c / 2
        let (r, g, b): (Double, Double, Double)
        if hh < 1 { (r, g, b) = (c, x, 0) }
        else if hh < 2 { (r, g, b) = (x, c, 0) }
        else if hh < 3 { (r, g, b) = (0, c, x) }
        else if hh < 4 { (r, g, b) = (0, x, c) }
        else if hh < 5 { (r, g, b) = (x, 0, c) }
        else { (r, g, b) = (c, 0, x) }
        return (r + m, g + m, b + m, a)
    }

    /// Two colours mixed `p` of the way, straight through RGB (no detour through other hues).
    static func mixRgb(_ a: Rgba, _ b: Rgba, _ p: Double) -> Color {
        Color(
            .sRGB,
            red: a.r + (b.r - a.r) * p,
            green: a.g + (b.g - a.g) * p,
            blue: a.b + (b.b - a.b) * p,
            opacity: a.a + (b.a - a.a) * p
        )
    }

    /// The tones a clay is lit with; mid-change, two clays' tones mixed `p` of the way.
    struct Tones {
        let from: Hsl
        let to: Hsl
        let p: Double
        init(c: Hsl) { from = c; to = c; p = 0 }
        init(from: Hsl, to: Hsl, p: Double) { self.from = from; self.to = to; self.p = p }
        private func tone(_ f: (Hsl) -> Rgba) -> Color { mixRgb(f(from), f(to), p) }
        func base(_ a: Double) -> Color { tone { rgba($0.h, $0.s, $0.l, a) } }
        func light(_ a: Double) -> Color { tone { rgba($0.h - 6, $0.s * 0.8, $0.l + (0.96 - $0.l) * 0.5, a) } }
        func shade(_ a: Double) -> Color { tone { rgba($0.h + 8, min(1, $0.s * 1.08), $0.l * 0.56, a) } }
        func deep(_ a: Double) -> Color { tone { rgba($0.h + 12, $0.s, $0.l * 0.34, a) } }
    }

    /// The paper tone of a clay: its hue at a share `t.s` of its saturation and lightness `t.l`.
    static func paper(_ c: Hsl, _ t: (s: Double, l: Double), _ a: Double = 1) -> Rgba {
        rgba(c.h, min(1, c.s * t.s), t.l, a)
    }

    /// The paper tone between two clays, `p` of the way.
    static func paperMix(_ a: Hsl, _ b: Hsl, _ t: (s: Double, l: Double), _ p: Double, _ alpha: Double = 1) -> Color {
        mixRgb(paper(a, t, alpha), paper(b, t, alpha), p)
    }

    static func color(_ c: Rgba) -> Color { Color(.sRGB, red: c.r, green: c.g, blue: c.b, opacity: c.a) }

    /// A Skia blur sigma (in points) as a SwiftUI blur radius: side by side on the simulator, the
    /// same number gives the same spread.
    static func blur(_ sigma: Double) -> Double { sigma }
}

enum ClayPressBlobPainter {
    private typealias S = ClayPressBlobStyle
    private typealias P = ClayPressBlobPhysics

    /// A smooth closed curve through the ring (Catmull-Rom as cubics).
    static func ring(_ s: ClayPressBlobSim, into path: inout Path) {
        let n = P.points
        path.move(to: CGPoint(x: s.x[0], y: s.y[0]))
        for i in 0..<n {
            let i0 = (i + n - 1) % n
            let i2 = (i + 1) % n
            let i3 = (i + 2) % n
            path.addCurve(
                to: CGPoint(x: s.x[i2], y: s.y[i2]),
                control1: CGPoint(x: s.x[i] + (s.x[i2] - s.x[i0]) / 6, y: s.y[i] + (s.y[i2] - s.y[i0]) / 6),
                control2: CGPoint(x: s.x[i2] - (s.x[i3] - s.x[i]) / 6, y: s.y[i2] - (s.y[i3] - s.y[i]) / 6)
            )
        }
        path.closeSubpath()
    }

    /// `path` filled with `shading`, moved by (dx, dy) and blurred by a Skia-style sigma.
    private static func shifted(
        _ context: inout GraphicsContext, _ path: Path, _ dx: Double, _ dy: Double,
        _ shading: GraphicsContext.Shading, _ sigma: Double, eoFill: Bool = false
    ) {
        context.drawLayer { layer in
            if sigma > 0 { layer.addFilter(.blur(radius: S.blur(sigma))) }
            layer.translateBy(x: dx, y: dy)
            layer.fill(path, with: shading, style: FillStyle(eoFill: eoFill))
        }
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    private static func grain(_ strength: Double, _ scale: Double, _ mottle: Double, _ origin: CGPoint) -> GraphicsContext.Shading {
        .shader(ShaderLibrary.clayPressBlobGrain(
            .float(Float(strength)), .float(Float(scale)), .float(Float(mottle)),
            .float2(Float(origin.x), Float(origin.y))
        ))
    }

    /// One finger's dent: a soft hollow lit from the upper left, a raised lip, and fading ridges.
    private static func dent(
        _ context: inout GraphicsContext, _ cx: Double, _ cy: Double,
        depth: Double, ridge: Double, seed: Double, t: S.Tones
    ) {
        let d = depth * depth * (3 - 2 * depth)
        if d < 0.01 { return }
        let r = P.dentRadius * (0.6 + 0.4 * d)
        // Light direction, toward the upper left.
        let lx = -0.62
        let ly = -0.78
        // The lip: clay pushed up around the dent, lit on its outer upper-left side.
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: S.blur(2.6)))
            layer.stroke(
                circle(cx, cy, r + 3),
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: t.light(0.55 * d), location: 0),
                        .init(color: t.light(0), location: 0.5),
                        .init(color: t.shade(0.35 * d), location: 1),
                    ]),
                    startPoint: CGPoint(x: cx + lx * (r + 4), y: cy + ly * (r + 4)),
                    endPoint: CGPoint(x: cx - lx * (r + 4), y: cy - ly * (r + 4))
                ),
                lineWidth: 7
            )
        }
        // The hollow: in shadow on the side facing the light, catching it on the far wall.
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: S.blur(3.2)))
            layer.fill(
                circle(cx, cy, r),
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: t.deep(0.5 * d), location: 0),
                        .init(color: t.shade(0.22 * d), location: 0.35),
                        .init(color: t.base(0), location: 0.62),
                        .init(color: t.light(0.5 * d), location: 1),
                    ]),
                    startPoint: CGPoint(x: cx + lx * r, y: cy + ly * r),
                    endPoint: CGPoint(x: cx - lx * r, y: cy - ly * r)
                )
            )
        }
        if ridge < 0.02 { return }
        // The fingerprint: broken, slightly oval loops, embossed by a dark and a light copy.
        let a = ridge * ridge
        var loops = Path()
        for k in 1...S.ridges {
            let rr = r * (0.12 + Double(k) * S.ridgeStep)
            let start = seed * 360 + Double(k) * 47
            let sweep = 290 - Double(k % 2) * 46 - (seed * 97 * Double(k)).truncatingRemainder(dividingBy: 30)
            // An arc of the oval rr × 0.8 rr, sampled finely enough to read as a curve.
            let steps = 36
            for step in 0...steps {
                let ang = (start + sweep * Double(step) / Double(steps)) * Double.pi / 180
                let pt = CGPoint(x: rr * cos(ang), y: rr * 0.8 * sin(ang))
                if step == 0 { loops.move(to: pt) } else { loops.addLine(to: pt) }
            }
        }
        let style = StrokeStyle(lineWidth: 1.1, lineCap: .round, lineJoin: .round)
        let turn = Angle.degrees(seed * 180 - 30)
        var lightCopy = context
        lightCopy.translateBy(x: cx + 0.7, y: cy + 0.9)
        lightCopy.rotate(by: turn)
        lightCopy.stroke(loops, with: .color(t.light(0.5 * a)), style: style)
        var darkCopy = context
        darkCopy.translateBy(x: cx - 0.4, y: cy - 0.5)
        darkCopy.rotate(by: turn)
        darkCopy.stroke(loops, with: .color(t.deep(0.34 * a)), style: style)
    }

    /// The whole scene: the studio sweep, the clay's shadows, its body, dents, rims and sheen.
    static func draw(_ context: inout GraphicsContext, sim s: ClayPressBlobSim, size: CGSize, hsl: [ClayPressBlobStyle.Hsl]) {
        let width = size.width
        let height = size.height
        let r0 = s.r0
        let all = Path(CGRect(origin: .zero, size: size))
        guard !hsl.isEmpty else { return }
        // The clay's colour and the paper's, mid-change: the paper turns with the change as a whole.
        let p = s.recolour
        let from = hsl[min(max(s.colourFrom, 0), hsl.count - 1)]
        let to = hsl[min(max(s.colourTo, 0), hsl.count - 1)]
        let e = ClayPressBlobMetrics.easeInOut(p)
        // The studio.
        context.fill(all, with: .linearGradient(
            Gradient(stops: [
                .init(color: S.paperMix(from, to, S.paperTop, e), location: 0),
                .init(color: S.paperMix(from, to, S.paperMid, e), location: 0.46),
                .init(color: S.paperMix(from, to, S.paperFloor, e), location: 1),
            ]),
            startPoint: .zero,
            endPoint: CGPoint(x: 0, y: height)
        ))
        context.fill(all, with: .radialGradient(
            Gradient(colors: [Color.rgba(255, 255, 255, 0.62), Color.rgba(255, 255, 255, 0)]),
            center: CGPoint(x: s.homeX - r0 * 0.5, y: s.homeY - r0 * 0.9),
            startRadius: 0,
            endRadius: width * 0.95
        ))
        context.fill(all, with: grain(S.grain, 140, 0.5, .zero))

        // Mid-change, the shading follows the mix while the new colour spreads from the tap.
        let fromTones = S.Tones(c: from)
        let toTones = S.Tones(c: to)
        let mixed = S.Tones(from: from, to: to, p: e)
        func shadowTone(_ a: Double) -> Color { S.paperMix(from, to, S.paperInk, e, a) }

        var body = Path()
        ring(s, into: &body)
        var outside = Path(CGRect(x: -width, y: -height, width: width * 3, height: height * 3))
        ring(s, into: &outside)

        // Shadows on the floor.
        shifted(&context, body, S.shadowWide.x, S.shadowWide.y, .color(shadowTone(S.shadowWide.alpha)), S.shadowWide.blur)
        shifted(&context, body, S.bounce.x, S.bounce.y, .color(mixed.base(S.bounce.alpha)), S.bounce.blur)
        shifted(&context, body, S.shadowTight.x, S.shadowTight.y, .color(shadowTone(S.shadowTight.alpha)), S.shadowTight.blur)

        // The body, with the new colour spreading from the tap as a soft-edged disc.
        var clay = context
        clay.clip(to: body)
        clay.fill(body, with: .color(fromTones.base(1)))
        if p < 1 {
            let ox = s.cx + s.recolourX
            let oy = s.cy + s.recolourY
            if s.calm {
                clay.fill(body, with: .color(toTones.base(p)))
            } else {
                shifted(&clay, circle(ox, oy, e * r0 * 2.7), 0, 0, .color(toTones.base(1)), 12)
            }
        }

        // Where the sheen sits: part way out toward the upper-left ring point, so it rides the shape.
        let n = P.points
        let k = Int((((S.sheenAngle + 360).truncatingRemainder(dividingBy: 360)) / 360 * Double(n)).rounded()) % n
        let ax = s.cx + (s.x[k] - s.cx) * S.sheenIn - s.cvx * 0.018
        let ay = s.cy + (s.y[k] - s.cy) * S.sheenIn - s.cvy * 0.018
        // Volume: light falls off from the sheen; the far side turns into the shade.
        clay.fill(body, with: .radialGradient(
            Gradient(stops: [
                .init(color: mixed.light(0.5), location: 0),
                .init(color: mixed.light(0), location: 0.32),
                .init(color: mixed.shade(0), location: 0.5),
                .init(color: mixed.shade(0.6), location: 1),
            ]),
            center: CGPoint(x: ax, y: ay),
            startRadius: 0,
            endRadius: r0 * 2.1
        ))
        // Clay grain and mottling, carried with the body.
        clay.fill(body, with: grain(0.09, 16, 0.28, CGPoint(x: s.cx, y: s.cy)))

        for d in s.dents where d.live {
            dent(&clay, s.cx + d.x, s.cy + d.y, depth: d.depth, ridge: d.ridge, seed: d.seed, t: mixed)
        }

        // The rim: dark toward the lower right, lit toward the upper left, and a thin bounce of floor
        // light right on the lower edge.
        shifted(&clay, outside, S.shadeOffset.x, S.shadeOffset.y, .color(mixed.deep(0.62)), S.shadeOffset.blur, eoFill: true)
        shifted(&clay, outside, S.lightOffset.x, S.lightOffset.y, .color(mixed.light(0.6)), S.lightOffset.blur, eoFill: true)
        shifted(&clay, outside, -1.5, -2, .color(mixed.light(0.28)), 1.6, eoFill: true)

        // The sheen: a broad soft glow and a small hot spot, turned along the surface.
        let tx = s.x[(k + 1) % n] - s.x[(k + n - 1) % n]
        let ty = s.y[(k + 1) % n] - s.y[(k + n - 1) % n]
        var sheen = clay
        sheen.translateBy(x: ax, y: ay)
        sheen.rotate(by: .radians(atan2(ty, tx)))
        shifted(&sheen, Path(ellipseIn: CGRect(x: -r0 * 0.36, y: -r0 * 0.17, width: r0 * 0.72, height: r0 * 0.34)), 0, 0, .color(Color.white.opacity(S.sheenBroad)), 11)
        shifted(&sheen, Path(ellipseIn: CGRect(x: -r0 * 0.15, y: -r0 * 0.12, width: r0 * 0.24, height: r0 * 0.1)), 0, 0, .color(Color.white.opacity(S.sheenHot)), 3.2)
    }
}
