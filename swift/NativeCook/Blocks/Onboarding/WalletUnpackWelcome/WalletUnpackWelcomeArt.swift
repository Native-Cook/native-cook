import SwiftUI

/// The wallet, drawn in its own box (origin at its top left): a back panel, and a front pocket
/// with a scooped notch and a croc-scale grain; and the two notes that slide out between them.
enum WalletUnpackWelcomeArt {
    static let walletW = 210.0
    static let walletH = 152.0
    private static let walletRadius = 16.0
    /// The pocket's top edge, below the wallet's, and the notch scooped out of it.
    private static let pocketTop = 24.0
    private static let notchW = 46.0
    private static let notchD = 13.0
    private static let noteW = 170.0
    private static let noteH = 60.0
    /// The notes' box: wide and tall enough for a note risen above the wallet.
    static let notesW = 320.0
    static let notesH = 440.0

    private static let backTop = Color(hex: 0x34412F)
    private static let backBottom = Color(hex: 0x232D20)
    private static let grain = Color(hex: 0x2A3526)
    private static let tones = [Color(hex: 0x3E4C39), Color(hex: 0x43523E), Color(hex: 0x394734)]
    private static let noteTop = Color(hex: 0xDCE6DA)
    private static let noteBottom = Color(hex: 0xB5C6B6)

    // The croc grain: rows of these heights; in each, scales 16 to 36 points wide, each nudged up
    // or down a little and shaded one of three tones, all from a fixed seed.
    private static let rowHeights = [13.0, 15, 12, 16, 14, 13, 15, 14, 12]
    private static let seed = 20_261_014

    /// Each scale as a rectangle and a tone (0 to 2), in the wallet's box. The numbers come from
    /// Park–Miller, which gives the same sequence in every language.
    private static let scales: [(rect: CGRect, tone: Int)] = {
        var s = seed
        func next() -> Double {
            s = s * 16807 % 2_147_483_647
            return Double(s) / 2_147_483_647
        }
        var out: [(CGRect, Int)] = []
        var y = pocketTop
        var row = 0
        while y < walletH {
            let h = rowHeights[row % rowHeights.count]
            var x = -next() * 14
            while x < walletW {
                let w = 16 + next() * 20
                let nudge = (next() - 0.5) * 3
                out.append((CGRect(x: x, y: y + nudge, width: w, height: h - abs(nudge)), Int(floor(next() * 3))))
                x += w
            }
            y += h
            row += 1
        }
        return out
    }()

    private static var walletShape: Path {
        Path(roundedRect: CGRect(x: 0, y: 0, width: walletW, height: walletH), cornerRadius: walletRadius, style: .circular)
    }

    /// The pocket's top edge with its notch, `dy` below where it sits.
    private static func pocketEdge(_ path: inout Path, dy: Double = 0) {
        let mid = walletW / 2, half = notchW / 2, top = pocketTop + dy
        path.addLine(to: CGPoint(x: mid - half, y: top))
        // 0.55 of the half-axes puts the cubic's handles where a quarter ellipse needs them.
        path.addCurve(to: CGPoint(x: mid, y: top + notchD), control1: CGPoint(x: mid - half, y: top + notchD * 0.55), control2: CGPoint(x: mid - half * 0.55, y: top + notchD))
        path.addCurve(to: CGPoint(x: mid + half, y: top), control1: CGPoint(x: mid + half * 0.55, y: top + notchD), control2: CGPoint(x: mid + half, y: top + notchD * 0.55))
        path.addLine(to: CGPoint(x: walletW, y: top))
    }

    /// The pocket: the wallet's lower part, its top edge scooped in the middle.
    private static var pocketShape: Path {
        var path = Path()
        let r = walletRadius
        path.move(to: CGPoint(x: 0, y: pocketTop))
        pocketEdge(&path)
        path.addLine(to: CGPoint(x: walletW, y: walletH - r))
        path.addQuadCurve(to: CGPoint(x: walletW - r, y: walletH), control: CGPoint(x: walletW, y: walletH))
        path.addLine(to: CGPoint(x: r, y: walletH))
        path.addQuadCurve(to: CGPoint(x: 0, y: walletH - r), control: CGPoint(x: 0, y: walletH))
        path.closeSubpath()
        return path
    }

    /// The back panel.
    static func drawBack(_ context: GraphicsContext) {
        context.fill(walletShape, with: .linearGradient(Gradient(colors: [backTop, backBottom]), startPoint: .zero, endPoint: CGPoint(x: 0, y: walletH)))
        // A soft light along the top edge.
        var line = Path()
        line.move(to: CGPoint(x: walletRadius, y: 0.8))
        line.addLine(to: CGPoint(x: walletW - walletRadius, y: 0.8))
        context.stroke(line, with: .color(.white.opacity(0.1)), lineWidth: 1)
    }

