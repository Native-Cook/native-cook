import SwiftUI

/// The card's fixed geometry, in pt from its top left.
enum CreditCardFlipFormCardMetrics {
    static let radius = 18.0
    static let pad = 22.0
    static let numberAt = 0.52
    static let groupGap = 14.0
    static let numberLine = 28.0
    static let bottomRow = 56.0
    static let smallLine = 19.0
    static let chip = CGRect(x: 22, y: 62, width: 42, height: 31)
    static let stripe = (y: 24.0, height: 42.0)
    static let strip = (x: 22.0, y: 92.0, height: 36.0)
    static let cvvBox = CGSize(width: 62, height: 28)
    /// The new gradient spreads from the network's mark as a disc this soft at the rim.
    static let revealSoft = 26.0
    static let guillocheLines = 15
    /// Brushed metal: `brushLines` hairlines across the card at seeded heights, half lighter and
    /// half darker than the base, so the surface has a grain the light can catch.
    static let brushLines = 120
    static let brushSeed: UInt32 = 7
    static let sheenWidth = 90.0
    static let sheenTilt = -22.0
    /// The soft specular light over the card, `specular` × the card's width in radius. It rests
    /// above the top left and slides against the card's tilt (`specularTravel` of the card per 10°).
    static let specular = 0.95
    static let specularTravel = 0.5
    /// The focus outline stands this far clear of what it frames, with this corner radius; a comet
    /// of light runs round it, a bright head `cometHead` of the perimeter long over a dimmer tail
    /// `cometTail` long, once round every `orbit` s.
    static let outlinePadX = 8.0
    static let outlinePadY = 7.0
    static let outlineRadius = 10.0
    static let cometHead = 0.12
    static let cometTail = 0.34
    static let orbit = 2.6
    static let perspective = 1100.0
    static let lift = 14.0
    static let liftScale = 0.05
    /// Dragging on the card tilts it towards the finger, up to `tiltMaxY`° about its upright axis
    /// and `tiltMaxX`° about its horizontal one (`tiltPerPt`° per pt, eased into the limit); it
    /// springs flat on release with the finger's speed. A new network swings it by `nudge`°.
    static let tiltPerPt = 0.09
    static let tiltMaxY = 12.0
    static let tiltMaxX = 9.0
    static let tiltSpring = (duration: 0.7, ratio: 0.55)
    static let nudge = 7.0
    static let nudgeTime = 0.26
}

// MARK: - Art

/// The focus outline drawn on one face: its frame, how much of it shows, and where the comet is
/// (0…1 round the frame, looping).
struct CreditCardFlipFormOutline {
    var rect: CGRect
    var on: Double
    var orbit: Double
    /// Light on the card's colour; dark ink on the cream signature strip (the CVV).
    var dark = false
}

/// Everything drawn on one face of the card: its colour (spreading from the mark when the network
/// changes), brushed grain and guilloche, the light over it, the chip or the stripes, the light
/// crossing it, the focus outline and the edge.
struct CreditCardFlipFormArt: View {
    let size: CGSize
    let back: Bool
    let from: CreditCardFlipFormNetwork
    let to: CreditCardFlipFormNetwork
    let mix: Double
    let shine: Double
    let tiltX: Double
    let tiltY: Double
    let origin: CGPoint
    let outline: CreditCardFlipFormOutline

    private typealias C = CreditCardFlipFormCardMetrics

