import SwiftUI

/// One of the five monoline diagrams, drawn on a 300-unit square and scaled to `size`. Only a diagram that is
/// visible draws; the Skia canvas of the React Native block rebuilds just the page on screen the same way.
struct QuietSignalOnboardingDiagramView: View {
    let stage: Int
    let time: Double
    let reduced: Bool
    let size: CGFloat
    let visible: Bool

    var body: some View {
        Canvas { context, _ in
            guard visible else { return }
            var scaled = context
            let unit = size / 300
            scaled.scaleBy(x: unit, y: unit)
            QuietSignalOnboardingDiagrams.draw(stage: stage, in: scaled, time: time, reduced: reduced)
        }
        .frame(width: size, height: size)
        .clipped()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// The drawing of QuietSignalDiagrams.tsx: shared outlines and wave builders, then one function per stage.
/// Every moving line is rebuilt from the one loop clock, so curves glide instead of stepping. `reduced`
/// freezes each diagram on the pose React Native picks (`reduced ? n : time`).
enum QuietSignalOnboardingDiagrams {
    private static let fullTurn = Double.pi * 2
    /// `INK`.
    private static let ink = QuietSignalOnboardingPalette.hex(0xF2F2F7)
    /// `DIM`.
    private static let dim = QuietSignalOnboardingPalette.hex(0x8E8E99)

    static func draw(stage: Int, in context: GraphicsContext, time: Double, reduced: Bool) {
        switch stage {
        case 0: busyMind(context, time, reduced)
        case 1: attention(context, time, reduced)
        case 2: anywhere(context, time, reduced)
        case 3: rhythm(context, time, reduced)
        default: emblem(context, time, reduced)
        }
    }

    // MARK: - Outlines (the `svg(...)` constants)

    private static func pt(_ x: Double, _ y: Double) -> CGPoint {
        CGPoint(x: x, y: y)
    }

    private static func circleRect(_ cx: Double, _ cy: Double, _ r: Double) -> CGRect {
        CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)
    }

    /// `white(alpha)`: the `#FFFFFFxx` colours of the gradients.
    private static func white(_ alpha: Int) -> Color {
        Color(.sRGB, red: 1, green: 1, blue: 1, opacity: Double(alpha) / 255)
    }

    /// `HEAD`: a face in profile looking right: crown, brow, nose, lips, chin, then the neck.
    private static let head: Path = {
        var p = Path()
        p.move(to: pt(146, 30))
        p.addCurve(to: pt(245, 108), control1: pt(204, 26), control2: pt(244, 60))
        p.addLine(to: pt(246, 122))
        p.addCurve(to: pt(257, 146), control1: pt(247, 130), control2: pt(251, 137))
        p.addLine(to: pt(267, 160))
        p.addCurve(to: pt(260, 167), control1: pt(269, 164), control2: pt(266, 167))
        p.addLine(to: pt(254, 168))
        p.addCurve(to: pt(252, 179), control1: pt(256, 172), control2: pt(256, 176))
        p.addCurve(to: pt(249, 189), control1: pt(255, 183), control2: pt(254, 187))
        p.addCurve(to: pt(247, 207), control1: pt(251, 195), control2: pt(252, 201))
        p.addCurve(to: pt(211, 214), control1: pt(239, 215), control2: pt(222, 214))
        p.addLine(to: pt(207, 290))
        return p
    }()

    /// `HEAD_FILL`: the profile closed down to the shoulders.
    private static let headFill: Path = {
        var p = head
        p.addLine(to: pt(120, 290))
        p.addCurve(to: pt(104, 214), control1: pt(125, 250), control2: pt(118, 230))
        p.addCurve(to: pt(146, 30), control1: pt(70, 176), control2: pt(80, 40))
        p.closeSubpath()
        return p
    }()

    /// `PILL`: `M44 54H130A10 10 0 0 1 130 74H44A10 10 0 0 1 44 54Z`, a stadium from x 34 to 140.
    private static let pill = Path(roundedRect: CGRect(x: 34, y: 54, width: 106, height: 20), cornerRadius: 10)

    /// `DOC`: a page with its header rule.
    private static let doc: Path = {
        var p = Path()
        p.addRect(CGRect(x: 40, y: 156, width: 110, height: 116))
        p.move(to: pt(40, 168))
        p.addLine(to: pt(150, 168))
        return p
    }()

    /// `PHONE_BODY` (`PHONE_CLIP`): the phone's rounded body.
    private static let phoneBody = Path(roundedRect: CGRect(x: 84, y: 26, width: 132, height: 248), cornerRadius: 20)

    /// `PHONE`: the body and the speaker notch cut into its top edge.
    private static let phone: Path = {
        var p = phoneBody
        p.move(to: pt(132, 26))
        p.addLine(to: pt(132, 30))
        p.addQuadCurve(to: pt(138, 36), control: pt(132, 36))
        p.addLine(to: pt(162, 36))
        p.addQuadCurve(to: pt(168, 30), control: pt(168, 36))
        p.addLine(to: pt(168, 26))
        return p
    }()

    /// `CROSS`.
    private static let cross: Path = {
        var p = Path()
        p.move(to: pt(150, 10))
        p.addLine(to: pt(150, 290))
        p.move(to: pt(10, 150))
        p.addLine(to: pt(290, 150))
        return p
    }()

    /// `FAINT`: two long faint curves that sweep through the phone behind its waves. Each `S` segment of the
    /// SVG reflects the previous control point, which is written out here.
    private static let faint: Path = {
        var p = Path()
        p.move(to: pt(70, 250))
        p.addCurve(to: pt(160, 80), control1: pt(110, 230), control2: pt(130, 120))
        p.addCurve(to: pt(230, 30), control1: pt(190, 40), control2: pt(200, 40))
        p.move(to: pt(80, 260))
        p.addCurve(to: pt(200, 90), control1: pt(150, 240), control2: pt(170, 120))
        p.addCurve(to: pt(260, 50), control1: pt(230, 60), control2: pt(240, 60))
        return p
    }()

    /// `EYE`: the lid and, as a nearly closed arc in the SVG, the pupil: a circle of radius 2.5 at (238, 138).
    private static let eye: Path = {
        var p = Path()
        p.move(to: pt(228, 138))
        p.addQuadCurve(to: pt(248, 138), control: pt(238, 130))
        p.addQuadCurve(to: pt(228, 138), control: pt(238, 146))
        p.closeSubpath()
        p.addEllipse(in: circleRect(237.95, 138, 2.5))
        return p
    }()

    /// `KEY`: two short strokes inside the pulse's centre ring.
    private static let key: Path = {
        var p = Path()
        p.move(to: pt(3, 3))
        p.addLine(to: pt(10, 10))
        p.move(to: pt(8, 12))
        p.addLine(to: pt(12, 8))
        return p
    }()

    /// `EMBLEM`: the signal line of the welcome page.
    private static let emblemLine: Path = {
        var p = Path()
        p.move(to: pt(66, 150))
        p.addLine(to: pt(110, 150))
        p.addCurve(to: pt(130, 136), control1: pt(122, 150), control2: pt(126, 146))
        p.addLine(to: pt(142, 104))
        p.addCurve(to: pt(172, 100), control1: pt(148, 90), control2: pt(166, 88))
        p.addCurve(to: pt(164, 132), control1: pt(178, 112), control2: pt(170, 122))
        p.addLine(to: pt(150, 154))
        p.addCurve(to: pt(162, 180), control1: pt(144, 166), control2: pt(150, 178))
        p.addCurve(to: pt(186, 160), control1: pt(176, 182), control2: pt(182, 170))
        p.addLine(to: pt(196, 134))
        p.addCurve(to: pt(222, 118), control1: pt(200, 124), control2: pt(208, 118))
        return p
    }()

    /// `RING`: a circle of radius 84 about (150, 150).
    private static let ring = Path(ellipseIn: circleRect(150, 150, 84))

    // MARK: - Builders (`wave`, `bars`, `crescent`, `arc`)

    /// `wave`: a sine sampled every 4 units, tapered to nothing at both ends unless `tapered` is false.
    private static func wave(_ x0: Double, _ x1: Double, y: Double, amp: Double, length: Double, phase: Double, tapered: Bool = true) -> Path {
        var p = Path()
        var x = x0
        var first = true
        while x <= x1 {
            let taper = tapered ? sin((x - x0) / (x1 - x0) * Double.pi) : 1.0
            let v = y + sin(x / length * fullTurn + phase) * amp * taper
            if first {
                p.move(to: pt(x, v))
                first = false
            } else {
                p.addLine(to: pt(x, v))
            }
            x += 4
        }
        return p
    }

    /// `bars`: a row of vertical bars whose heights wander with the clock, tallest in the middle.
    private static func bars(_ t: Double, x0: Double, count: Int, gap: Double, y: Double, maxHeight: Double, seed: Double) -> Path {
        var p = Path()
        let middle = Double(count - 1) / 2
        for i in 0..<count {
            let index = Double(i)
            let centre = 1 - abs(index - middle) / (middle + 1)
            let first = 0.25 * sin(t * 5.1 + index * 1.7 + seed)
            let second = 0.25 * sin(t * 3.3 + index * 0.9 + seed * 2)
            let h = 3 + maxHeight * centre * (0.45 + 0.55 * (0.5 + first + second))
            let x = x0 + index * gap
            p.move(to: pt(x, y - h / 2))
            p.addLine(to: pt(x, y + h / 2))
        }
        return p
    }

    /// `crescent`: an outer half circle facing `angle`, closed by an inner arc pulled back toward the centre: a moon.
    private static func crescent(_ cx: Double, _ cy: Double, _ r: Double, _ angle: Double, _ thickness: Double) -> Path {
        var p = Path()
        let a0 = angle - Double.pi / 2
        let dx = cos(angle)
        let dy = sin(angle)
        for i in 0...28 {
            let a = a0 + Double.pi * (Double(i) / 28)
            let point = pt(cx + cos(a) * r, cy + sin(a) * r)
            if i == 0 { p.move(to: point) } else { p.addLine(to: point) }
        }
        for i in stride(from: 28, through: 0, by: -1) {
            let s = Double(i) / 28
            let a = a0 + Double.pi * s
            let pull = sin(s * Double.pi) * thickness
            p.addLine(to: pt(cx + cos(a) * r - dx * pull, cy + sin(a) * r - dy * pull))
        }
        return p
    }

    /// `arc`: a polyline round (cx, cy), a point every 0.08 rad.
    private static func arc(_ cx: Double, _ cy: Double, _ r: Double, _ from: Double, _ to: Double) -> Path {
        var p = Path()
        let steps = max(2, Int(ceil(abs(to - from) / 0.08)))
        for i in 0...steps {
            let a = from + (to - from) * (Double(i) / Double(steps))
            let point = pt(cx + cos(a) * r, cy + sin(a) * r)
            if i == 0 { p.move(to: point) } else { p.addLine(to: point) }
        }
        return p
    }

    // MARK: - Painting

    /// A stroke with Skia's defaults (butt caps, miter joins, miter limit 4) unless told otherwise.
    private static func stroke(
        _ context: GraphicsContext, _ path: Path, _ color: Color, _ width: CGFloat,
        opacity: Double = 1, cap: CGLineCap = .butt, join: CGLineJoin = .miter
    ) {
        guard opacity > 0 else { return }
        let style = StrokeStyle(lineWidth: width, lineCap: cap, lineJoin: join, miterLimit: 4)
        context.stroke(path, with: .color(color.opacity(opacity)), style: style)
    }

    // MARK: - Stage 0: a busy mind

    /// `BusyMind`: moons swing through the head, a notification hums, a page keeps filling.
    private static func busyMind(_ context: GraphicsContext, _ t: Double, _ reduced: Bool) {
        let v = reduced ? 2.0 : t
        stroke(context, head, ink, 1.5, opacity: 0.9)
        stroke(context, pill, dim, 1.3)
        // `hum`
        stroke(context, wave(54, 122, y: 64, amp: 3.2, length: 12, phase: -v * 7), ink, 1.3, opacity: 0.85)
        context.fill(doc, with: .color(QuietSignalOnboardingPalette.hex(0x050507)))
        stroke(context, doc, ink, 1.4, opacity: 0.9)
        // `lines`
        let reach: [Double] = [86, 74, 86, 58, 70]
        var lines = Path()
        for i in 0..<5 {
            let grow = reduced ? 1.0 : 0.5 + 0.5 * sin(v * 1.3 - Double(i) * 0.7)
            let y = 192 + Double(i) * 13
            lines.move(to: pt(52, y))
            lines.addLine(to: pt(52 + reach[i] * (0.35 + 0.65 * grow), y))
        }
        stroke(context, lines, dim, 1.2, cap: .round)
        // `ripples`
        var ripples = Path()
        for i in 0..<3 {
            let n = Double(i)
            let a = v * (0.5 + n * 0.18) + n * 2
            ripples.addPath(arc(160, 150, 34 + n * 12, a, a + 1.6 + n * 0.4))
        }
        stroke(context, ripples, dim, 1, opacity: 0.7)
        // `moons`
        var moons = Path()
        moons.addPath(crescent(168, 116, 50, 0.3 + sin(v * 0.9) * 0.5, 24))
        moons.addPath(crescent(160, 152, 36, 3.5 + sin(v * 0.7 + 1.2) * 0.55, 14))
        stroke(context, moons, ink, 1.5, join: .round)
        // `ThoughtDot`: the three dots light one after another, like someone about to reply.
        for i in 0..<3 {
            let cx = 202 + Double(i) * 13
            let phase = (t * 0.8 + 1 - Double(i) / 3).truncatingRemainder(dividingBy: 1)
            let lit = reduced ? i == 0 : phase < 0.33
            stroke(context, Path(ellipseIn: circleRect(cx, 146, 4.2)), ink, 1.3, opacity: 0.85)
            if lit {
                context.fill(Path(ellipseIn: circleRect(cx, 146, 3.6)), with: .color(ink))
            }
        }
    }

    // MARK: - Stage 1: attention

    /// `Attention`: a lit profile, two waves crossing in a slow figure of eight, a voice between them.
    private static func attention(_ context: GraphicsContext, _ t: Double, _ reduced: Bool) {
        let v = reduced ? 1.5 : t
        let glow = Gradient(stops: [
            .init(color: white(0x30), location: 0),
            .init(color: white(0x14), location: 0.7),
            .init(color: white(0x00), location: 1),
        ])
        context.fill(
            Path(ellipseIn: circleRect(205, 150, 120)),
            with: .radialGradient(glow, center: pt(205, 150), startRadius: 0, endRadius: 120)
        )
        // The faint grid, composited as one group (`<Group opacity={0.22}>`).
        var grid = context
        grid.opacity = 0.22
        grid.drawLayer { layer in
            stroke(layer, cross, dim, 0.8)
            var dots = Path()
            for i in 0..<49 {
                dots.addEllipse(in: circleRect(30 + Double(i % 7) * 40, 30 + Double(i / 7) * 40, 1.1))
            }
            layer.fill(dots, with: .color(ink))
        }
        let lit = Gradient(stops: [
            .init(color: Color(.sRGB, red: 0, green: 0, blue: 0, opacity: 0), location: 0),
            .init(color: white(0x10), location: 0.45),
            .init(color: white(0xD8), location: 1),
        ])
        context.fill(headFill, with: .linearGradient(lit, startPoint: pt(150, 0), endPoint: pt(258, 0)))
        stroke(context, head, ink, 1.4, opacity: 0.75)
        // `waves`: two long sines in antiphase cross twice across the frame, slowly breathing.
        let amp = 64 + sin(v * 0.8) * 8
        var waves = Path()
        waves.addPath(wave(14, 286, y: 150, amp: amp, length: 248, phase: Double.pi / 2 + v * 0.5, tapered: false))
        waves.addPath(wave(14, 286, y: 150, amp: amp, length: 248, phase: -Double.pi / 2 + v * 0.5, tapered: false))
        stroke(context, waves, ink, 1.5, opacity: 0.9)
        // `voice`
        stroke(context, bars(v, x0: 120, count: 13, gap: 5, y: 150, maxHeight: 30, seed: 0), ink, 2, cap: .round)
    }

    // MARK: - Stage 2: anywhere

    /// `Ripple`: soft rings of light swell out of each speaker and fade, like sound in a dark room.
    private static func ripple(_ context: GraphicsContext, side: Int, index: Int, _ t: Double, _ reduced: Bool) {
        let n = Double(index)
        let k = reduced ? 0.3 + n * 0.25 : (t * 0.5 + n / 3).truncatingRemainder(dividingBy: 1)
        let radius = 16 + k * 40
        let alpha = reduced ? 0.35 : 0.75 * sin(min(1, k * 1.1) * Double.pi)
        let cx = side < 0 ? 46.0 : 254.0
        var clipped = context
        clipped.clip(to: Path(CGRect(x: side < 0 ? 0 : 254, y: 0, width: 46, height: 300)))
        clipped.opacity = alpha
        let rings = Gradient(stops: [
            .init(color: white(0x00), location: 0),
            .init(color: white(0x00), location: 0.25),
            .init(color: white(0x24), location: 0.62),
            .init(color: white(0x00), location: 1),
        ])
        clipped.fill(
            Path(ellipseIn: circleRect(cx, 150, radius)),
            with: .radialGradient(rings, center: pt(cx, 150), startRadius: 0, endRadius: 60)
        )
    }

    /// `Anywhere`: a phone carries the waves; sound ripples out on both sides.
    private static func anywhere(_ context: GraphicsContext, _ t: Double, _ reduced: Bool) {
        let v = reduced ? 1.0 : t
        for side in [-1, 1] {
            for index in 0..<3 {
                ripple(context, side: side, index: index, t, reduced)
            }
        }
        context.fill(phone, with: .color(QuietSignalOnboardingPalette.hex(0x060608)))
        stroke(context, phone, ink, 1.6, opacity: 0.9)
        // `waves`: the swell slowly trades shallow ripples for a broad S, clipped to the phone.
        var waves = Path()
        for i in 0..<3 {
            let n = Double(i)
            let broad = 0.5 + 0.5 * sin(v * 0.55 + n * 0.9)
            waves.addPath(wave(84, 216, y: 150, amp: 18 + 10 * broad, length: 66 + 30 * broad, phase: v * 1.2 + n * (fullTurn / 3), tapered: false))
        }
        var inside = context
        inside.clip(to: phoneBody)
        stroke(inside, faint, ink, 1, opacity: 0.16)
        stroke(inside, waves, ink, 1.4, opacity: 0.85)
        // `left`, `right`
        stroke(context, bars(v, x0: 28, count: 8, gap: 5, y: 150, maxHeight: 26, seed: 1), ink, 2, cap: .round)
        stroke(context, bars(v, x0: 236, count: 8, gap: 5, y: 150, maxHeight: 26, seed: 4), ink, 2, cap: .round)
    }

    // MARK: - Stage 3: rhythm

    /// The `streams` path as its separate contours: four light streams, then four arcs that wrap the back of
    /// the head. Kept apart because Skia trims each contour on its own.
    private static func streamContours(_ v: Double) -> [Path] {
        var contours: [Path] = []
        for i in 0..<4 {
            let y = 60 + Double(i) * 30
            let sway = sin(v * 0.7 + Double(i)) * 8
            var stream = Path()
            stream.move(to: pt(20, y + 70))
            stream.addCurve(to: pt(230, y - 30 + sway), control1: pt(80, y + 20 + sway), control2: pt(150, y + 10 - sway))
            contours.append(stream)
        }
        for i in 0..<4 {
            let n = Double(i)
            let a = -2.2 + sin(v * 0.4 + n) * 0.1
            contours.append(arc(150, 150, 96 + n * 12, a, a + 1.5 - n * 0.2))
        }
        return contours
    }

    /// `Rhythm`: a soft pulse of rings in the mind, and streams of light passing through.
    private static func rhythm(_ context: GraphicsContext, _ t: Double, _ reduced: Bool) {
        let v = reduced ? 2.0 : t
        // `pulse`: translate(132, 158) then scale, about the ring centre.
        var pulse = context
        pulse.translateBy(x: 132, y: 158)
        let swell = reduced ? 1.0 : 1 + sin(t * 1.6) * 0.06
        pulse.scaleBy(x: swell, y: swell)
        for i in 0..<5 {
            let n = Double(i)
            let radius = 70 - n * 12
            let haze = Gradient(stops: [
                .init(color: white(0x02), location: 0),
                .init(color: white(0x02), location: 0.3),
                .init(color: white(0x22), location: 0.88),
                .init(color: white(0x0C), location: 1),
            ])
            var disc = pulse
            disc.opacity = 0.4 + n * 0.12
            disc.fill(
                Path(ellipseIn: circleRect(0, 0, radius)),
                with: .radialGradient(haze, center: .zero, startRadius: 0, endRadius: radius)
            )
        }
        // The centre ring and its key, in the same translated frame.
        var centre = context
        centre.translateBy(x: 132, y: 158)
        stroke(centre, Path(ellipseIn: circleRect(0, 0, 11)), ink, 1.4)
        stroke(centre, key, ink, 1.4, cap: .round)
        // `streams`: faint in full, and a travelling stretch of it, brighter and a touch soft.
        let contours = streamContours(v)
        var streams = Path()
        for contour in contours { streams.addPath(contour) }
        stroke(context, streams, ink, 1, opacity: 0.16)
        let flow = reduced ? 0.0 : (t * 0.18).truncatingRemainder(dividingBy: 1)
        let flowEnd = min(1, flow + 0.45)
        var travelling = Path()
        for contour in contours { travelling.addPath(contour.trimmedPath(from: flow, to: flowEnd)) }
        var softened = context
        softened.opacity = 0.5
        softened.drawLayer { layer in
            layer.addFilter(.blur(radius: 0.4))
            stroke(layer, travelling, ink, 1.2)
        }
        stroke(context, head, ink, 1.5, opacity: 0.9)
        stroke(context, eye, ink, 1.3)
    }

    // MARK: - Stage 4: welcome

    /// `ARC_KNOTS`: the times, in seconds, at which the arc's angles are keyed.
    private static let arcKnots: [Double] = [28.333, 28.5, 28.667, 28.833, 29, 29.167, 29.333, 29.45]
    /// The arc's leading and trailing angles at each knot, in degrees.
    private static let arcStarts: [Double] = [180, 215, 280, 325, 405, 500, 595, 650]
    private static let arcEnds: [Double] = [180.1, 325, 415, 490, 560, 605, 630, 650.1]

    /// `knot`: linear between the keyed values.
    private static func knot(_ t: Double, _ values: [Double]) -> Double {
        var i = 0
        while i < arcKnots.count - 2 && t > arcKnots[i + 1] { i += 1 }
        let k = min(1, max(0, (t - arcKnots[i]) / (arcKnots[i + 1] - arcKnots[i])))
        return values[i] + (values[i + 1] - values[i]) * k
    }

    /// `Emblem`: an arc sweeps round, closes into a ring, and the signal line draws itself inside.
    private static func emblem(_ context: GraphicsContext, _ t: Double, _ reduced: Bool) {
        typealias M = QuietSignalOnboardingMotion
        let arcAlpha = (reduced || t < 28.333 || t > 29.5) ? 0 : min(1, M.segment(t, 28.333, 28.6) * 1.6)
        if arcAlpha > 0 {
            let from = knot(t, arcStarts) * Double.pi / 180
            let to = knot(t, arcEnds) * Double.pi / 180
            stroke(context, arc(150, 150, 84, from, to), ink, 6, opacity: arcAlpha, cap: .round)
        }
        let ringAlpha = reduced ? 1 : M.ease(M.segment(t, 29.88, 30.167))
        stroke(context, ring, ink, 6, opacity: ringAlpha)
        let draw = reduced ? 1 : M.ease(M.segment(t, 30.5, 31.8))
        if draw > 0.001 {
            stroke(context, emblemLine.trimmedPath(from: 0, to: draw), ink, 6, cap: .round, join: .round)
        }
    }
}
