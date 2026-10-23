import CoreText
import SwiftUI
import UIKit

// Drawing for the rotary dial, on a SwiftUI GraphicsContext, step for step as the React Native
// Skia drawing. The light comes from the upper left throughout: bevels are bright toward it and
// dark away from it, shadows fall down and right. The chrome's reflections are a conic gradient
// around the dial's centre that does not turn with the wheel (a polished disc spinning in place
// keeps its reflections), so only the holes, the shadows they cast and the numbers they reveal move.

struct RotaryPhoneDialGeometry: Equatable {
    var width: Double
    var height: Double
    var cx: Double
    var cy: Double
    /// The enamel number plate.
    var plateR: Double
    /// The finger wheel.
    var wheelR: Double
    /// Radius of the circle the holes sit on, and of each hole.
    var ringR: Double
    var holeR: Double
    /// The opening in the middle that holds the number card.
    var hubR: Double
    /// The paper strip's window.
    var strip: CGRect
}

/// The faces: DIN Condensed on the plate, Futura for the letters and the card's name, American
/// Typewriter for the card's number and the strip.
struct RotaryPhoneDialFonts {
    let digit: CTFont
    let letters: CTFont
    let cardName: CTFont
    let cardNumber: CTFont

    init(_ g: RotaryPhoneDialGeometry) {
        func font(_ name: String, _ size: Double) -> CTFont {
            CTFontCreateWithName(name as CFString, size, nil)
        }
        digit = font("DINCondensed-Bold", (g.holeR * 0.98).rounded())
        letters = font("Futura-Bold", 7.5)
        cardName = font("Futura-Bold", 9)
        cardNumber = font("AmericanTypewriter-Semibold", (g.hubR * 0.36).rounded())
    }
}

enum RotaryPhoneDialDrawing {
    private typealias T = RotaryPhoneDialTuning

    // Bakelite body, enamel plate, ink, chrome.
    static let bodyHi = rgb(0x2F6253)
    static let body = rgb(0x163E33)
    static let bodyLo = rgb(0x06150F)
    static let plate = rgb(0xF1E8D3)
    static let plateEdge = rgb(0xD8CAAA)
    static let ink = rgb(0x201D18)
    static let red = rgb(0xA23A2C)
    static let paper = rgb(0xF4EDDC)
    static let chrome: [Gradient.Stop] = zip(
        [0xD6D8DA, 0xFAFAF9, 0x979BA0, 0xE6E7E8, 0x6A6F74, 0xCDD0D2, 0xFBFBFA, 0x8C9095, 0xD6D8DA],
        [0, 0.1, 0.24, 0.38, 0.5, 0.62, 0.76, 0.88, 1.0]
    ).map { Gradient.Stop(color: rgb($0.0), location: $0.1) }

    // The autoPlay finger reaches in from this direction (degrees, clockwise from the right).
    static let fingerFrom = 58.0
    // How far the finger shows before it has faded out, in fingertip radii.
    static let fingerLength = 4.6

    // Letters over the digits, as on old exchange dials.
    static let letters = ["", "ABC", "DEF", "GHI", "JKL", "MNO", "PRS", "TUV", "WXY", "OPER"]

    /// The name and number on the card in the middle.
    static let cardName = "ELMWOOD"
    static let cardNumber = "5-0193"

    static func rgb(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }

    private static func rad(_ deg: Double) -> Double { deg * .pi / 180 }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    /// A light-to-dark linear gradient across a box around (x, y) of half-size r, lit from the upper left.
    private static func bevel(_ x: Double, _ y: Double, _ r: Double, _ light: Color, _ dark: Color) -> GraphicsContext.Shading {
        .linearGradient(
            Gradient(colors: [light, dark]),
            startPoint: CGPoint(x: x - r * 0.7, y: y - r * 0.7),
            endPoint: CGPoint(x: x + r * 0.7, y: y + r * 0.7)
        )
    }