    var body: some View {
        Canvas { context, _ in
            let w = size.width
            let h = size.height
            let card = Path(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: C.radius, style: .continuous)
            context.clip(to: card)
            layer(context, palette: from.palette, w: w, h: h)
            let far = hypot(max(origin.x, w - origin.x), max(origin.y, h - origin.y)) + C.revealSoft * 2
            let radius = mix * far
            if radius > 0.5 {
                context.drawLayer { spread in
                    spread.clipToLayer { mask in
                        mask.addFilter(.blur(radius: C.revealSoft / 2))
                        mask.fill(Path(ellipseIn: CGRect(x: origin.x - radius, y: origin.y - radius, width: radius * 2, height: radius * 2)), with: .color(.white))
                    }
                    layer(spread, palette: to.palette, w: w, h: h)
                }
            }
            // Brushed grain, then the printed guilloche over it.
            let grain = Self.brush(w: w, h: h)
            context.stroke(grain.light, with: .color(.white.opacity(0.05)), lineWidth: 0.6)
            context.stroke(grain.dark, with: .color(.black.opacity(0.09)), lineWidth: 0.6)
            context.stroke(Self.guilloche(w: w, h: h), with: .color(.white.opacity(0.07)), lineWidth: 0.7)
            // The light over the card (mirrored on the back, which is the front turned over), and
            // shade towards the bottom edge.
            let side = back ? -1.0 : 1.0
            let light = CGPoint(
                x: w * (back ? 0.78 : 0.22) - side * tiltY * w * C.specularTravel / 10,
                y: -h * 0.18 + tiltX * h * C.specularTravel / 10
            )
            context.fill(
                Path(CGRect(x: 0, y: 0, width: w, height: h)),
                with: .radialGradient(
                    Gradient(stops: [
                        .init(color: .white.opacity(0.2), location: 0),
                        .init(color: .white.opacity(0.06), location: 0.45),
                        .init(color: .white.opacity(0), location: 1),
                    ]),
                    center: light, startRadius: 0, endRadius: w * C.specular
                )
            )
            context.fill(
                Path(CGRect(x: 0, y: h * 0.55, width: w, height: h * 0.45)),
                with: .linearGradient(
                    Gradient(colors: [.black.opacity(0), .black.opacity(0.22)]),
                    startPoint: CGPoint(x: 0, y: h * 0.55), endPoint: CGPoint(x: 0, y: h)
                )
            )
            if back {
                context.fill(Path(CGRect(x: 0, y: C.stripe.y, width: w, height: C.stripe.height)), with: .color(.rgba(10, 10, 14, 0.9)))
                context.fill(
                    Path(roundedRect: CGRect(x: C.strip.x, y: C.strip.y, width: w - C.strip.x * 2, height: C.strip.height), cornerRadius: 5),
                    with: .color(.rgba(242, 238, 228, 0.94))
                )
            } else {
                let chip = C.chip
                context.fill(
                    Path(roundedRect: chip, cornerRadius: 6),
                    with: .linearGradient(
                        Gradient(colors: [Color(hex: 0xf4e3b0), Color(hex: 0xb8904a), Color(hex: 0xefd9a0), Color(hex: 0x9c7634)]),
                        startPoint: CGPoint(x: chip.minX, y: chip.minY), endPoint: CGPoint(x: chip.maxX, y: chip.maxY)
                    )
                )
                context.stroke(Self.chipLines, with: .color(.rgba(90, 60, 18, 0.55)), lineWidth: 1)
                context.stroke(Path(roundedRect: chip.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 5.5), with: .color(.rgba(255, 246, 214, 0.5)), lineWidth: 1)
            }
            // The light: a band leaning SHEEN_TILT°, swept across from out of sight to out of sight.
            var sheen = context
            sheen.translateBy(x: 0, y: h / 2)
            sheen.translateBy(x: -C.sheenWidth * 2 + (w + C.sheenWidth * 4) * shine, y: 0)
            sheen.rotate(by: .degrees(C.sheenTilt))
            sheen.translateBy(x: 0, y: -h / 2)
            sheen.fill(
                Path(CGRect(x: 0, y: -h, width: C.sheenWidth, height: h * 3)),
                with: .linearGradient(
                    Gradient(colors: [.white.opacity(0), .white.opacity(0.22), .white.opacity(0)]),
                    startPoint: .zero, endPoint: CGPoint(x: C.sheenWidth, y: 0)
                )
            )
            drawOutline(context)
            // The card's edge: a hairline of light along the top, darker along the bottom.
            context.stroke(
                Path(roundedRect: CGRect(x: 0.5, y: 0.5, width: w - 1, height: h - 1), cornerRadius: C.radius - 0.5, style: .continuous),
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: .white.opacity(0.38), location: 0),
                        .init(color: .white.opacity(0.06), location: 0.5),
                        .init(color: .black.opacity(0.25), location: 1),
                    ]),
                    startPoint: .zero, endPoint: CGPoint(x: 0, y: h)
                ),
                lineWidth: 1
            )
        }
        .frame(width: size.width, height: size.height)
    }

    /// The focus outline: a fine frame of light with a comet running round it at an even speed.
    private func drawOutline(_ context: GraphicsContext) {
        guard outline.on > 0.002 else { return }
        let r = outline.rect
        let frame = Path(
            roundedRect: CGRect(x: r.minX, y: r.minY, width: max(1, r.width), height: max(1, r.height)),
            cornerRadius: C.outlineRadius
        )
        var c = context
        c.opacity = outline.on
        // Light on the card's colour; dark ink (#1d2340) on the cream signature strip.
        let ink = outline.dark ? Color(.sRGB, red: 29 / 255, green: 35 / 255, blue: 64 / 255) : .white
        c.fill(frame, with: .color(ink.opacity(0.06)))
        c.stroke(frame, with: .color(ink.opacity(outline.dark ? 0.3 : 0.34)), lineWidth: 1)
        func comet(_ length: Double, width: Double, color: Color) {
            let u = outline.orbit
            let start = u - length
            let pieces = start >= 0 ? [(start, u)] : [(start + 1, 1.0), (0.0, u)]
            for (a, b) in pieces where b > a {
                c.stroke(frame.trimmedPath(from: a, to: b), with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round))
            }
        }
        comet(C.cometTail, width: 1.5, color: ink.opacity(0.4))
        comet(C.cometHead, width: 1.75, color: ink)
    }

    /// The card's colour: a diagonal gradient and two soft pools of the palette's light colour.
    private func layer(_ context: GraphicsContext, palette: [UInt32], w: Double, h: Double) {
        context.fill(
            Path(CGRect(x: 0, y: 0, width: w, height: h)),
            with: .linearGradient(Gradient(colors: [Color(hex: palette[0]), Color(hex: palette[1])]), startPoint: .zero, endPoint: CGPoint(x: w, y: h))
        )
        var glow = context
        glow.opacity = 0.26
        glow.addFilter(.blur(radius: 42))
        let r1 = h * 0.62
        glow.fill(Path(ellipseIn: CGRect(x: w * 0.92 - r1, y: -h * 0.12 - r1, width: r1 * 2, height: r1 * 2)), with: .color(Color(hex: palette[2])))
        var low = context
        low.opacity = 0.18
        low.addFilter(.blur(radius: 36))
        let r2 = h * 0.5
        low.fill(Path(ellipseIn: CGRect(x: w * 0.04 - r2, y: h * 1.08 - r2, width: r2 * 2, height: r2 * 2)), with: .color(Color(hex: palette[2])))
    }

    /// The brushed grain: hairlines at heights from a small linear congruential generator, the same
    /// sequence on every platform.
    private static func brush(w: Double, h: Double) -> (light: Path, dark: Path) {
        var seed = CreditCardFlipFormCardMetrics.brushSeed
        func next() -> Double {
            seed = seed &* 1_664_525 &+ 1_013_904_223
            return Double(seed) / 4_294_967_296
        }
        var light = Path()
        var dark = Path()
        for _ in 0..<CreditCardFlipFormCardMetrics.brushLines {
            let y = next() * h
            if next() < 0.5 {
                light.move(to: CGPoint(x: 0, y: y)); light.addLine(to: CGPoint(x: w, y: y))
            } else {
                dark.move(to: CGPoint(x: 0, y: y)); dark.addLine(to: CGPoint(x: w, y: y))
            }
        }
        return (light, dark)
    }

    /// The fine wavy lines printed across the card, like the guilloche on a banknote.
    private static func guilloche(w: Double, h: Double) -> Path {
        var path = Path()
        for k in 0..<CreditCardFlipFormCardMetrics.guillocheLines {
            let base = h * 0.3 + Double(k) * 6.5
            var x = -4.0
            while x <= w + 4 {
                let y = base + 10 * sin(x / 26 + Double(k) * 0.42) + 4 * sin(x / 9.5 - Double(k) * 0.8)
                if x == -4 { path.move(to: CGPoint(x: x, y: y)) } else { path.addLine(to: CGPoint(x: x, y: y)) }
                x += 4
            }
        }
        return path
    }

    private static let chipLines: Path = {
        let c = CreditCardFlipFormCardMetrics.chip
        var path = Path()
        path.move(to: CGPoint(x: c.minX, y: c.minY + c.height / 3))
        path.addLine(to: CGPoint(x: c.minX + c.width * 0.34, y: c.minY + c.height / 3))
        path.move(to: CGPoint(x: c.minX, y: c.minY + c.height * 2 / 3))
        path.addLine(to: CGPoint(x: c.minX + c.width * 0.34, y: c.minY + c.height * 2 / 3))
        path.move(to: CGPoint(x: c.maxX, y: c.minY + c.height / 3))
        path.addLine(to: CGPoint(x: c.minX + c.width * 0.66, y: c.minY + c.height / 3))
        path.move(to: CGPoint(x: c.maxX, y: c.minY + c.height * 2 / 3))
        path.addLine(to: CGPoint(x: c.minX + c.width * 0.66, y: c.minY + c.height * 2 / 3))
        path.addRoundedRect(in: CGRect(x: c.minX + c.width * 0.34, y: c.minY + c.height * 0.18, width: c.width * 0.32, height: c.height * 0.64), cornerSize: CGSize(width: 3, height: 3))
        return path
    }()
}