    /// The pocket with its grain, shading and lit edge, and the shadow its edge casts above it.
    static func drawPocket(_ context: GraphicsContext) {
        // The shadow of the pocket's edge, over whatever is tucked in behind it.
        var shade = context
        shade.clip(to: walletShape)
        shade.fill(
            Path(CGRect(x: 0, y: pocketTop - 7, width: walletW, height: 8 + notchD)),
            with: .linearGradient(Gradient(colors: [.black.opacity(0), .black.opacity(0.28)]), startPoint: CGPoint(x: 0, y: pocketTop - 7), endPoint: CGPoint(x: 0, y: pocketTop + 1))
        )

        var pocket = context
        pocket.clip(to: pocketShape)
        pocket.fill(Path(CGRect(x: 0, y: 0, width: walletW, height: walletH)), with: .color(grain))
        for scale in scales {
            let r = scale.rect
            pocket.fill(Path(roundedRect: r.insetBy(dx: 1, dy: 1), cornerRadius: 5, style: .circular), with: .color(tones[scale.tone]))
            // Each scale catches a little light along its top.
            pocket.fill(Path(roundedRect: CGRect(x: r.minX + 3, y: r.minY + 1.5, width: r.width - 6, height: 2), cornerRadius: 1, style: .circular), with: .color(.white.opacity(0.05)))
        }
        // Light from above, falling off towards the bottom.
        pocket.fill(
            Path(CGRect(x: 0, y: 0, width: walletW, height: walletH)),
            with: .linearGradient(Gradient(colors: [.white.opacity(0.07), .black.opacity(0.22)]), startPoint: CGPoint(x: 0, y: pocketTop), endPoint: CGPoint(x: 0, y: walletH))
        )

        // The pocket's lit edge.
        var edge = Path()
        edge.move(to: CGPoint(x: 0, y: pocketTop + 0.5))
        pocketEdge(&edge, dy: 0.5)
        context.stroke(edge, with: .color(.white.opacity(0.16)), lineWidth: 1)
    }

    /// A note centred at `center`, turned, its long edges rippling, softened by `blur`.
    private static func drawNote(_ context: GraphicsContext, center: CGPoint, turn: Double, wave: Double, phase: Double, blur: Double) {
        let half = noteW / 2
        var outline = Path()
        // The top edge left to right, then the bottom edge back, ten points apart.
        for x in stride(from: -half, through: half, by: 10) {
            let point = CGPoint(x: x, y: -noteH / 2 + wave * sin((x + half) / 120 * tau + phase))
            if x == -half { outline.move(to: point) } else { outline.addLine(to: point) }
        }
        for x in stride(from: half, through: -half, by: -10) {
            outline.addLine(to: CGPoint(x: x, y: noteH / 2 + wave * sin((x + half) / 120 * tau + phase + 0.6)))
        }
        outline.closeSubpath()

        var ctx = context
        ctx.translateBy(x: center.x, y: center.y)
        ctx.rotate(by: .degrees(turn))
        ctx.addFilter(.blur(radius: blur))
        ctx.fill(outline, with: .linearGradient(Gradient(colors: [noteTop, noteBottom]), startPoint: CGPoint(x: 0, y: -noteH / 2), endPoint: CGPoint(x: 0, y: noteH / 2)))
        // A paler oval where a note would carry its watermark, and a fine inner border.
        ctx.fill(Path(ellipseIn: CGRect(x: -half + 22, y: -16, width: 40, height: 32)), with: .color(.white.opacity(0.35)))
        ctx.stroke(Path(CGRect(x: -half + 6, y: -noteH / 2 + 6, width: noteW - 12, height: noteH - 12)), with: .color(.white.opacity(0.4)), lineWidth: 1)
    }

    /// Both notes in the notes' box, whose centre is the wallet's.
    static func drawNotes(_ context: GraphicsContext, hero h: WalletUnpackWelcomeHero) {
        drawNote(context, center: CGPoint(x: notesW / 2, y: notesH / 2 + h.note1Y), turn: h.note1Turn, wave: h.note1Wave, phase: h.phase, blur: h.note1Blur)
        drawNote(context, center: CGPoint(x: notesW / 2, y: notesH / 2 + h.note2Y), turn: h.note2Turn, wave: h.note2Wave, phase: h.phase + 1.3, blur: h.note2Blur)
    }
}