    /// Fills `path` blurred by `sigma`, like a Skia blur mask filter.
    private static func soft(_ context: inout GraphicsContext, _ path: Path, _ color: Color, _ sigma: Double) {
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: sigma))
            layer.fill(path, with: .color(color))
        }
    }

    /// Text centred on x with its baseline on y, optionally letter-spaced by `tracking`.
    private static func centredText(_ context: inout GraphicsContext, _ text: String, _ x: Double, _ y: Double, _ font: CTFont, _ color: Color, _ tracking: Double) {
        var attributes: [NSAttributedString.Key: Any] = [
            NSAttributedString.Key(kCTFontAttributeName as String): font,
            NSAttributedString.Key(kCTForegroundColorAttributeName as String): UIColor(color).cgColor,
        ]
        if tracking != 0 { attributes[NSAttributedString.Key(kCTKernAttributeName as String)] = tracking }
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attributes))
        // Letter-spaced text has no space after its last glyph.
        let width = CTLineGetTypographicBounds(line, nil, nil, nil) - (tracking != 0 ? tracking : 0)
        context.withCGContext { cg in
            cg.saveGState()
            cg.textMatrix = CGAffineTransform(scaleX: 1, y: -1)
            cg.textPosition = CGPoint(x: x - width / 2, y: y)
            CTLineDraw(line, cg)
            cg.restoreGState()
        }
    }

    /// The finger wheel's metal: a disc with the ten holes and the middle cut out (even-odd).
    static func wheelPath(_ g: RotaryPhoneDialGeometry, _ phi: Double) -> Path {
        var p = circle(g.cx, g.cy, g.wheelR)
        for i in 0..<T.holes {
            let a = rad(T.holeRest(i) + phi)
            p.addPath(circle(g.cx + g.ringR * cos(a), g.cy + g.ringR * sin(a), g.holeR))
        }
        p.addPath(circle(g.cx, g.cy, g.hubR))
        return p
    }

    /// Everything that never moves: the phone's body, the strip's window, the enamel plate.
    static func drawStatic(_ context: inout GraphicsContext, _ g: RotaryPhoneDialGeometry, _ fonts: RotaryPhoneDialFonts) {
        let (width, height, cx, cy, plateR, ringR) = (g.width, g.height, g.cx, g.cy, g.plateR, g.ringR)
        // Bakelite, lit from the upper left, with a broad soft sheen.
        context.fill(
            Path(CGRect(x: 0, y: 0, width: width, height: height)),
            with: .radialGradient(
                Gradient(stops: [.init(color: bodyHi, location: 0), .init(color: body, location: 0.45), .init(color: bodyLo, location: 1)]),
                center: CGPoint(x: width * 0.28, y: height * 0.16),
                startRadius: 0,
                endRadius: height * 1.05
            )
        )
        var sheen = context
        sheen.translateBy(x: width * 0.3, y: height * 0.22)
        sheen.rotate(by: .degrees(-28))
        soft(&sheen, Path(ellipseIn: CGRect(x: -width * 0.55, y: -38, width: width * 1.1, height: 76)), Color.white.opacity(0.07), 26)

        // The strip's window: a chrome frame sunk into the body, paper inside.
        let s = g.strip
        let frame = Path(roundedRect: CGRect(x: s.minX - 6, y: s.minY - 6, width: s.width + 12, height: s.height + 12), cornerRadius: 14)
        soft(&context, Path(roundedRect: CGRect(x: s.minX - 4, y: s.minY + 2, width: s.width + 12, height: s.height + 12), cornerRadius: 14), Color.black.opacity(0.5), 8)
        context.fill(frame, with: bevel(s.midX, s.midY, s.width / 2, rgb(0xF7F7F6), rgb(0x5E6368)))
        let paperBox = Path(roundedRect: s, cornerRadius: 9)
        context.fill(
            paperBox,
            with: .linearGradient(
                Gradient(stops: [.init(color: rgb(0xE3D9C2), location: 0), .init(color: paper, location: 0.28), .init(color: rgb(0xEDE4CF), location: 1)]),
                startPoint: CGPoint(x: 0, y: s.minY),
                endPoint: CGPoint(x: 0, y: s.maxY)
            )
        )
        var inside = context
        inside.clip(to: paperBox)
        // Ruled line, perforations along both edges, a shadow under the frame's top lip.
        var rule = Path()
        rule.move(to: CGPoint(x: s.minX, y: s.minY + s.height * 0.74))
        rule.addLine(to: CGPoint(x: s.maxX, y: s.minY + s.height * 0.74))
        inside.stroke(rule, with: .color(rgb(0xA23A2C, 0.35)), lineWidth: 0.8)
        var x = s.minX + 10
        while x < s.maxX {
            inside.fill(circle(x, s.minY + 7, 2.1), with: .color(rgb(0x46371F, 0.22)))
            inside.fill(circle(x, s.maxY - 7, 2.1), with: .color(rgb(0x46371F, 0.22)))
            x += 14
        }
        soft(&inside, Path(CGRect(x: s.minX, y: s.minY - 10, width: s.width, height: 14)), rgb(0x281C0A, 0.32), 5)

        // The dial's well: a dark recess, a chrome bezel, then the enamel plate.
        soft(&context, circle(cx + 4, cy + 9, plateR * 1.13), Color.black.opacity(0.55), 16)
        context.fill(circle(cx, cy, plateR * 1.1), with: .color(bodyLo))
        context.fill(circle(cx, cy, plateR * 1.075), with: bevel(cx, cy, plateR * 1.08, rgb(0xF4F4F3), rgb(0x4D5257)))
        context.fill(circle(cx, cy, plateR * 1.04), with: .color(rgb(0x2A2C2E)))
        context.fill(
            circle(cx, cy, plateR),
            with: .radialGradient(
                Gradient(stops: [.init(color: rgb(0xFBF6EA), location: 0), .init(color: plate, location: 0.5), .init(color: plateEdge, location: 1)]),
                center: CGPoint(x: cx - plateR * 0.3, y: cy - plateR * 0.35),
                startRadius: 0,
                endRadius: plateR * 1.5
            )
        )
        // Fine enamel rings near the rim.
        context.stroke(circle(cx, cy, plateR - 4), with: .color(rgb(0x201D18, 0.55)), lineWidth: 0.8)
        context.stroke(circle(cx, cy, g.wheelR + 3), with: .color(rgb(0x201D18, 0.18)), lineWidth: 0.8)

        // The numbers, printed on the plate under each hole's resting place.
        for i in 0..<T.holes {
            let a = rad(T.holeRest(i))
            let x = cx + ringR * cos(a)
            let y = cy + ringR * sin(a)
            let d = T.holeDigits[i]
            let label = letters[d == 0 ? 9 : d - 1]
            if !label.isEmpty {
                centredText(&context, label, x, y - g.holeR * 0.3, fonts.letters, red, 0.8)
                centredText(&context, String(d), x, y + g.holeR * 0.54, fonts.digit, ink, 0)
            } else {
                centredText(&context, String(d), x, y + g.holeR * 0.31, fonts.digit, ink, 0)
            }
            // A small dot on the outer ring marks each position.
            context.fill(circle(cx + (plateR - 9) * cos(a), cy + (plateR - 9) * sin(a), 1.6), with: .color(ink))
        }
    }

    /// The wheel, wound `phi` degrees clockwise: its shadow on the plate, the metal, the bevels.
    static func drawWheel(_ context: inout GraphicsContext, _ g: RotaryPhoneDialGeometry, _ phi: Double) {
        let (cx, cy, wheelR, ringR, holeR, hubR) = (g.cx, g.cy, g.wheelR, g.ringR, g.holeR, g.hubR)
        let metal = wheelPath(g, phi)
        let even = FillStyle(eoFill: true)

        // Its shadow on the plate, seen around the rim and inside the holes.
        var shade = context
        shade.clip(to: circle(cx, cy, g.plateR))
        shade.translateBy(x: 2.5, y: 4.5)
        shade.drawLayer { layer in
            layer.addFilter(.blur(radius: 4.5))
            layer.fill(metal, with: .color(rgb(0x301E0A, 0.42)), style: even)
        }

        context.fill(metal, with: .conicGradient(Gradient(stops: chrome), center: CGPoint(x: cx, y: cy), angle: .zero), style: even)
        var turned = context
        turned.clip(to: metal, style: even)
        // Turned-metal rings, and a darkening toward the rim for the disc's curvature.
        for k in 0..<34 {
            let r = hubR + (wheelR - hubR) * (Double(k) + 0.5) / 34
            turned.stroke(circle(cx, cy, r), with: .color(k % 2 == 0 ? Color.white.opacity(0.10) : Color.black.opacity(0.07)), lineWidth: 0.6)
        }
        turned.fill(
            circle(cx, cy, wheelR),
            with: .radialGradient(
                Gradient(stops: [.init(color: .clear, location: 0), .init(color: .clear, location: 0.78), .init(color: Color.black.opacity(0.22), location: 1)]),
                center: CGPoint(x: cx, y: cy),
                startRadius: 0,
                endRadius: wheelR
            )
        )
        // Inside each hole the wall shows: dark on the side away from the light, bright on the other.
        for i in 0..<T.holes {
            let a = rad(T.holeRest(i) + phi)
            let x = cx + ringR * cos(a)
            let y = cy + ringR * sin(a)
            turned.stroke(circle(x, y, holeR + 0.6), with: bevel(x, y, holeR, rgb(0x3A3E43), .white), lineWidth: 3.2)
        }
        // The rim's bevel, and the hub's retaining ring.
        context.stroke(circle(cx, cy, wheelR - 1.1), with: bevel(cx, cy, wheelR, .white, rgb(0x41454A)), lineWidth: 2.2)
        context.stroke(circle(cx, cy, hubR + 1.5), with: bevel(cx, cy, hubR, rgb(0x50555A), .white), lineWidth: 5)
    }

    /// The finger stop: a curved chrome hook reaching in over the holes' track, twitched by `kick`.
    static func drawStop(_ context: inout GraphicsContext, _ g: RotaryPhoneDialGeometry, _ kick: Double) {
        let outer = g.plateR + 4
        let inner = g.ringR - g.holeR * 0.25
        var hook = Path()
        // Drawn along the +x axis, then turned to the stop's angle.
        hook.move(to: CGPoint(x: outer, y: -9))
        hook.addCurve(to: CGPoint(x: inner + 3, y: -4), control1: CGPoint(x: outer - (outer - inner) * 0.45, y: -8), control2: CGPoint(x: inner + 10, y: -6.5))
        hook.addQuadCurve(to: CGPoint(x: inner + 0.5, y: 3.6), control: CGPoint(x: inner - 3, y: -1.5))
        hook.addCurve(to: CGPoint(x: outer, y: 10), control1: CGPoint(x: inner + 12, y: 6.5), control2: CGPoint(x: outer - (outer - inner) * 0.4, y: 9))
        hook.closeSubpath()
        var c = context
        c.translateBy(x: g.cx, y: g.cy)
        c.rotate(by: .degrees(T.stopAngle + kick * 0.04))
        var shadow = c
        shadow.translateBy(x: 2, y: 4)
        soft(&shadow, hook, rgb(0x140C04, 0.45), 3.5)
        c.fill(
            hook,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: rgb(0xFDFDFC), location: 0), .init(color: rgb(0xC9CCCF), location: 0.35),
                    .init(color: rgb(0x7C8187), location: 0.75), .init(color: rgb(0xB5B8BB), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: -9),
                endPoint: CGPoint(x: 0, y: 10)
            )
        )
        c.stroke(hook, with: .color(rgb(0x1E1E1E, 0.45)), lineWidth: 0.8)
        // The screw that holds it to the plate.
        c.fill(circle(outer - 7, 0.5, 4.2), with: .color(rgb(0x6E7378)))
        c.fill(circle(outer - 7, 0.5, 3.4), with: bevel(outer - 7, 0.5, 4, .white, rgb(0x83888D)))
        var slot = Path()
        slot.move(to: CGPoint(x: outer - 9.3, y: -1.4))
        slot.addLine(to: CGPoint(x: outer - 4.7, y: 2.4))
        c.stroke(slot, with: .color(rgb(0x282828, 0.6)), style: StrokeStyle(lineWidth: 1, lineCap: .round))
    }

    /// The number card in the middle, under a clear dome; `press` (0…1) pushes it in.
    static func drawCard(_ context: inout GraphicsContext, _ g: RotaryPhoneDialGeometry, _ fonts: RotaryPhoneDialFonts, _ press: Double) {
        let (cx, cy) = (g.cx, g.cy)
        let r = g.hubR - 2
        var c = context
        let k = 1 - 0.035 * press
        c.translateBy(x: cx, y: cy)
        c.scaleBy(x: k, y: k)
        c.translateBy(x: -cx, y: -cy)
        c.fill(
            circle(cx, cy, r),
            with: .radialGradient(
                Gradient(stops: [.init(color: rgb(0xFFFBF1), location: 0), .init(color: paper, location: 0.55), .init(color: rgb(0xDCD0B5), location: 1)]),
                center: CGPoint(x: cx - r * 0.25, y: cy - r * 0.3),
                startRadius: 0,
                endRadius: r * 1.4
            )
        )
        // The card's printed border and the shadow of the retaining ring on it.
        c.stroke(circle(cx, cy, r - 7), with: .color(red), lineWidth: 1.2)
        c.stroke(circle(cx, cy, r - 10), with: .color(red), lineWidth: 0.6)
        centredText(&c, cardName, cx, cy - r * 0.12, fonts.cardName, red, 2.2)
        centredText(&c, cardNumber, cx, cy + r * 0.34, fonts.cardNumber, ink, 0.6)
        c.drawLayer { layer in
            layer.addFilter(.blur(radius: 3))
            layer.stroke(circle(cx + 1, cy + 2, r + 1), with: .color(rgb(0x281C0A, 0.28)), lineWidth: 6)
        }
        // The clear dome: a broad sheen and a crisp window highlight, dimmer when pressed.
        var dome = c
        dome.clip(to: circle(cx, cy, r))
        dome.opacity = 1 - 0.5 * press
        dome.fill(
            Path(ellipseIn: CGRect(x: cx - r * 1.05, y: cy - r * 1.02, width: r * 1.5, height: r * 0.95)),
            with: .linearGradient(
                Gradient(colors: [Color.white.opacity(0.55), Color.white.opacity(0)]),
                startPoint: CGPoint(x: cx - r, y: cy - r),
                endPoint: CGPoint(x: cx + r * 0.2, y: cy + r * 0.2)
            )
        )
        var glint = c
        glint.translateBy(x: cx, y: cy)
        glint.rotate(by: .degrees(-38))
        glint.translateBy(x: -cx, y: -cy)
        soft(
            &glint,
            Path(roundedRect: CGRect(x: cx - r * 0.42, y: cy - r * 0.78, width: r * 0.5, height: 4.5), cornerRadius: 2.25),
            Color.white.opacity(0.85 * (1 - 0.6 * press)),
            1.2
        )
        if press > 0.001 { soft(&c, circle(cx, cy, r * 0.94), Color.black.opacity(0.14 * press), 8) }
    }

    /// The finger's outline along +x from the tip (at the origin) back toward the hand.
    private static func fingerPath(_ r: Double, _ length: Double) -> Path {
        var b = Path()
        b.move(to: CGPoint(x: 0, y: -r))
        b.addCurve(to: CGPoint(x: length, y: -r * 1.2), control1: CGPoint(x: length * 0.4, y: -r * 1.02), control2: CGPoint(x: length * 0.7, y: -r * 1.12))
        b.addLine(to: CGPoint(x: length, y: r * 1.2))
        b.addCurve(to: CGPoint(x: 0, y: r), control1: CGPoint(x: length * 0.7, y: r * 1.12), control2: CGPoint(x: length * 0.4, y: r * 1.02))
        // The fingertip: a half circle round the far side of the origin.
        b.addArc(center: .zero, radius: r, startAngle: .degrees(90), endAngle: .degrees(270), clockwise: false)
        b.closeSubpath()
        return b
    }

    /**
     The autoPlay finger, seen from above: a fingertip pressed into the hole, reaching in from the
     lower right as a right hand's would, with a nail and a soft shadow, fading out toward the hand
     so it reads as a touch rather than an object.
     */
    static func drawFinger(_ context: inout GraphicsContext, _ g: RotaryPhoneDialGeometry, _ angle: Double, _ shown: Double) {
        if shown <= 0.01 { return }
        let a = rad(angle)
        let x = g.cx + g.ringR * cos(a)
        let y = g.cy + g.ringR * sin(a)
        let r = g.holeR * 0.82
        let length = r * fingerLength
        var c = context
        c.translateBy(x: x, y: y)
        c.rotate(by: .degrees(fingerFrom))
        let finger = fingerPath(r, length)
        c.drawLayer { layer in
            var shadow = layer
            shadow.translateBy(x: 3, y: 5)
            soft(&shadow, finger, rgb(0x1E0F05, 0.34), 7)
            layer.fill(
                finger,
                with: .linearGradient(
                    Gradient(stops: [.init(color: rgb(0xF0CDB8), location: 0), .init(color: rgb(0xE2B39B), location: 0.45), .init(color: rgb(0xB57E66), location: 1)]),
                    startPoint: CGPoint(x: 0, y: -r),
                    endPoint: CGPoint(x: 0, y: r)
                )
            )
            layer.fill(
                Path(roundedRect: CGRect(x: -r * 0.55, y: -r * 0.56, width: r * 1.15, height: r * 1.12), cornerRadius: r * 0.5),
                with: .linearGradient(
                    Gradient(colors: [rgb(0xFBE4DA), rgb(0xE9BFB0), rgb(0xC9968A)]),
                    startPoint: CGPoint(x: 0, y: -r * 0.6),
                    endPoint: CGPoint(x: 0, y: r * 0.6)
                )
            )
            soft(&layer, Path(ellipseIn: CGRect(x: -r * 0.3, y: -r * 0.42, width: r * 0.6, height: r * 0.22)), Color.white.opacity(0.55), 1.5)
            // Fade toward the hand, and the whole finger in and out with `shown`.
            layer.blendMode = .destinationIn
            layer.fill(
                Path(CGRect(x: -r * 2, y: -r * 3, width: length + r * 4, height: r * 6)),
                with: .linearGradient(
                    Gradient(colors: [Color.black.opacity(shown), Color.black.opacity(0)]),
                    startPoint: CGPoint(x: r * 1.4, y: 0),
                    endPoint: CGPoint(x: length, y: 0)
                )
            )
        }
    }

    /// Pulse dots at the strip's right end: one fills for each number that passes the stop.
    static func drawPulses(_ context: inout GraphicsContext, _ g: RotaryPhoneDialGeometry, _ pulses: Int, _ shown: Double) {
        if shown <= 0.01 { return }
        let s = g.strip
        let y = s.minY + s.height * 0.5
        for k in 0..<10 {
            let x = s.maxX - 18 - Double(9 - k) * 9
            if k < pulses {
                context.fill(circle(x, y, 2.6), with: .color(rgb(0xA23A2C, 0.9 * shown)))
            } else {
                context.stroke(circle(x, y, 2.4), with: .color(rgb(0x201D18, 0.28 * shown)), lineWidth: 0.9)
            }
        }
    }
}