// MARK: - Reel

/// One character of the number, the expiry or the CVV: the window onto its reel.
struct CreditCardFlipFormReelView: View {
    let roll: Double
    let speed: Double
    let placeholder: String
    let width: Double
    let line: Double
    let font: Font
    let color: Color
    var shadow = false

    var body: some View {
        Canvas { context, size in
            let index = Int(floor(roll))
            for j in [index, index + 1] where (0...20).contains(j) {
                guard let glyph = context.resolveSymbol(id: j) else { continue }
                context.draw(glyph, at: CGPoint(x: size.width / 2, y: (Double(j) - roll) * line + line / 2), anchor: .center)
            }
        } symbols: {
            ForEach(0...20, id: \.self) { j in
                Text(j == 0 ? placeholder : String((j - 1) % 10))
                    .font(font)
                    .monospacedDigit()
                    .foregroundStyle(color)
                    .shadow(color: shadow ? .black.opacity(0.25) : .clear, radius: 1, y: 1)
                    .tag(j)
            }
        }
        .frame(width: width, height: line)
        .opacity(1 - (1 - CreditCardFlipFormReel.movingAlpha) * speed)
    }
}

// MARK: - Network mark

/// The network's mark: a small drawn emblem and its name, white on the card; before a digit is
/// typed, an empty dashed slot where the mark will go.
struct CreditCardFlipFormMark: View {
    let network: CreditCardFlipFormNetwork

