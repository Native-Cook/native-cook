import SwiftUI

/// The colours of the block, hex as written in the React Native file.
enum TactileCalculatorWelcomePalette {
    static func rgb(_ hex: UInt32, opacity: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }

    /// `CREAM`: the page.
    static let cream = rgb(0xEEE8DC)
    /// `INK`: the ink-black (right-facing) walls.
    static let ink = rgb(0x151412)
    /// `OUTLINE`: the hairline outline of every shape.
    static let outline = rgb(0x1D1B18)
    /// `styles.welcome` colour.
    static let welcome = rgb(0x2A2725)
    /// `styles.title` colour.
    static let title = rgb(0xD9402D)
    /// `styles.cta` background.
    static let button = rgb(0x2D2B2C)
    /// The solid strip `ProgressiveBlurViewport` falls back to for the light tint.
    static let blurFallback = rgb(0xE5E9DF)
}

/// One key of the stack: its colours, its accessibility label and where it sits on the screen
/// (`KEYS` in the React Native file). `centerX` is a fraction of the window's width and `top` a
/// fraction of its height.
struct TactileCalculatorWelcomeKey: Identifiable {
    let symbol: TactileCalculatorWelcomeSymbol
    let face: Color
    let wall: Color
    let glyphWall: Color
    let label: String
    let centerX: Double
    let top: Double

    var id: TactileCalculatorWelcomeSymbol { symbol }

    private typealias P = TactileCalculatorWelcomePalette

    /// The five keys, top of the screen first. Later keys are drawn above earlier ones.
    static let all: [TactileCalculatorWelcomeKey] = [
        TactileCalculatorWelcomeKey(
            symbol: .minus, face: P.rgb(0xF7EED9), wall: P.rgb(0xE8D2A4), glyphWall: P.rgb(0xE8D2A4),
            label: "Subtract", centerX: 0.5, top: -0.024),
        TactileCalculatorWelcomeKey(
            symbol: .divide, face: P.rgb(0x6F9CF2), wall: P.rgb(0xF2C46F), glyphWall: P.rgb(0xF2C46F),
            label: "Divide", centerX: 0.928, top: 0.092),
        TactileCalculatorWelcomeKey(
            symbol: .plus, face: P.rgb(0xFFC694), wall: P.rgb(0xFA9441), glyphWall: P.rgb(0xF89A4B),
            label: "Add", centerX: 0.5, top: 0.2099),
        TactileCalculatorWelcomeKey(
            symbol: .multiply, face: P.rgb(0xF45A45), wall: P.rgb(0xF2C46F), glyphWall: P.rgb(0xC13A28),
            label: "Multiply", centerX: 0.074, top: 0.321),
        TactileCalculatorWelcomeKey(
            symbol: .equals, face: P.rgb(0xFFF6DA), wall: P.rgb(0xF2C46F), glyphWall: P.rgb(0xF2C46F),
            label: "Equals", centerX: 0.505, top: 0.4403),
    ]
}

/// Draws one key on a 216 x 150 plate scaled by `scale` (the two `Svg` layers of `KeyButton`).
///
/// The body is the rim ring, the two walls, the pinstripes and the foot outline; the cap is the
/// face, its glint and the raised symbol. A pressed cap sinks `travel` plate units and the
/// walls shorten with it, so a fully pressed cap lies flush in its rim.
enum TactileCalculatorWelcomeKeyArt {
    private typealias G = TactileCalculatorWelcomeGeometry
    private typealias P = TactileCalculatorWelcomePalette

    /// `TRAVEL`: `WALL - 0.6`, how far a cap sinks.
    static let travel: Double = TactileCalculatorWelcomeGeometry.depth - 0.6

    /// `pressure` is 0 at rest and 1 with the cap fully down.
    static func draw(
        _ canvas: GraphicsContext, key: TactileCalculatorWelcomeKey, pressure: Double, scale: Double
    ) {
        var context = canvas
        context.scaleBy(x: CGFloat(scale), y: CGFloat(scale))
        let sink: Double = travel * pressure

        // Body layer (static position).
        context.stroke(G.ringPath, with: .color(P.outline), style: StrokeStyle(lineWidth: 1.1))
        context.fill(
            G.chainWall(G.bodyLitFlat, top: sink, bottom: G.depth), with: .color(key.wall))
        context.fill(
            G.chainWall(G.bodyInkFlat, top: sink, bottom: G.depth), with: .color(P.ink))
        let stripeOpacity: Double = 1 - pressure
        if stripeOpacity > 0 {
            var stripes = Path()
            for stripe in G.stripes {
                stripes.move(to: CGPoint(x: stripe.x, y: stripe.y + sink))
                stripes.addLine(to: CGPoint(x: stripe.x, y: stripe.y + G.depth))
            }
            context.stroke(
                stripes, with: .color(P.ink.opacity(stripeOpacity)), style: StrokeStyle(lineWidth: 1.8))
        }
        context.stroke(
            G.bodyFoot, with: .color(P.outline), style: StrokeStyle(lineWidth: 1.4, lineJoin: .round))

        // Cap layer: translateY = TRAVEL * pressure.
        var cap = context
        cap.translateBy(x: 0, y: CGFloat(sink))
        cap.fill(G.facePath, with: .color(key.face))
        cap.stroke(G.facePath, with: .color(P.outline), style: StrokeStyle(lineWidth: 1.4, lineJoin: .round))
        cap.stroke(
            G.faceGlint,
            with: .color(Color.white.opacity(0.34)),
            style: StrokeStyle(lineWidth: 1.5, lineCap: .round)
        )
        drawGlyph(cap, key: key)
    }

    /// `Glyph`: a raised symbol, walls first, then the tops drawn twice (an outline, then the
    /// face colour) so overlapping bars read as one outline.
    private static func drawGlyph(_ cap: GraphicsContext, key: TactileCalculatorWelcomeKey) {
        let parts = G.glyphs[key.symbol] ?? []
        let wallStyle = StrokeStyle(lineWidth: 1.3, lineJoin: .round)
        for part in parts {
            for path in part.lit {
                cap.fill(path, with: .color(key.glyphWall))
                cap.stroke(path, with: .color(P.outline), style: wallStyle)
            }
            for path in part.ink {
                cap.fill(path, with: .color(P.ink))
                cap.stroke(path, with: .color(P.outline), style: wallStyle)
            }
        }
        let outlineStyle = StrokeStyle(lineWidth: 2.6, lineJoin: .round)
        for part in parts {
            cap.fill(part.top, with: .color(P.outline))
            cap.stroke(part.top, with: .color(P.outline), style: outlineStyle)
        }
        for part in parts {
            cap.fill(part.top, with: .color(key.face))
        }
    }
}