    private static let ink = Color.white.opacity(0.95)

    var body: some View {
        if network == .none {
            Capsule()
                .strokeBorder(Color.white.opacity(0.22), style: StrokeStyle(lineWidth: 2 / 3, dash: [2, 2]))
                .frame(width: 64, height: 22)
        } else {
            HStack(spacing: 6) {
                Canvas { context, _ in Self.draw(network, in: context) }
                    .frame(width: 26, height: 24)
                Text(network.title.uppercased())
                    .font(.system(size: 14, weight: .heavy).italic())
                    .tracking(1.6)
                    .foregroundStyle(Self.ink)
            }
        }
    }

    static func draw(_ network: CreditCardFlipFormNetwork, in context: GraphicsContext) {
        let ink = Self.ink
        switch network {
        case .vela:
            var p = Path()
            p.move(to: CGPoint(x: 3, y: 21)); p.addLine(to: CGPoint(x: 13, y: 2)); p.addLine(to: CGPoint(x: 13, y: 21)); p.closeSubpath()
            p.move(to: CGPoint(x: 15.5, y: 21)); p.addLine(to: CGPoint(x: 15.5, y: 8)); p.addLine(to: CGPoint(x: 24, y: 21)); p.closeSubpath()
            context.fill(p, with: .color(ink))
        case .kestrel:
            var p = Path()
            p.move(to: CGPoint(x: 2, y: 7)); p.addLine(to: CGPoint(x: 13, y: 17)); p.addLine(to: CGPoint(x: 24, y: 7))
            p.move(to: CGPoint(x: 6, y: 3)); p.addLine(to: CGPoint(x: 13, y: 10)); p.addLine(to: CGPoint(x: 20, y: 3))
            context.stroke(p, with: .color(ink), style: StrokeStyle(lineWidth: 2.6, lineCap: .round, lineJoin: .round))
        case .orrery:
            context.stroke(Path(ellipseIn: CGRect(x: 3.5, y: 3.5, width: 17, height: 17)), with: .color(ink), lineWidth: 2.2)
            context.fill(Path(ellipseIn: CGRect(x: 9, y: 9, width: 6, height: 6)), with: .color(ink))
            context.fill(Path(ellipseIn: CGRect(x: 17, y: 3.6, width: 5.2, height: 5.2)), with: .color(ink))
        case .nimbus:
            var p = Path()
            p.move(to: CGPoint(x: 3, y: 18))
            creditCardFlipFormArc(&p, from: CGPoint(x: 3, y: 18), to: CGPoint(x: 8, y: 11), radius: 5)
            creditCardFlipFormArc(&p, from: CGPoint(x: 8, y: 11), to: CGPoint(x: 20, y: 10), radius: 6.5)
            creditCardFlipFormArc(&p, from: CGPoint(x: 20, y: 10), to: CGPoint(x: 23, y: 18), radius: 4.5)
            p.closeSubpath()
            context.stroke(p, with: .color(ink), style: StrokeStyle(lineWidth: 2.2, lineJoin: .round))
        case .none:
            break
        }
    }
}

/// An SVG arc (small, clockwise on screen) from `a` to `b`, added as line segments.
func creditCardFlipFormArc(_ path: inout Path, from a: CGPoint, to b: CGPoint, radius r: Double) {
    let mid = CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
    let dx = b.x - a.x
    let dy = b.y - a.y
    let chord = hypot(dx, dy)
    let r = max(r, chord / 2)
    let h = sqrt(max(0, r * r - chord * chord / 4))
    // For a small arc swept clockwise on screen (y down) the centre lies to the right of a → b.
    let center = CGPoint(x: mid.x - dy / chord * h, y: mid.y + dx / chord * h)
    let start = atan2(a.y - center.y, a.x - center.x)
    var end = atan2(b.y - center.y, b.x - center.x)
    while end < start { end += 2 * .pi }
    let steps = 16
    for i in 1...steps {
        let t = start + (end - start) * Double(i) / Double(steps)
        path.addLine(to: CGPoint(x: center.x + r * cos(t), y: center.y + r * sin(t)))
    }
}

// MARK: - Turn

/// The card's turn about its vertical axis, with the perspective RN gives it (1100 pt away).
struct CreditCardFlipFormTurn: GeometryEffect {
    var degrees: Double
    let size: CGSize

    var animatableData: Double {
        get { degrees }
        set { degrees = newValue }
    }

    func effectValue(size _: CGSize) -> ProjectionTransform {
        let cx = size.width / 2
        let cy = size.height / 2
        var turn = CATransform3DMakeRotation(degrees * .pi / 180, 0, 1, 0)
        var view = CATransform3DIdentity
        view.m34 = -1 / CreditCardFlipFormCardMetrics.perspective
        turn = CATransform3DConcat(turn, view)
        let into = CATransform3DMakeTranslation(-cx, -cy, 0)
        let back = CATransform3DMakeTranslation(cx, cy, 0)
        return ProjectionTransform(CATransform3DConcat(CATransform3DConcat(into, turn), back))
    }
}

/// The card's tilt under a finger: turned `y`° about its upright axis, then `x`° about its
/// horizontal one, through the same 1100 pt perspective.
struct CreditCardFlipFormTilt: GeometryEffect {
    var x: Double
    var y: Double
    let size: CGSize

    var animatableData: AnimatablePair<Double, Double> {
        get { AnimatablePair(x, y) }
        set {
            x = newValue.first
            y = newValue.second
        }
    }

    func effectValue(size _: CGSize) -> ProjectionTransform {
        let cx = size.width / 2
        let cy = size.height / 2
        let turnY = CATransform3DMakeRotation(y * .pi / 180, 0, 1, 0)
        let turnX = CATransform3DMakeRotation(x * .pi / 180, 1, 0, 0)
        var view = CATransform3DIdentity
        view.m34 = -1 / CreditCardFlipFormCardMetrics.perspective
        let turn = CATransform3DConcat(CATransform3DConcat(turnY, turnX), view)
        let into = CATransform3DMakeTranslation(-cx, -cy, 0)
        let back = CATransform3DMakeTranslation(cx, cy, 0)
        return ProjectionTransform(CATransform3DConcat(CATransform3DConcat(into, turn), back))
    }
}
